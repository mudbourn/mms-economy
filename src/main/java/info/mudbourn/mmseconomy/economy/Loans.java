package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Loan lifecycle: opening, bank lending, installments on the borrower's active playtime, debts and early payoff.
public final class Loans {

    public static final double MAX_RATE_PERCENT = 6.5;

    public static final int MAX_INSTALLMENTS = 52;

    private static final int CHECK_INTERVAL = 200;

    private static final long DAY_TICKS = 24000L;

    private static final String BANK = "Bank";

    private static final String BANK_LOAN = BANK + " loan";

    private Loans() {
    }

    // Snaps an installment period to weekly (7) or monthly (30) days.
    public static int normalizeDays(int days) {
        return days <= 18 ? 7 : 30;
    }

    public static String period(int days) {
        return days == 7 ? "weekly" : "monthly";
    }

    // Records a new loan whose first period starts now; an empty lender id means the bank.
    public static Loan open(MinecraftServer server, ServerPlayer borrower, String lenderId,
                            String lenderName, long principal, int rateBps, int periodDays,
                            int installments, int lateBps) {
        Obligations state = Obligations.get(server);
        int period = normalizeDays(periodDays);
        Loan loan = new Loan(
            state.allocateId(),
            borrower.getUUID().toString(),
            borrower.getName().getString(),
            lenderId,
            lenderName,
            principal,
            rateBps,
            period,
            installments,
            lateBps,
            principal,
            installments,
            PlayTime.get(server).ticks(borrower.getUUID().toString()));
        state.loans.add(loan);
        state.setDirty();
        return loan;
    }

    // Lends Treasury money to a player standing at a bank, on the configured bank terms.
    public static void borrowFromBank(ServerPlayer player, long amount, int installments) {
        MinecraftServer server = player.level().getServer();
        EconomyConfig config = MmsEconomy.config();
        if (server == null || !config.bankLoansEnabled) {
            player.sendSystemMessage(Component.literal("The bank is not lending."));
            return;
        }
        if (!BankAccess.inReach(player)) {
            player.sendSystemMessage(Component.literal("Stand at a bank to borrow from it."));
            return;
        }
        int maxInstallments = Math.max(1, Math.min(config.bankLoanMaxInstallments, MAX_INSTALLMENTS));
        if (installments < 1 || installments > maxInstallments) {
            player.sendSystemMessage(Component.literal("The bank lends over 1 to " + maxInstallments
                + " installments."));
            return;
        }

        Obligations state = Obligations.get(server);
        String id = player.getUUID().toString();
        if (Billing.owedBy(state, id) > 0) {
            player.sendSystemMessage(Component.literal("Clear your overdue debts before borrowing from the bank."));
            return;
        }
        long room = Math.max(0L, bankLimit(player) - bankPrincipal(state, id));
        if (amount > room) {
            player.sendSystemMessage(Component.literal("The bank will lend you at most " + Currency.format(room)
                + " more." + (goodStanding(player) ? "" : " " + standingRule())));
            return;
        }
        if (!Treasury.get(server).debit(amount)) {
            player.sendSystemMessage(Component.literal("The bank cannot cover that loan right now."));
            return;
        }

        Wallet.deposit(player, amount);
        Loan loan = open(server, player, "", BANK, amount, config.bankLoanRateBps,
            config.bankLoanPeriodDays, installments, config.bankLoanLateBps);
        History.log(player, new HistoryEntry(History.currentDay(player), HistoryCategory.DEAL, "", 0,
            amount, 0L, amount, BANK_LOAN));
        player.sendSystemMessage(Component.literal("Borrowed " + Currency.format(amount) + " from the bank: "
            + terms(loan) + "."));
    }

