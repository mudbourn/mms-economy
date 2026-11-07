package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.UUID;

// The in-game day clock for bills, bonds and debts: charges what is due, grows late debts and collects them.
public final class Billing {

    private static final int MAX_GROWTH_DAYS = 3650;

    private static final long MAX_PERIODS = 3650L;

    private static final long AMOUNT_CAP = Long.MAX_VALUE / 4L;

    private Billing() {
    }

    public static long today(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / 24000L;
    }

    // Floored basis-point share of an amount.
    public static long bpsOf(long amount, int bps) {
        if (amount <= 0 || bps <= 0) {
            return 0L;
        }
        return BigInteger.valueOf(amount)
            .multiply(BigInteger.valueOf(bps))
            .divide(BigInteger.valueOf(10000L))
            .min(BigInteger.valueOf(AMOUNT_CAP))
            .longValue();
    }

    // An amount repeated n times, held under the amount cap.
    public static long times(long amount, long n) {
        if (amount <= 0 || n <= 0) {
            return 0L;
        }
        return amount > AMOUNT_CAP / n ? AMOUNT_CAP : amount * n;
    }

    // Runs once per new in-game day: late growth, due bills, due bonds, then debt collection.
    public static void tick(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }
        long day = overworld.getTimeOfDay() / 24000L;
        Obligations state = Obligations.get(server);
        if (state.lastDay < 0L) {
            state.lastDay = day;
            state.markDirty();
            return;
        }
        if (day <= state.lastDay) {
            return;
        }

        long elapsed = Math.min(day - state.lastDay, MAX_GROWTH_DAYS);
        for (long i = 0; i < elapsed; i++) {
            growDebts(state);
        }
        state.lastDay = day;

        for (Bill bill : new ArrayList<>(state.bills)) {
            if (bill.nextDue <= day) {
                long missed = (day - bill.nextDue) / bill.intervalDays + 1;
                charge(server, state, bill, times(bill.amount, Math.min(missed, MAX_PERIODS)));
                bill.nextDue += missed * bill.intervalDays;
            }
        }
        Bonds.processDue(server, state, day);
        for (Debt debt : new ArrayList<>(state.debts)) {
            collect(server, state, debt);
        }
        state.markDirty();
    }

    // Collects what a joining player owes and reminds them of the rest.
    public static void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;
        String id = player.getUuid().toString();
        Obligations state = Obligations.get(server);
        for (Debt debt : new ArrayList<>(state.debts)) {
            if (debt.payer.equals(id)) {
                collect(server, state, debt);
            }
        }
        state.markDirty();

        long owed = owedBy(state, id);
        if (owed > 0) {
            player.sendMessage(Text.literal("You owe " + Currency.format(owed)
                + " in overdue bills and bonds."), false);
        }
    }

    public static long owedBy(Obligations state, String payerId) {
        return state.debts.stream()
            .filter(debt -> debt.payer.equals(payerId))
            .mapToLong(debt -> debt.amount)
            .sum();
    }

    // Records a new bill whose first charge is due one interval from today.
    public static Bill create(MinecraftServer server, ServerPlayerEntity payer, UUID payeeId,
                              String payeeName, long amount, int intervalDays, int lateBps, String label) {
        Obligations state = Obligations.get(server);
        Bill bill = new Bill(
            state.allocateId(),
            payer.getUuid().toString(),
            payer.getName().getString(),
            payeeId.toString(),
            payeeName,
            amount,
            intervalDays,
            lateBps,
            label,
            today(server) + intervalDays);
        state.bills.add(bill);
        state.markDirty();
        return bill;
    }

    // Pays up to amount from an online payer to a payee, taxed as a bill; returns the gross actually paid.
    public static long pay(MinecraftServer server, ServerPlayerEntity payer, String payeeId,
                           String payeeName, long amount, String label) {
        long gross = Math.min(amount, Wallet.balance(payer));
        if (gross <= 0) {
            return 0L;
        }

        UUID payee = UUID.fromString(payeeId);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(payee);
        Tax.Settlement settlement = online != null
            ? Tax.settleTaxed(payer, online, gross, TaxKind.BILL)
            : Tax.settleTaxedOffline(payer, gross, TaxKind.BILL);
        if (settlement == null) {
            return 0L;
        }

        String payerName = payer.getName().getString();
        long day = History.currentDay(payer);
        History.log(payer, new HistoryEntry(day, HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), -settlement.gross(), payeeName + " (" + label + ")"));
        HistoryEntry received = new HistoryEntry(day, HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), settlement.net(), payerName + " (" + label + ")");
        payer.sendMessage(Text.literal("Bill " + label + ": paid " + payeeName + " "
            + Currency.format(settlement.gross()) + " (tax " + Currency.format(settlement.tax()) + ")."), false);

        if (online != null) {
            History.log(online, received);
            online.sendMessage(Text.literal("Bill " + label + ": received "
                + Currency.format(settlement.net()) + " from " + payerName
                + " (tax " + Currency.format(settlement.tax()) + ")."), false);
        } else {
            PendingPayouts.get(server).record(payee, settlement.net(), received);
        }
        return settlement.gross();
    }

    public static void tell(MinecraftServer server, String uuid, String message) {
        if (uuid.isEmpty()) {
            return;
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(UUID.fromString(uuid));
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    // Opens a debt record for an unpaid amount.
    public static void addDebt(Obligations state, String payerId, String payerName, String payeeId,
                               String payeeName, String ref, boolean bond, boolean principalPart,
                               long amount, int lateBps, String label) {
        state.debts.add(new Debt(state.allocateId(), payerId, payerName, payeeId, payeeName, ref,
            bond, principalPart, amount, -1L, lateBps, label));
        state.markDirty();
    }

    private static void growDebts(Obligations state) {
        for (Debt debt : state.debts) {
            debt.amount = Math.min(AMOUNT_CAP, debt.amount + bpsOf(debt.amount, debt.lateBps));
        }
    }

    private static void charge(MinecraftServer server, Obligations state, Bill bill, long due) {
        ServerPlayerEntity payer = server.getPlayerManager().getPlayer(UUID.fromString(bill.payer));
        long paid = payer == null ? 0L : pay(server, payer, bill.payee, bill.payeeName, due, bill.label);
        long shortfall = due - paid;
        if (shortfall <= 0) {
            return;
        }

        addDebt(state, bill.payer, bill.payerName, bill.payee, bill.payeeName, Integer.toString(bill.id),
            false, false, shortfall, bill.lateBps, bill.label);
        tell(server, bill.payer, "Missed bill " + bill.label + " to " + bill.payeeName + ": "
            + Currency.format(shortfall) + " is now a debt.");
        tell(server, bill.payee, bill.payerName + " missed bill " + bill.label + ": "
            + Currency.format(shortfall) + " is now a debt.");
    }

    private static void collect(MinecraftServer server, Obligations state, Debt debt) {
        long paid;
        if (debt.bond) {
            paid = Bonds.collect(server, state, debt);
        } else {
            ServerPlayerEntity payer = server.getPlayerManager().getPlayer(UUID.fromString(debt.payer));
            paid = payer == null ? 0L : pay(server, payer, debt.payee, debt.payeeName, debt.amount, debt.label);
        }
        if (paid <= 0) {
            return;
        }

        debt.amount -= paid;
        if (debt.amount <= 0) {
            state.debts.remove(debt);
        }
        state.markDirty();
    }
}
