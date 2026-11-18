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
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// The /bill, /bond and /loan commands plus the admin /eco bond subtree.
public final class LendingCommand {

    private LendingCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bill")
            .then(Commands.literal("create")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .then(Commands.argument("intervalDays", IntegerArgumentType.integer(1, 3650))
                            .then(Commands.argument("lateRatePercent", DoubleArgumentType.doubleArg(0, 10))
                                .then(Commands.argument("label", StringArgumentType.greedyString())
                                    .executes(LendingCommand::billCreate)))))))
            .then(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .then(Commands.argument("intervalDays", IntegerArgumentType.integer(1, 3650))
                            .then(Commands.literal("late")
                                .then(Commands.argument("lateRatePercent", DoubleArgumentType.doubleArg(0, 10))
                                    .then(Commands.argument("label", StringArgumentType.greedyString())
                                        .executes(context -> billPay(context,
                                            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")))))))
                            .then(Commands.argument("label", StringArgumentType.greedyString())
                                .executes(context -> billPay(context, 0)))))))
            .then(Commands.literal("accept")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        OfferManager.acceptBill(context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "id"));
                        return 1;
                    })))
            .then(Commands.literal("list")
                .executes(LendingCommand::billList))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(LendingCommand::billCancel))));

        dispatcher.register(Commands.literal("bond")
            .then(Commands.literal("issue")
                .then(Commands.argument("principal", StringArgumentType.word())
                    .then(Commands.argument("ratePercent", DoubleArgumentType.doubleArg(0, 6.5))
                        .then(Commands.argument("couponDays", IntegerArgumentType.integer(1, 30))
                            .then(Commands.argument("termDays", IntegerArgumentType.integer(1, 36500))
                                .then(Commands.argument("lateRatePercent",
                                        DoubleArgumentType.doubleArg(0, 10))
                                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(LendingCommand::bondIssue))))))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("offering", IntegerArgumentType.integer(1))
                    .executes(LendingCommand::bondCancel)))
            .then(Commands.literal("market")
                .executes(LendingCommand::bondMarket))
            .then(Commands.literal("buy")
                .then(Commands.argument("offering", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        BondMarket.buy(context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "offering"));
                        return 1;
                    })))
            .then(Commands.literal("vault")
                .executes(LendingCommand::bondVault))
            .then(Commands.literal("withdraw")
                .then(Commands.argument("index", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        Bonds.withdraw(context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "index"));
                        return 1;
                    }))));

        dispatcher.register(Commands.literal("loan")
            .then(Commands.literal("request")
                .then(Commands.argument("lender", EntityArgument.player())
                    .then(Commands.argument("principal", StringArgumentType.word())
                        .then(Commands.argument("ratePercent",
                                DoubleArgumentType.doubleArg(0, Loans.MAX_RATE_PERCENT))
                            .then(Commands.argument("periodDays", IntegerArgumentType.integer(1, 30))
                                .then(Commands.argument("installments",
                                        IntegerArgumentType.integer(1, Loans.MAX_INSTALLMENTS))
                                    .then(Commands.argument("lateRatePercent",
                                            DoubleArgumentType.doubleArg(0, 10))
                                        .executes(LendingCommand::loanRequest))))))))
            .then(Commands.literal("accept")
                .executes(context -> {
                    OfferManager.acceptLoan(context.getSource().getPlayerOrException());
                    return 1;
                }))
            .then(Commands.literal("bank")
                .then(Commands.argument("amount", StringArgumentType.word())
                    .then(Commands.argument("installments",
                            IntegerArgumentType.integer(1, Loans.MAX_INSTALLMENTS))
                        .executes(LendingCommand::loanBank))))
            .then(Commands.literal("list")
                .executes(LendingCommand::loanList))
            .then(Commands.literal("repay")
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        Loans.repay(context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "id"));
                        return 1;
                    }))));

        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("eco")
            .then(Commands.literal("bond")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("issue")
                    .then(Commands.argument("principal", StringArgumentType.word())
                        .then(Commands.argument("ratePercent", DoubleArgumentType.doubleArg(0, 6.5))
                            .then(Commands.argument("couponDays", IntegerArgumentType.integer(1, 30))
                                .then(Commands.argument("termDays", IntegerArgumentType.integer(1, 36500))
                                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 10000))
                                        .executes(LendingCommand::treasuryIssue)))))))
                .then(Commands.literal("list")
                    .executes(LendingCommand::bondMarket))
                .then(Commands.literal("cancel")
                    .then(Commands.argument("offering", IntegerArgumentType.integer(1))
                        .executes(LendingCommand::treasuryCancel))));
        dispatcher.register(admin);
    }

    private static boolean validPeriod(CommandSourceStack source, int days) {
        if (days != 7 && days != 30) {
            source.sendFailure(Component.literal("The period must be 7 (weekly) or 30 (monthly) days."));
            return false;
        }
        return true;
    }

    private static int bps(double percent) {
        return (int) Math.round(percent * 100.0);
    }

    private static long amount(CommandSourceStack source, String text) {
        long pice = Currency.parse(text);
        if (pice <= 0) {
            source.sendFailure(Component.literal("Enter an amount like 12.34 or a whole number of Pice."));
        }
        return pice;
    }

    private static int billCreate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer payee = source.getPlayerOrException();
        ServerPlayer payer = EntityArgument.getPlayer(context, "player");
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (payee == payer) {
            source.sendFailure(Component.literal("You cannot bill yourself."));
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

    private static int billPay(CommandContext<CommandSourceStack> context, int lateBps)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer payer = source.getPlayerOrException();
        ServerPlayer payee = EntityArgument.getPlayer(context, "player");
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (payee == payer) {
            source.sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }

        Bill bill = Billing.create(
            source.getServer(),
            payer,
            payee.getUUID(),
            payee.getName().getString(),
            amount,
            IntegerArgumentType.getInteger(context, "intervalDays"),
            lateBps,
            StringArgumentType.getString(context, "label"));
        payer.sendSystemMessage(Component.literal("Bill " + bill.id + " started: " + Currency.format(amount)
            + " to " + bill.payeeName + " every " + bill.intervalDays + " days, first due day "
            + bill.nextDue + "."));
        payee.sendSystemMessage(Component.literal(bill.payerName + " will pay you " + Currency.format(amount)
            + " every " + bill.intervalDays + " days (" + bill.label + ")."));
        return 1;
    }

    private static int billList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String me = player.getUUID().toString();
        Obligations state = Obligations.get(source.getServer());

        boolean any = false;
        for (Bill bill : state.bills) {
            boolean paying = bill.payer.equals(me);
            if (!paying && !bill.payee.equals(me)) {
                continue;
            }
            any = true;
            String other = paying ? "you pay " + bill.payeeName : bill.payerName + " pays you";
            source.sendSuccess(() -> Component.literal("Bill " + bill.id + ": " + other + " "
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
            source.sendSuccess(() -> Component.literal("Debt: " + who + " " + Currency.format(debt.amount)
                + " for " + debt.label + ", late rate " + BondPaper.percent(debt.lateBps) + " per day"), false);
        }
        if (!any) {
            source.sendSuccess(() -> Component.literal("You have no bills or debts."), false);
        }
        return 1;
    }

    private static int billCancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Obligations state = Obligations.get(source.getServer());
        Bill bill = state.bill(IntegerArgumentType.getInteger(context, "id"));
        String me = player.getUUID().toString();
        if (bill == null || (!bill.payer.equals(me) && !bill.payee.equals(me))) {
            source.sendFailure(Component.literal("You have no such bill."));
            return 0;
        }

        state.bills.remove(bill);
        state.setDirty();
        source.sendSuccess(() -> Component.literal("Bill " + bill.id + " cancelled. Any debt stays owed."), false);
        Billing.tell(source.getServer(), bill.payer.equals(me) ? bill.payee : bill.payer,
            player.getName().getString() + " cancelled bill " + bill.id + " (" + bill.label + ").");
        return 1;
    }

    private static int loanRequest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer borrower = source.getPlayerOrException();
        ServerPlayer lender = EntityArgument.getPlayer(context, "lender");
        long principal = amount(source, StringArgumentType.getString(context, "principal"));
        if (principal <= 0) {
            return 0;
        }
        if (borrower == lender) {
            source.sendFailure(Component.literal("You cannot borrow from yourself."));
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

    private static int loanBank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        long amount = amount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        Loans.borrowFromBank(player, amount, IntegerArgumentType.getInteger(context, "installments"));
        return 1;
    }

    private static int loanList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String me = player.getUUID().toString();
        List<Loan> loans = Loans.involving(Obligations.get(source.getServer()), me);
        if (loans.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no loans."), false);
            return 1;
        }
        for (Loan loan : loans) {
            source.sendSuccess(() -> Component.literal(Loans.describe(source.getServer(), loan, me) + "."), false);
        }
        return 1;
    }

    private static int bondVault(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Obligations state = Obligations.get(context.getSource().getServer());
        List<Bond> held = Bonds.vaulted(state, player);
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your bank vault holds no bonds."));
            return 1;
        }
        for (int i = 0; i < held.size(); i++) {
            String line = Bonds.describe(context.getSource().getServer(), state, held.get(i));
            player.sendSystemMessage(Component.literal((i + 1) + ". " + line));
        }
        return 1;
    }

    private static int bondMarket(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> lines = BondMarket.describe(source.getServer());
        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No bonds are on offer."), false);
            return 1;
        }
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int bondIssue(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer issuer = source.getPlayerOrException();
        long principal = amount(source, StringArgumentType.getString(context, "principal"));
        if (principal <= 0
            || !validPeriod(source, IntegerArgumentType.getInteger(context, "couponDays"))) {
            return 0;
        }

        BondOffering offering = BondMarket.issue(
            source.getServer(),
            issuer.getUUID().toString(),
            issuer.getName().getString(),
            principal,
            bps(DoubleArgumentType.getDouble(context, "ratePercent")),
            IntegerArgumentType.getInteger(context, "couponDays"),
            IntegerArgumentType.getInteger(context, "termDays"),
            bps(DoubleArgumentType.getDouble(context, "lateRatePercent")),
            IntegerArgumentType.getInteger(context, "count"));
        source.sendSuccess(() -> Component.literal("Bond offering " + offering.id + " is on the market: "
            + offering.remaining + " bonds of " + Currency.format(offering.principal)
            + ". You owe each investor its coupons and the principal at maturity."), false);
        return 1;
    }

    private static int bondCancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer issuer = source.getPlayerOrException();
        int id = IntegerArgumentType.getInteger(context, "offering");
        if (!BondMarket.cancel(source.getServer(), id, issuer.getUUID().toString())) {
            source.sendFailure(Component.literal("You have no bond offering " + id + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Withdrew the unsold bonds of offering " + id + "."), false);
        return 1;
    }

    private static int treasuryIssue(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
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
        source.sendSuccess(() -> Component.literal("Created bond offering " + offering.id + " of "
            + offering.remaining + " bonds."), true);
        return 1;
    }

    private static int treasuryCancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int id = IntegerArgumentType.getInteger(context, "offering");
        if (!BondMarket.cancel(source.getServer(), id, null)) {
            source.sendFailure(Component.literal("No bond offering " + id + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cancelled bond offering " + id + "."), true);
        return 1;
    }
}