    // Every ten seconds, charges online borrowers whose active playtime filled a period and grows their loan debts.
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL != 0 || server.overworld() == null) {
            return;
        }

        Obligations state = Obligations.get(server);
        PlayTime playTime = PlayTime.get(server);
        for (Loan loan : new ArrayList<>(state.loans)) {
            if (online(server, loan.borrower) == null) {
                continue;
            }
            long ticks = playTime.ticks(loan.borrower);
            while (loan.paymentsLeft > 0 && ticks - loan.snapshot >= loan.periodTicks()) {
                charge(server, state, loan);
            }
            if (loan.paymentsLeft <= 0) {
                close(server, state, loan);
            }
        }
        for (Debt debt : state.debts) {
            if (!debt.loan || online(server, debt.payer) == null) {
                continue;
            }
            long ticks = playTime.ticks(debt.payer);
            while (ticks - debt.snapshot >= DAY_TICKS) {
                Billing.grow(debt);
                debt.snapshot += DAY_TICKS;
                state.setDirty();
            }
        }
    }

    // Draws on a loan debt's payer, untaxed up to its principal, and returns what was collected.
    public static long collect(MinecraftServer server, Debt debt) {
        ServerPlayer payer = online(server, debt.payer);
        if (payer == null) {
            return 0L;
        }

        long want = Math.min(debt.amount, Wallet.balance(payer));
        long principal = pay(server, payer, debt.payee, debt.payeeName, Math.min(want, debt.base), false, debt.label);
        debt.base -= principal;
        long paid = principal
            + pay(server, payer, debt.payee, debt.payeeName, want - principal, true, debt.label);
        if (paid > 0) {
            payer.sendSystemMessage(Component.literal("Collected " + Currency.format(paid) + " of overdue "
                + debt.label + "."));
            Billing.tell(server, debt.payee, debt.payerName + " paid " + Currency.format(paid)
                + " of overdue " + debt.label + ".");
        }
        return paid;
    }

    // Settles a loan early for its remaining principal plus one period of interest.
    public static void repay(ServerPlayer player, int id) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        Obligations state = Obligations.get(server);
        Loan loan = state.loan(id);
        if (loan == null || !loan.borrower.equals(player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal("You have no loan " + id + " to pay off."));
            return;
        }
        long due = loan.payoff();
        if (!Wallet.canAfford(player, due)) {
            player.sendSystemMessage(Component.literal("Paying off " + label(loan) + " costs " + Currency.format(due)
                + "; you only have " + Currency.format(Wallet.balance(player)) + "."));
            return;
        }

        String label = label(loan);
        pay(server, player, loan.lender, loan.lenderName, loan.interest(), true, label);
        pay(server, player, loan.lender, loan.lenderName, loan.principalLeft, false, label);
        loan.principalLeft = 0L;
        loan.paymentsLeft = 0;
        state.loans.remove(loan);
        state.setDirty();
        player.sendSystemMessage(Component.literal("Paid off " + label + " for " + Currency.format(due) + "."));
        Billing.tell(server, loan.lender, loan.borrowerName + " paid off " + label + " early for "
            + Currency.format(due) + ".");
    }

    // The loans the player borrowed or lent, in storage order.
    public static List<Loan> involving(Obligations state, String playerId) {
        return state.loans.stream()
            .filter(loan -> loan.borrower.equals(playerId) || loan.lender.equals(playerId))
            .toList();
    }

    // A one-line summary of a loan from the viewer's side.
    public static String describe(MinecraftServer server, Loan loan, String viewerId) {
        String who;
        if (loan.borrower.equals(viewerId)) {
            who = "you owe " + loan.lenderName;
        } else if (loan.lender.equals(viewerId)) {
            who = loan.borrowerName + " owes you";
        } else {
            who = loan.borrowerName + " owes " + loan.lenderName;
        }
        return label(loan) + ": " + who + " " + Currency.format(loan.principalLeft) + " of "
            + Currency.format(loan.principal) + ", " + loan.paymentsLeft + " " + period(loan.periodDays)
            + " payments left, next " + Currency.format(loan.installment()) + " after "
            + PlayTime.format(untilDue(server, loan)) + " of the borrower's active play";
    }

    // Tells the holder of a loan deed where its loan stands.
    public static void inspect(ServerPlayer player, ItemStack stack) {
        MinecraftServer server = player.level().getServer();
        int id = LoanDeed.read(stack);
        if (server == null || id < 0) {
            player.sendSystemMessage(Component.literal("This deed is blank; lend with /loan accept."));
            return;
        }
        Loan loan = Obligations.get(server).loan(id);
        if (loan == null) {
            player.sendSystemMessage(Component.literal("This loan is settled and the deed is void."));
            return;
        }
        player.sendSystemMessage(Component.literal(describe(server, loan, player.getUUID().toString()) + "."));
    }

    // The most bank loan principal the player may owe at once.
    public static long bankLimit(ServerPlayer player) {
        EconomyConfig config = MmsEconomy.config();
        return goodStanding(player)
            ? Math.max(config.bankLoanMaxPice, config.bankLoanBasicMaxPice)
            : config.bankLoanBasicMaxPice;
    }

    // Whether the player's balance and transaction history earn the full bank loan limit.
    public static boolean goodStanding(ServerPlayer player) {
        EconomyConfig config = MmsEconomy.config();
        if (Wallet.balance(player) < config.bankLoanStandingBalancePice) {
            return false;
        }
        long large = 0L;
        for (HistoryCategory category : HistoryCategory.values()) {
            large += History.get(player, category).stream()
                .filter(entry -> entry.gross() >= config.bankLoanStandingTransactionPice)
                .filter(entry -> !entry.counterparty().startsWith(BANK + " (")
                    && !entry.counterparty().equals(BANK_LOAN))
                .count();
        }
        return large >= config.bankLoanStandingTransactions;
    }

    // The bank's lending terms for the player in words, or an empty string when it is not lending.
    public static String bankTerms(ServerPlayer player) {
        EconomyConfig config = MmsEconomy.config();
        if (!config.bankLoansEnabled) {
            return "";
        }
        return "up to " + Currency.format(bankLimit(player)) + " at "
            + BondPaper.percent(config.bankLoanRateBps) + " " + period(normalizeDays(config.bankLoanPeriodDays))
            + " over at most " + config.bankLoanMaxInstallments + " payments, timed by your active play";
    }

    private static String standingRule() {
        EconomyConfig config = MmsEconomy.config();
        return "Hold " + Currency.format(config.bankLoanStandingBalancePice) + " and have "
            + config.bankLoanStandingTransactions + " past transactions of "
            + Currency.format(config.bankLoanStandingTransactionPice) + " or more to borrow up to "
            + Currency.format(config.bankLoanMaxPice) + ".";
    }

    public static String label(Loan loan) {
        return "Loan " + loan.id;
    }

    // The repayment schedule of a loan in words.
    public static String terms(Loan loan) {
        return loan.installments + " " + period(loan.periodDays) + " payments of about "
            + Currency.format(loan.installment()) + " (" + BondPaper.percent(loan.rateBps)
            + " interest each), one due per " + PlayTime.format(loan.periodTicks())
            + " of the borrower's active play";
    }

    // Active borrower ticks left before the next installment is charged.
    private static long untilDue(MinecraftServer server, Loan loan) {
        long played = PlayTime.get(server).ticks(loan.borrower) - loan.snapshot;
        return Math.max(0L, loan.periodTicks() - played);
    }

    private static long bankPrincipal(Obligations state, String borrowerId) {
        return state.loans.stream()
            .filter(loan -> loan.byBank() && loan.borrower.equals(borrowerId))
            .mapToLong(loan -> loan.principalLeft)
            .sum();
    }

    private static void charge(MinecraftServer server, Obligations state, Loan loan) {
        ServerPlayer borrower = online(server, loan.borrower);
        long principal = loan.principalDue();
        long paid = settle(server, state, loan, borrower, loan.interest(), false)
            + settle(server, state, loan, borrower, principal, true);
        loan.principalLeft -= principal;
        loan.paymentsLeft--;
        loan.snapshot += loan.periodTicks();
        state.setDirty();

        if (paid > 0) {
            String label = label(loan);
            Billing.tell(server, loan.borrower, label + ": paid " + loan.lenderName + " "
                + Currency.format(paid) + ", " + loan.paymentsLeft + " payments left.");
            Billing.tell(server, loan.lender, label + ": " + loan.borrowerName + " paid "
                + Currency.format(paid) + ", " + loan.paymentsLeft + " payments left.");
        }
    }

    // Pays one part of an installment and turns any shortfall into a debt; returns what was paid.
    private static long settle(MinecraftServer server, Obligations state, Loan loan,
                               ServerPlayer borrower, long amount, boolean principalPart) {
        if (amount <= 0) {
            return 0L;
        }

        String label = label(loan);
        long paid = borrower == null
            ? 0L
            : pay(server, borrower, loan.lender, loan.lenderName, amount, !principalPart, label);
        long shortfall = amount - paid;
        if (shortfall <= 0) {
            return paid;
        }

        Debt debt = Billing.addDebt(state, loan.borrower, loan.borrowerName, loan.lender, loan.lenderName,
            Integer.toString(loan.id), false, true, principalPart, shortfall, loan.lateBps, label);
        debt.snapshot = PlayTime.get(server).ticks(loan.borrower);
        Billing.tell(server, loan.borrower, "Missed " + label + " payment: "
            + Currency.format(shortfall) + " is now a debt.");
        Billing.tell(server, loan.lender, loan.borrowerName + " missed a " + label + " payment: "
            + Currency.format(shortfall) + " is now a debt.");
        return paid;
    }

    private static void close(MinecraftServer server, Obligations state, Loan loan) {
        state.loans.remove(loan);
        state.setDirty();
        String label = label(loan);
        Billing.tell(server, loan.borrower, label + " from " + loan.lenderName
            + " has run its term and is closed. Any missed payments stay owed as debts.");
        Billing.tell(server, loan.lender, label + " to " + loan.borrowerName
            + " has run its term and is closed. Any missed payments stay owed as debts.");
    }

    // Moves up to amount from the payer to the lender or the Treasury, taxing it when asked; returns the gross paid.
    private static long pay(MinecraftServer server, ServerPlayer payer, String lenderId,
                            String lenderName, long amount, boolean taxed, String label) {
        long gross = Math.min(amount, Wallet.balance(payer));
        if (gross <= 0 || !Wallet.withdraw(payer, gross)) {
            return 0L;
        }

        long day = History.currentDay(payer);
        boolean bank = lenderId.isEmpty();
        long tax = taxed && !bank ? Tax.on(gross, TaxKind.LOAN) : 0L;
        long net = gross - tax;
        Treasury.get(server).credit(bank ? gross : tax);
        History.log(payer, new HistoryEntry(day, HistoryCategory.BILL, "", 0,
            gross, tax, -gross, lenderName + " (" + label + ")"));
        if (bank) {
            return gross;
        }

        UUID lender = UUID.fromString(lenderId);
        HistoryEntry received = new HistoryEntry(day, HistoryCategory.BILL, "", 0,
            gross, tax, net, payer.getName().getString() + " (" + label + ")");
        ServerPlayer online = server.getPlayerList().getPlayer(lender);
        if (online != null) {
            Wallet.deposit(online, net);
            History.log(online, received);
        } else {
            PendingPayouts.get(server).record(lender, net, received);
        }
        return gross;
    }

    private static ServerPlayer online(MinecraftServer server, String uuid) {
        return server.getPlayerList().getPlayer(UUID.fromString(uuid));
    }
}
