package info.mudbourn.mmseconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import info.mudbourn.mmseconomy.deal.OfferManager;
import info.mudbourn.mmseconomy.economy.Bill;
import info.mudbourn.mmseconomy.economy.Billing;
import info.mudbourn.mmseconomy.economy.Bond;
import info.mudbourn.mmseconomy.economy.BondMarket;
import info.mudbourn.mmseconomy.economy.BondOffering;
import info.mudbourn.mmseconomy.economy.BondPaper;
import info.mudbourn.mmseconomy.economy.Bonds;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Debt;
import info.mudbourn.mmseconomy.economy.Loan;
import info.mudbourn.mmseconomy.economy.Loans;
import info.mudbourn.mmseconomy.economy.Obligations;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

// The /bill, /bond and /loan commands plus the admin /eco bond subtree.
public final class LendingCommand {

    private LendingCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("bill")
            .then(CommandManager.literal("create")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("amount", StringArgumentType.word())
                        .then(CommandManager.argument("intervalDays", IntegerArgumentType.integer(1, 3650))
                            .then(CommandManager.argument("lateRatePercent", DoubleArgumentType.doubleArg(0, 10))
                                .then(CommandManager.argument("label", StringArgumentType.greedyString())
                                    .executes(LendingCommand::billCreate)))))))
            .then(CommandManager.literal("pay")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("amount", StringArgumentType.word())
                        .then(CommandManager.argument("intervalDays", IntegerArgumentType.integer(1, 3650))
                            .then(CommandManager.literal("late")
                                .then(CommandManager.argument("lateRatePercent", DoubleArgumentType.doubleArg(0, 10))
                                    .then(CommandManager.argument("label", StringArgumentType.greedyString())
                                        .executes(context -> billPay(context,
                                            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")))))))
                            .then(CommandManager.argument("label", StringArgumentType.greedyString())
                                .executes(context -> billPay(context, 0)))))))
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        OfferManager.acceptBill(context.getSource().getPlayerOrThrow(),
                            IntegerArgumentType.getInteger(context, "id"));
                        return 1;
                    })))
            .then(CommandManager.literal("list")
                .executes(LendingCommand::billList))
            .then(CommandManager.literal("cancel")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                    .executes(LendingCommand::billCancel))));

        dispatcher.register(CommandManager.literal("bond")
            .then(CommandManager.literal("issue")
                .then(CommandManager.argument("principal", StringArgumentType.word())
                    .then(CommandManager.argument("ratePercent", DoubleArgumentType.doubleArg(0, 6.5))
                        .then(CommandManager.argument("couponDays", IntegerArgumentType.integer(1, 30))
                            .then(CommandManager.argument("termDays", IntegerArgumentType.integer(1, 36500))
                                .then(CommandManager.argument("lateRatePercent",
                                        DoubleArgumentType.doubleArg(0, 10))
                                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(LendingCommand::bondIssue))))))))
            .then(CommandManager.literal("cancel")
                .then(CommandManager.argument("offering", IntegerArgumentType.integer(1))
                    .executes(LendingCommand::bondCancel)))
            .then(CommandManager.literal("market")
                .executes(LendingCommand::bondMarket))
            .then(CommandManager.literal("buy")
                .then(CommandManager.argument("offering", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        BondMarket.buy(context.getSource().getPlayerOrThrow(),
                            IntegerArgumentType.getInteger(context, "offering"));
                        return 1;
                    })))
            .then(CommandManager.literal("vault")
                .executes(LendingCommand::bondVault))
            .then(CommandManager.literal("withdraw")
                .then(CommandManager.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        Bonds.withdraw(context.getSource().getPlayerOrThrow(),
                            IntegerArgumentType.getInteger(context, "index"));
                        return 1;
                    }))));

        dispatcher.register(CommandManager.literal("loan")
            .then(CommandManager.literal("request")
                .then(CommandManager.argument("lender", EntityArgumentType.player())
                    .then(CommandManager.argument("principal", StringArgumentType.word())
                        .then(CommandManager.argument("ratePercent",
                                DoubleArgumentType.doubleArg(0, Loans.MAX_RATE_PERCENT))
                            .then(CommandManager.argument("periodDays", IntegerArgumentType.integer(1, 30))
                                .then(CommandManager.argument("installments",
                                        IntegerArgumentType.integer(1, Loans.MAX_INSTALLMENTS))
                                    .then(CommandManager.argument("lateRatePercent",
                                            DoubleArgumentType.doubleArg(0, 10))
                                        .executes(LendingCommand::loanRequest))))))))
            .then(CommandManager.literal("accept")
                .executes(context -> {
                    OfferManager.acceptLoan(context.getSource().getPlayerOrThrow());
                    return 1;
                }))
            .then(CommandManager.literal("bank")
                .then(CommandManager.argument("amount", StringArgumentType.word())
                    .then(CommandManager.argument("installments",
                            IntegerArgumentType.integer(1, Loans.MAX_INSTALLMENTS))
                        .executes(LendingCommand::loanBank))))
            .then(CommandManager.literal("list")
                .executes(LendingCommand::loanList))
            .then(CommandManager.literal("repay")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        Loans.repay(context.getSource().getPlayerOrThrow(),
                            IntegerArgumentType.getInteger(context, "id"));
                        return 1;
                    }))));

        LiteralArgumentBuilder<ServerCommandSource> admin = CommandManager.literal("eco")
            .then(CommandManager.literal("bond")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("issue")
                    .then(CommandManager.argument("principal", StringArgumentType.word())
                        .then(CommandManager.argument("ratePercent", DoubleArgumentType.doubleArg(0, 6.5))
                            .then(CommandManager.argument("couponDays", IntegerArgumentType.integer(1, 30))
                                .then(CommandManager.argument("termDays", IntegerArgumentType.integer(1, 36500))
                                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 10000))
                                        .executes(LendingCommand::treasuryIssue)))))))
                .then(CommandManager.literal("list")
                    .executes(LendingCommand::bondMarket))
                .then(CommandManager.literal("cancel")
                    .then(CommandManager.argument("offering", IntegerArgumentType.integer(1))
                        .executes(LendingCommand::treasuryCancel))));
        dispatcher.register(admin);
    }

    private static boolean validPeriod(ServerCommandSource source, int days) {
        if (days != 7 && days != 30) {
            source.sendError(Text.literal("The period must be 7 (weekly) or 30 (monthly) days."));
            return false;
        }
        return true;
    }

    private static int bps(double percent) {
        return (int) Math.round(percent * 100.0);
    }

    private static long amount(ServerCommandSource source, String text) {
        long pice = Currency.parse(text);
        if (pice <= 0) {
            source.sendError(Text.literal("Enter an amount like 12.34 or a whole number of Pice."));
        }
        return pice;
    }

    private static int billCreate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity payee = source.getPlayerOrThrow();
        ServerPlayerEntity payer = EntityArgumentType.getPlayer(context, "player");
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (payee == payer) {
            source.sendError(Text.literal("You cannot bill yourself."));
            return 0;
        }

        OfferManager.offerBill(
            payee,
            payer,
            amount,
            IntegerArgumentType.getInteger(context, "intervalDays"),
            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")),
            StringArgumentType.getString(context, "label"));
        return 1;
    }

    private static int billPay(CommandContext<ServerCommandSource> context, int lateBps)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity payer = source.getPlayerOrThrow();
        ServerPlayerEntity payee = EntityArgumentType.getPlayer(context, "player");
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (payee == payer) {
            source.sendError(Text.literal("You cannot pay yourself."));
            return 0;
        }

        Bill bill = Billing.create(
            source.getServer(),
            payer,
            payee.getUuid(),
            payee.getName().getString(),
            amount,
            IntegerArgumentType.getInteger(context, "intervalDays"),
            lateBps,
            StringArgumentType.getString(context, "label"));
        payer.sendMessage(Text.literal("Bill " + bill.id + " started: " + Currency.format(amount)
            + " to " + bill.payeeName + " every " + bill.intervalDays + " days, first due day "
            + bill.nextDue + "."), false);
        payee.sendMessage(Text.literal(bill.payerName + " will pay you " + Currency.format(amount)
            + " every " + bill.intervalDays + " days (" + bill.label + ")."), false);
        return 1;
    }

    private static int billList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String me = player.getUuid().toString();
        Obligations state = Obligations.get(source.getServer());

        boolean any = false;
        for (Bill bill : state.bills) {
            boolean paying = bill.payer.equals(me);
            if (!paying && !bill.payee.equals(me)) {
                continue;
            }
            any = true;
            String other = paying ? "you pay " + bill.payeeName : bill.payerName + " pays you";
            source.sendFeedback(() -> Text.literal("Bill " + bill.id + ": " + other + " "
                + Currency.format(bill.amount) + " every " + bill.intervalDays + " days, next due day "
                + bill.nextDue + " (" + bill.label + ")"), false);
        }
        for (Debt debt : state.debts) {
            boolean owing = debt.payer.equals(me);
            if (!owing && !debt.payee.equals(me)) {
                continue;
            }
            any = true;
            String who = owing ? "you owe " + debt.payeeName : debt.payerName + " owes you";
            source.sendFeedback(() -> Text.literal("Debt: " + who + " " + Currency.format(debt.amount)
                + " for " + debt.label + ", late rate " + BondPaper.percent(debt.lateBps) + " per day"), false);
        }
        if (!any) {
            source.sendFeedback(() -> Text.literal("You have no bills or debts."), false);
        }
        return 1;
    }

    private static int billCancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        Obligations state = Obligations.get(source.getServer());
        Bill bill = state.bill(IntegerArgumentType.getInteger(context, "id"));
        String me = player.getUuid().toString();
        if (bill == null || (!bill.payer.equals(me) && !bill.payee.equals(me))) {
            source.sendError(Text.literal("You have no such bill."));
            return 0;
        }

        state.bills.remove(bill);
        state.markDirty();
        source.sendFeedback(() -> Text.literal("Bill " + bill.id + " cancelled. Any debt stays owed."), false);
        Billing.tell(source.getServer(), bill.payer.equals(me) ? bill.payee : bill.payer,
            player.getName().getString() + " cancelled bill " + bill.id + " (" + bill.label + ").");
        return 1;
    }

    private static int loanRequest(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity borrower = source.getPlayerOrThrow();
        ServerPlayerEntity lender = EntityArgumentType.getPlayer(context, "lender");
        long principal = amount(source, StringArgumentType.getString(context, "principal"));
        if (principal <= 0) {
            return 0;
        }
        if (borrower == lender) {
            source.sendError(Text.literal("You cannot borrow from yourself."));
            return 0;
        }
        if (!validPeriod(source, IntegerArgumentType.getInteger(context, "periodDays"))) {
            return 0;
        }

        OfferManager.offerLoan(
            borrower,
            lender,
            principal,
            bps(DoubleArgumentType.getDouble(context, "ratePercent")),
            IntegerArgumentType.getInteger(context, "periodDays"),
            IntegerArgumentType.getInteger(context, "installments"),
            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")));
        return 1;
    }

    private static int loanBank(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        Loans.borrowFromBank(player, amount, IntegerArgumentType.getInteger(context, "installments"));
        return 1;
    }

    private static int loanList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String me = player.getUuid().toString();
        List<Loan> loans = Loans.involving(Obligations.get(source.getServer()), me);
        if (loans.isEmpty()) {
            source.sendFeedback(() -> Text.literal("You have no loans."), false);
            return 1;
        }
        for (Loan loan : loans) {
            source.sendFeedback(() -> Text.literal(Loans.describe(source.getServer(), loan, me) + "."), false);
        }
        return 1;
    }

    private static int bondVault(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Obligations state = Obligations.get(context.getSource().getServer());
        List<Bond> held = Bonds.vaulted(state, player);
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("Your bank vault holds no bonds."), false);
            return 1;
        }
        for (int i = 0; i < held.size(); i++) {
            String line = Bonds.describe(context.getSource().getServer(), state, held.get(i));
            player.sendMessage(Text.literal((i + 1) + ". " + line), false);
        }
        return 1;
    }

    private static int bondMarket(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<String> lines = BondMarket.describe(source.getServer());
        if (lines.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No bonds are on offer."), false);
            return 1;
        }
        for (String line : lines) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int bondIssue(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity issuer = source.getPlayerOrThrow();
        long principal = amount(source, StringArgumentType.getString(context, "principal"));
        if (principal <= 0
            || !validPeriod(source, IntegerArgumentType.getInteger(context, "couponDays"))) {
            return 0;
        }

        BondOffering offering = BondMarket.issue(
            source.getServer(),
            issuer.getUuid().toString(),
            issuer.getName().getString(),
            principal,
            bps(DoubleArgumentType.getDouble(context, "ratePercent")),
            IntegerArgumentType.getInteger(context, "couponDays"),
            IntegerArgumentType.getInteger(context, "termDays"),
            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")),
            IntegerArgumentType.getInteger(context, "count"));
        source.sendFeedback(() -> Text.literal("Bond offering " + offering.id + " is on the market: "
            + offering.remaining + " bonds of " + Currency.format(offering.principal)
            + ". You owe each investor its coupons and the principal at maturity."), false);
        return 1;
    }

    private static int bondCancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity issuer = source.getPlayerOrThrow();
        int id = IntegerArgumentType.getInteger(context, "offering");
        if (!BondMarket.cancel(source.getServer(), id, issuer.getUuid().toString())) {
            source.sendError(Text.literal("You have no bond offering " + id + "."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Withdrew the unsold bonds of offering " + id + "."), false);
        return 1;
    }

    private static int treasuryIssue(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        long principal = amount(source, StringArgumentType.getString(context, "principal"));
        if (principal <= 0
            || !validPeriod(source, IntegerArgumentType.getInteger(context, "couponDays"))) {
            return 0;
        }

        BondOffering offering = BondMarket.issue(
            source.getServer(),
            "",
            BondMarket.TREASURY,
            principal,
            bps(DoubleArgumentType.getDouble(context, "ratePercent")),
            IntegerArgumentType.getInteger(context, "couponDays"),
            IntegerArgumentType.getInteger(context, "termDays"),
            0,
            IntegerArgumentType.getInteger(context, "count"));
        source.sendFeedback(() -> Text.literal("Created bond offering " + offering.id + " of "
            + offering.remaining + " bonds."), true);
        return 1;
    }

    private static int treasuryCancel(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "offering");
        if (!BondMarket.cancel(source.getServer(), id, null)) {
            source.sendError(Text.literal("No bond offering " + id + "."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Cancelled bond offering " + id + "."), true);
        return 1;
    }
}
