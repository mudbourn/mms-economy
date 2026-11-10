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

    // A borrower asks a lender for a loan repaid in installments.
    public static void offerLoan(ServerPlayerEntity borrower, ServerPlayerEntity lender, long principal,
                                 int rateBps, int periodDays, int installments, int lateBps) {
        OFFERS.values().removeIf(entry -> entry.type == PendingOffer.Type.LOAN
            && entry.target.equals(lender.getUuid()));
        add(PendingOffer.Type.LOAN, borrower, lender, principal, rateBps, periodDays, installments, lateBps, "");
        borrower.sendMessage(Text.literal("Loan request sent to " + lender.getName().getString() + "."), false);
        lender.sendMessage(Text.literal(borrower.getName().getString() + " asks to borrow "
            + Currency.format(principal) + ", repaid in " + installments + " " + Loans.period(periodDays)
            + " payments with " + BondPaper.percent(rateBps) + " interest each, late rate "
            + BondPaper.percent(lateBps) + " per day. Accept with /loan accept."), false);
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

    public static void acceptLoan(ServerPlayerEntity lender) {
        PendingOffer offer = OFFERS.values().stream()
            .filter(entry -> entry.type == PendingOffer.Type.LOAN && entry.target.equals(lender.getUuid()))
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
        String lenderName = lender.getName().getString();
        Loan loan = Loans.open(server, borrower, lender.getUuid().toString(), lenderName, offer.amount,
            offer.rateBps, offer.days, offer.installments, offer.lateBps);
        lender.getInventory().offerOrDrop(LoanDeed.create(loan));

        long day = History.currentDay(lender);
        History.log(lender, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, -offer.amount, offer.proposerName + " loan"));
        History.log(borrower, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offer.amount, 0L, offer.amount, lenderName + " loan"));
        lender.sendMessage(Text.literal("Lent " + Currency.format(offer.amount) + " to " + offer.proposerName
            + " as " + Loans.label(loan) + ": " + Loans.terms(loan) + ". You hold the deed."), false);
        borrower.sendMessage(Text.literal("Borrowed " + Currency.format(offer.amount) + " from "
            + lenderName + " as " + Loans.label(loan) + ": " + Loans.terms(loan) + "."), false);
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
                                    int installments, int lateBps, String label) {
        PendingOffer offer = new PendingOffer(
            nextId++,
            type,
            proposer.getUuid(),
            proposer.getName().getString(),
            target.getUuid(),
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
