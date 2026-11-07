package info.mudbourn.mmseconomy.deal;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Bill;
import info.mudbourn.mmseconomy.economy.Billing;
import info.mudbourn.mmseconomy.economy.Bond;
import info.mudbourn.mmseconomy.economy.Bonds;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// Owns the pending bill and loan requests and ticks them toward their timeout.
public final class OfferManager {

    private static final Map<Integer, PendingOffer> OFFERS = new HashMap<>();

    private static int nextId = 1;

    private OfferManager() {
    }

    // A payee asks a payer to start paying a bill.
    public static void offerBill(ServerPlayerEntity payee, ServerPlayerEntity payer, long amount,
                                 int intervalDays, int lateBps, String label) {
        PendingOffer offer = add(PendingOffer.Type.BILL, payee, payer, amount, 0, intervalDays, 0, lateBps, label);
        payee.sendMessage(Text.literal("Bill request " + offer.id + " sent to "
            + payer.getName().getString() + "."), false);
        payer.sendMessage(Text.literal(payee.getName().getString() + " asks you to pay "
            + Currency.format(amount) + " every " + intervalDays + " days for " + label
            + ". Accept with /bill accept " + offer.id + "."), false);
    }

    // A borrower asks a lender for a loan on the given bond terms.
    public static void offerBond(ServerPlayerEntity borrower, ServerPlayerEntity lender, long principal,
                                 int rateBps, int couponDays, int termDays, int lateBps) {
        OFFERS.values().removeIf(entry -> entry.type == PendingOffer.Type.BOND
            && entry.target.equals(lender.getUuid()));
        PendingOffer offer = add(PendingOffer.Type.BOND, borrower, lender, principal, rateBps, couponDays,
            termDays, lateBps, "");
        borrower.sendMessage(Text.literal("Loan request sent to " + lender.getName().getString() + "."), false);
        lender.sendMessage(Text.literal(borrower.getName().getString() + " asks you for a loan of "
            + Currency.format(principal) + ": " + rateBps / 100.0 + "% every " + couponDays
            + " days for " + termDays + " days, late rate " + lateBps / 100.0
            + "% per day. Accept with /bond accept (offer " + offer.id + ")."), false);
    }

    public static void acceptBill(ServerPlayerEntity payer, int id) {
        PendingOffer offer = OFFERS.get(id);
        if (offer == null || offer.type != PendingOffer.Type.BILL || !offer.target.equals(payer.getUuid())) {
            payer.sendMessage(Text.literal("You have no bill request " + id + "."), false);
            return;
        }

        OFFERS.remove(id);
        MinecraftServer server = payer.getEntityWorld().getServer();
        Bill bill = Billing.create(server, payer, offer.proposer, offer.proposerName, offer.amount,
            offer.days, offer.lateBps, offer.label);
        payer.sendMessage(Text.literal("Bill " + bill.id + " started: " + Currency.format(bill.amount)
            + " to " + bill.payeeName + " every " + bill.intervalDays + " days, first due day "
            + bill.nextDue + "."), false);
        Billing.tell(server, offer.proposer.toString(), payer.getName().getString()
            + " accepted bill " + bill.id + " (" + bill.label + ").");
    }

    public static void acceptBond(ServerPlayerEntity lender) {
        PendingOffer offer = OFFERS.values().stream()
            .filter(entry -> entry.type == PendingOffer.Type.BOND && entry.target.equals(lender.getUuid()))
            .findFirst()
            .orElse(null);
        if (offer == null) {
            lender.sendMessage(Text.literal("You have no loan request."), false);
            return;
        }

        MinecraftServer server = lender.getEntityWorld().getServer();
        ServerPlayerEntity borrower = server.getPlayerManager().getPlayer(offer.proposer);
        if (borrower == null) {
            OFFERS.remove(offer.id);
            lender.sendMessage(Text.literal(offer.proposerName + " is no longer online."), false);
            return;
        }
        if (!Wallet.canAfford(lender, offer.amount)) {
            lender.sendMessage(Text.literal("You only have " + Currency.format(Wallet.balance(lender)) + "."), false);
            return;
        }

        if (!Wallet.transfer(lender, borrower, offer.amount)) {
            lender.sendMessage(Text.literal("The transfer failed. Loan cancelled."), false);
            return;
        }
        OFFERS.remove(offer.id);
        Bond bond = Bonds.issue(server, borrower.getUuid().toString(), offer.proposerName, offer.amount,
            offer.rateBps, offer.days, offer.termDays, offer.lateBps);
        Bonds.give(lender, bond);

        long day = History.currentDay(lender);
        History.log(lender, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, -offer.amount, offer.proposerName + " loan"));
        History.log(borrower, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, offer.amount, lender.getName().getString() + " loan"));
        lender.sendMessage(Text.literal("Lent " + Currency.format(offer.amount) + " to " + offer.proposerName
            + ". You hold the bond; matures on day " + bond.maturityDay + "."), false);
        borrower.sendMessage(Text.literal("Borrowed " + Currency.format(offer.amount) + " from "
            + lender.getName().getString() + ". Coupons are due every " + offer.days + " days."), false);
    }

    // Expires requests that have waited past the deal timeout.
    public static void tickAll(MinecraftServer server) {
        for (PendingOffer offer : new ArrayList<>(OFFERS.values())) {
            offer.ticksLeft--;
            if (offer.ticksLeft > 0) {
                continue;
            }
            OFFERS.remove(offer.id);
            Billing.tell(server, offer.proposer.toString(), "Your request to " + describeTarget(server, offer)
                + " timed out.");
            Billing.tell(server, offer.target.toString(), "The request from " + offer.proposerName
                + " timed out.");
        }
    }

    private static String describeTarget(MinecraftServer server, PendingOffer offer) {
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(offer.target);
        return target == null ? "the other player" : target.getName().getString();
    }

    private static PendingOffer add(PendingOffer.Type type, ServerPlayerEntity proposer,
                                    ServerPlayerEntity target, long amount, int rateBps, int days,
                                    int termDays, int lateBps, String label) {
        PendingOffer offer = new PendingOffer(
            nextId++,
            type,
            proposer.getUuid(),
            proposer.getName().getString(),
            target.getUuid(),
            amount,
            rateBps,
            days,
            termDays,
            lateBps,
            label,
            MmsEconomy.config().dealTimeoutTicks);
        OFFERS.put(offer.id, offer);
        return offer;
    }
}
