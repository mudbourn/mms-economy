package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.UUID;

// Bond lifecycle: issue, coupon and maturity payments into the bond, debt collection and redemption.
public final class Bonds {

    private static final int MAX_RATE_BPS = 1000;

    private static final long MAX_PERIODS = 3650L;

    private Bonds() {
    }

    // Records a new bond; an empty issuer id means the Treasury.
    public static Bond issue(MinecraftServer server, String issuerId, String issuerName, long principal,
                             int couponBps, int couponDays, int termDays, int lateBps) {
        Obligations state = Obligations.get(server);
        long day = Billing.today(server);
        Bond bond = new Bond(
            UUID.randomUUID().toString(),
            issuerId,
            issuerName,
            principal,
            Math.min(couponBps, MAX_RATE_BPS),
            couponDays,
            lateBps,
            day,
            day + termDays,
            day + couponDays,
            0L,
            0L,
            false);
        state.bonds.add(bond);
        state.markDirty();
        return bond;
    }

    public static void give(ServerPlayerEntity player, Bond bond) {
        player.getInventory().offerOrDrop(BondPaper.create(bond));
    }

    // Moves every coupon and maturity that has come due into the bond, turning any shortfall into debt.
    public static void processDue(MinecraftServer server, Obligations state, long day) {
        for (Bond bond : new ArrayList<>(state.bonds)) {
            if (bond.matured) {
                continue;
            }

            long last = Math.min(day, bond.maturityDay);
            long interest = 0L;
            if (bond.nextCoupon <= last) {
                long missed = (last - bond.nextCoupon) / bond.couponDays + 1;
                interest = Billing.times(Billing.bpsOf(bond.principal, bond.couponBps),
                    Math.min(missed, MAX_PERIODS));
                bond.nextCoupon += missed * bond.couponDays;
            }
            settleDue(server, state, bond, interest, false);
            if (bond.maturityDay <= day) {
                bond.matured = true;
                settleDue(server, state, bond, bond.principal, true);
            }
        }
    }

    // Draws on a debt's payer for the bond and returns what was collected.
    public static long collect(MinecraftServer server, Obligations state, Debt debt) {
        Bond bond = state.bond(debt.ref);
        if (bond == null) {
            state.debts.remove(debt);
            return 0L;
        }

        long drawn = pull(server, debt.payer, debt.amount);
        if (drawn > 0) {
            long toPrincipal = debt.principalPart ? Math.min(drawn, debt.base) : 0L;
            debt.base -= toPrincipal;
            credit(bond, true, toPrincipal);
            credit(bond, false, drawn - toPrincipal);
            Billing.tell(server, debt.payer, "Collected " + Currency.format(drawn)
                + " of overdue " + debt.label + ".");
        }
        return drawn;
    }

    // Pays the holder what the bond has accrued, taxing interest only, and closes a repaid matured bond.
    public static void redeem(ServerPlayerEntity player, ItemStack stack) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = BondPaper.read(stack);
        if (server == null || id == null) {
            return;
        }

        Obligations state = Obligations.get(server);
        Bond bond = state.bond(id);
        if (bond == null) {
            player.sendMessage(Text.literal("This bond is not recognised or is already closed."), false);
            return;
        }

        long headroom = headroom(player);
        long principal = Math.min(bond.accruedPrincipal, headroom);
        long interest = interestWithin(bond.accruedInterest, headroom - principal);
        long tax = Tax.on(interest, TaxKind.BOND);
        long net = interest - tax + principal;
        if (interest + principal > 0) {
            bond.accruedInterest -= interest;
            bond.accruedPrincipal -= principal;
            if (tax > 0) {
                Treasury.get(server).credit(tax);
            }
            Wallet.deposit(player, net);
            History.log(player, new HistoryEntry(History.currentDay(player), HistoryCategory.DEAL, "", 0,
                interest + principal, tax, net, bond.issuerName + " bond"));
            player.sendMessage(Text.literal("Bond redeemed: received " + Currency.format(net)
                + " (interest " + Currency.format(interest) + ", tax " + Currency.format(tax)
                + ", principal " + Currency.format(principal) + ")."), false);
        }
        if (bond.accrued() > 0 && headroom < Long.MAX_VALUE) {
            player.sendMessage(Text.literal("Your wallet is full; the rest stays on the bond."), false);
        }

        state.markDirty();
        if (bond.matured && bond.accrued() == 0 && !state.hasDebtFor(bond)) {
            state.bonds.remove(bond);
            stack.decrement(1);
            player.sendMessage(Text.literal("The bond has matured and is fully repaid. It is now closed."), false);
            return;
        }
        player.sendMessage(Text.literal(status(state, bond)), false);
    }

    // Wallet room left under the balance cap, or the long maximum when there is no cap.
    private static long headroom(ServerPlayerEntity player) {
        long max = MmsEconomy.config().maxBalance;
        return max > 0 ? Math.max(0L, max - Wallet.balance(player)) : Long.MAX_VALUE;
    }

    // The most interest whose after-tax amount fits in the room.
    private static long interestWithin(long interest, long room) {
        long low = 0L;
        long high = interest;
        while (low < high) {
            long mid = low + (high - low + 1) / 2;
            if (mid - Tax.on(mid, TaxKind.BOND) <= room) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static String status(Obligations state, Bond bond) {
        long debt = state.debts.stream()
            .filter(entry -> entry.bond && entry.ref.equals(bond.id))
            .mapToLong(entry -> entry.amount)
            .sum();
        String next = bond.matured
            ? "matured, awaiting repayment"
            : "next coupon day " + Math.min(bond.nextCoupon, bond.maturityDay);
        return "Bond status: accrued " + Currency.format(bond.accrued())
            + ", issuer debt " + Currency.format(debt) + ", " + next + ".";
    }

    private static void settleDue(MinecraftServer server, Obligations state, Bond bond, long amount,
                                  boolean principalPart) {
        if (amount <= 0) {
            return;
        }

        long drawn = pull(server, bond.issuer, amount);
        credit(bond, principalPart, drawn);
        long shortfall = amount - drawn;
        if (shortfall <= 0) {
            return;
        }

        String label = "Bond " + bond.id.substring(0, 8);
        Billing.addDebt(state, bond.issuer, bond.issuerName, "", "", bond.id, true, principalPart,
            shortfall, bond.lateBps, label);
        Billing.tell(server, bond.issuer, "Missed " + label + " payment: "
            + Currency.format(shortfall) + " is now a debt.");
    }

    private static void credit(Bond bond, boolean principalPart, long amount) {
        if (principalPart) {
            bond.accruedPrincipal += amount;
        } else {
            bond.accruedInterest += amount;
        }
    }

    // Takes up to amount from the issuer's wallet, or from the Treasury when the id is empty.
    private static long pull(MinecraftServer server, String payerId, long amount) {
        if (payerId.isEmpty()) {
            Treasury treasury = Treasury.get(server);
            long take = Math.min(treasury.balance(), amount);
            return take > 0 && treasury.debit(take) ? take : 0L;
        }

        ServerPlayerEntity payer = server.getPlayerManager().getPlayer(UUID.fromString(payerId));
        if (payer == null) {
            return 0L;
        }
        long take = Math.min(Wallet.balance(payer), amount);
        return take > 0 && Wallet.withdraw(payer, take) ? take : 0L;
    }
}
