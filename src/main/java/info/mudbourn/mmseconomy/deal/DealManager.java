package info.mudbourn.mmseconomy.deal;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Tax;
import info.mudbourn.mmseconomy.economy.TaxKind;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Owns the live currency deals and ticks them toward their timeout.
public final class DealManager {

    private static final Map<UUID, PendingDeal> DEALS = new HashMap<>();

    private DealManager() {
    }

    public static void open(ServerPlayerEntity initiator, ServerPlayerEntity target) {
        if (DEALS.containsKey(initiator.getUuid()) || DEALS.containsKey(target.getUuid())) {
            initiator.sendMessage(Text.literal("One of you already has a deal open. Cancel it first."), false);
            return;
        }
        PendingDeal deal = new PendingDeal(
            initiator.getUuid(),
            target.getUuid(),
            MmsEconomy.config().dealTimeoutTicks);
        DEALS.put(initiator.getUuid(), deal);
        DEALS.put(target.getUuid(), deal);

        initiator.sendMessage(Text.literal("Deal opened with " + target.getName().getString()
            + ". Stage with /deal offer <amount>, then /deal confirm."), false);
        target.sendMessage(Text.literal(initiator.getName().getString()
            + " opened a deal with you. Stage with /deal offer <amount>, then /deal confirm."), false);
    }

    public static void offer(ServerPlayerEntity player, long amount) {
        PendingDeal deal = DEALS.get(player.getUuid());
        if (deal == null) {
            player.sendMessage(Text.literal("You have no open deal."), false);
            return;
        }
        deal.setOffer(player.getUuid(), amount);
        player.sendMessage(Text.literal("You offer " + Currency.format(amount)
            + ". Both must confirm again."), false);
    }

    public static void confirm(ServerPlayerEntity player) {
        PendingDeal deal = DEALS.get(player.getUuid());
        if (deal == null) {
            player.sendMessage(Text.literal("You have no open deal."), false);
            return;
        }

        deal.confirm(player.getUuid());
        player.sendMessage(Text.literal("Confirmed."), false);
        if (deal.bothConfirmed()) {
            settle(player.getEntityWorld().getServer(), deal);
        }
    }

    public static void cancel(ServerPlayerEntity player) {
        PendingDeal deal = DEALS.get(player.getUuid());
        if (deal == null) {
            player.sendMessage(Text.literal("You have no open deal."), false);
            return;
        }
        teardown(deal);
        player.sendMessage(Text.literal("Deal cancelled."), false);
    }

    public static void info(ServerPlayerEntity player) {
        PendingDeal deal = DEALS.get(player.getUuid());
        if (deal == null) {
            player.sendMessage(Text.literal("You have no open deal."), false);
            return;
        }
        long mine = deal.offer(player.getUuid());
        long theirs = deal.offer(deal.other(player.getUuid()));
        long tax = Tax.on(mine, TaxKind.DEAL);
        player.sendMessage(Text.literal("You give " + Currency.format(mine)
            + " (tax " + Currency.format(tax) + "), you receive up to "
            + Currency.format(theirs) + "."), false);
    }

    private static void settle(MinecraftServer server, PendingDeal deal) {
        if (server == null) {
            return;
        }
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(deal.first());
        ServerPlayerEntity b = server.getPlayerManager().getPlayer(deal.second());
        if (a == null || b == null) {
            if (a != null) {
                a.sendMessage(Text.literal("The other player left. Deal cancelled."), false);
            }
            if (b != null) {
                b.sendMessage(Text.literal("The other player left. Deal cancelled."), false);
            }
            teardown(deal);
            return;
        }

        long offerA = deal.offer(a.getUuid());
        long offerB = deal.offer(b.getUuid());
        if (!Wallet.canAfford(a, offerA) || !Wallet.canAfford(b, offerB)) {
            a.sendMessage(Text.literal("A wallet is short. Deal cancelled."), false);
            b.sendMessage(Text.literal("A wallet is short. Deal cancelled."), false);
            teardown(deal);
            return;
        }

        long taxA = Tax.on(offerA, TaxKind.DEAL);
        long taxB = Tax.on(offerB, TaxKind.DEAL);
        Wallet.withdraw(a, offerA);
        Wallet.withdraw(b, offerB);
        long collected = taxA + taxB;
        if (collected > 0) {
            Treasury.get(server).credit(collected);
        }
        Wallet.deposit(b, offerA - taxA);
        Wallet.deposit(a, offerB - taxB);

        long day = History.currentDay(a);
        History.log(a, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offerA, taxA, offerB - taxB - offerA, b.getName().getString()));
        History.log(b, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offerB, taxB, offerA - taxA - offerB, a.getName().getString()));

        a.sendMessage(Text.literal("Deal settled with " + b.getName().getString() + "."), false);
        b.sendMessage(Text.literal("Deal settled with " + a.getName().getString() + "."), false);
        teardown(deal);
    }

    private static void teardown(PendingDeal deal) {
        DEALS.remove(deal.first());
        DEALS.remove(deal.second());
    }

    public static void tickAll(MinecraftServer server) {
        if (DEALS.isEmpty()) {
            return;
        }
        java.util.Set<PendingDeal> distinct = new java.util.HashSet<>(DEALS.values());
        java.util.Set<PendingDeal> expired = new java.util.HashSet<>();
        for (PendingDeal deal : distinct) {
            if (deal.tick()) {
                expired.add(deal);
            }
        }
        for (PendingDeal deal : expired) {
            notifyExpired(server, deal.first());
            notifyExpired(server, deal.second());
            teardown(deal);
        }
    }

    private static void notifyExpired(MinecraftServer server, UUID who) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(who);
        if (player != null) {
            player.sendMessage(Text.literal("Your deal timed out."), false);
        }
    }
}
