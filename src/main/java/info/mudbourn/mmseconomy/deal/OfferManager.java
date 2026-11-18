package info.mudbourn.mmseconomy.deal;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Bill;
import info.mudbourn.mmseconomy.economy.Billing;
import info.mudbourn.mmseconomy.economy.BondPaper;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Loan;
import info.mudbourn.mmseconomy.economy.LoanDeed;
import info.mudbourn.mmseconomy.economy.Loans;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Prediction;

// Owns the pending bill and loan requests and ticks them toward their timeout.
public final class OfferManager {

    private static final Map<Integer, PendingOffer> OFFERS = new HashMap<>();

    private static int nextId = 1;

    private OfferManager() {
    }

    // A payee asks a payer to start paying a bill.
    public static void offerBill(ServerPlayer payee, ServerPlayer payer, long amount,
                                 int intervalDays, int lateBps, String label) {
        PendingOffer offer = add(PendingOffer.Type.BILL, payee, payer, amount, 0, intervalDays, 0, lateBps, label);
        payee.sendSystemMessage(Component.literal("Bill request " + offer.id + " sent to "
            + payer.getName().getString() + "."));
        payer.sendSystemMessage(Component.literal(payee.getName().getString() + " asks you to pay "
            + Currency.format(amount) + " every " + intervalDays + " days for " + label
            + ". Accept with /bill accept " + offer.id + "."));
    }

    // A borrower asks a lender for a loan repaid in installments.
    public static void offerLoan(ServerPlayer borrower, ServerPlayer lender, long principal,
                                 int rateBps, int periodDays, int installments, int lateBps) {
        OFFERS.values().removeIf(entry -> entry.type == PendingOffer.Type.LOAN
            && entry.target.equals(lender.getUUID()));
        add(PendingOffer.Type.LOAN, borrower, lender, principal, rateBps, periodDays, installments, lateBps, "");
        borrower.sendSystemMessage(Component.literal("Loan request sent to " + lender.getName().getString() + "."));
        lender.sendSystemMessage(Component.literal(borrower.getName().getString() + " asks to borrow "
            + Currency.format(principal) + ", repaid in " + installments + " " + Loans.period(periodDays)
            + " payments with " + BondPaper.percent(rateBps) + " interest each, late rate "
            + BondPaper.percent(lateBps) + " per day. Accept with /loan accept."));
    }

    public static void acceptBill(ServerPlayer payer, int id) {
        PendingOffer offer = OFFERS.get(id);
        if (offer == null || offer.type != PendingOffer.Type.BILL || !offer.target.equals(payer.getUUID())) {
            payer.sendSystemMessage(Component.literal("You have no bill request " + id + "."));
            return;
        }

        OFFERS.remove(id);
        MinecraftServer server = payer.level().getServer();
        Bill bill = Billing.create(server, payer, offer.proposer, offer.proposerName, offer.amount,
            offer.days, offer.lateBps, offer.label);
        payer.sendSystemMessage(Component.literal("Bill " + bill.id + " started: " + Currency.format(bill.amount)
            + " to " + bill.payeeName + " every " + bill.intervalDays + " days, first due day "
            + bill.nextDue + "."));
        Billing.tell(server, offer.proposer.toString(), payer.getName().getString()
            + " accepted bill " + bill.id + " (" + bill.label + ").");
    }

    public static void acceptLoan(ServerPlayer lender) {
        PendingOffer offer = OFFERS.values().stream()
            .filter(entry -> entry.type == PendingOffer.Type.LOAN && entry.target.equals(lender.getUUID()))
            .findFirst()
            .orElse(null);
        if (offer == null) {
            lender.sendSystemMessage(Component.literal("You have no loan request."));
            return;
        }

        MinecraftServer server = lender.level().getServer();
        ServerPlayer borrower = server.getPlayerList().getPlayer(offer.proposer);
        if (borrower == null) {
            OFFERS.remove(offer.id);
            lender.sendSystemMessage(Component.literal(offer.proposerName + " is no longer online."));
            return;
        }
        if (!Wallet.canAfford(lender, offer.amount)) {
            lender.sendSystemMessage(Component.literal("You only have " + Currency.format(Wallet.balance(lender)) + "."));
            return;
        }

        if (!Wallet.transfer(lender, borrower, offer.amount)) {
            lender.sendSystemMessage(Component.literal("The transfer failed. Loan cancelled."));
            return;
        }
        OFFERS.remove(offer.id);
        String lenderName = lender.getName().getString();
        Loan loan = Loans.open(server, borrower, lender.getUUID().toString(), lenderName, offer.amount,
            offer.rateBps, offer.days, offer.installments, offer.lateBps);
        lender.getInventory().placeItemBackInInventory(LoanDeed.create(loan), Prediction.SERVER_ONLY);

        long day = History.currentDay(lender);
        History.log(lender, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, -offer.amount, offer.proposerName + " loan"));
        History.log(borrower, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, offer.amount, lenderName + " loan"));
        lender.sendSystemMessage(Component.literal("Lent " + Currency.format(offer.amount) + " to " + offer.proposerName
            + " as " + Loans.label(loan) + ": " + Loans.terms(loan) + ". You hold the deed."));
        borrower.sendSystemMessage(Component.literal("Borrowed " + Currency.format(offer.amount) + " from "
            + lenderName + " as " + Loans.label(loan) + ": " + Loans.terms(loan) + "."));
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
        ServerPlayer target = server.getPlayerList().getPlayer(offer.target);
        return target == null ? "the other player" : target.getName().getString();
    }

    private static PendingOffer add(PendingOffer.Type type, ServerPlayer proposer,
                                    ServerPlayer target, long amount, int rateBps, int days,
                                    int installments, int lateBps, String label) {
        PendingOffer offer = new PendingOffer(
            nextId++,
            type,
            proposer.getUUID(),
            proposer.getName().getString(),
            target.getUUID(),
            amount,
            rateBps,
            days,
            installments,
            lateBps,
            label,
            MmsEconomy.config().dealTimeoutTicks);
        OFFERS.put(offer.id, offer);
        return offer;
    }
}
