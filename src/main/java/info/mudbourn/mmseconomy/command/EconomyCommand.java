package info.mudbourn.mmseconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import info.mudbourn.mmseconomy.economy.BankAccess;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Gems;
import info.mudbourn.mmseconomy.economy.Tax;
import info.mudbourn.mmseconomy.economy.TaxKind;
import info.mudbourn.mmseconomy.economy.ServerLibrary;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.market.Marketplace;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// The /eco command tree plus the top-level /pay command.
public final class EconomyCommand {

    private EconomyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(EconomyCommand::pay))));

        DealCommand.register(dispatcher);

        LendingCommand.register(dispatcher);

        dispatcher.register(Commands.literal("eco")
            .executes(EconomyCommand::openHub)
            .then(Commands.literal("balance")
                .executes(context -> balanceSelf(context.getSource()))
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(EconomyCommand::balanceOther)))
            .then(Commands.literal("deposit")
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(context -> deposit(context))))
            .then(Commands.literal("withdraw")
                .then(Commands.argument("amount", StringArgumentType.word())
                    .executes(context -> withdraw(context))))
            .then(Commands.literal("history")
                .executes(context -> history(context.getSource(), context.getSource().getPlayer())))
            .then(Commands.literal("market")
                .executes(context -> {
                    info.mudbourn.mmseconomy.market.Marketplace.browse(context.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("buy")
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            info.mudbourn.mmseconomy.market.Marketplace.buy(
                                context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "index"));
                            return 1;
                        }))))
            .then(Commands.literal("shop")
                .then(Commands.literal("list")
                    .then(Commands.argument("price", StringArgumentType.word())
                        .executes(EconomyCommand::shopList)))
                .then(Commands.literal("unlist")
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            info.mudbourn.mmseconomy.market.Marketplace.unlist(
                                context.getSource().getPlayerOrException(),
                                IntegerArgumentType.getInteger(context, "index"));
                            return 1;
                        }))))
            .then(Commands.literal("sell")
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        info.mudbourn.mmseconomy.market.Marketplace.serverSell(
                            context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "count"));
                        return 1;
                    })))
            .then(Commands.literal("buylist")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reload")
                    .executes(context -> {
                        info.mudbourn.mmseconomy.market.ServerBuyList.load();
                        context.getSource().sendSuccess(() -> Component.literal("Reloaded the server buy-list."), true);
                        return 1;
                    })))
            .then(Commands.literal("library")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> libraryList(context.getSource()))
                .then(Commands.literal("list")
                    .executes(context -> libraryList(context.getSource())))
                .then(Commands.literal("withdraw")
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ServerLibrary.get(ctx.getSource().getServer()).view().keySet(), builder))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(EconomyCommand::libraryWithdraw)))))
            .then(Commands.literal("debug")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("mint")
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(EconomyCommand::debugMint)))
                .then(Commands.literal("access")
                    .executes(EconomyCommand::debugAccess))
                .then(Commands.literal("open")
                    .executes(EconomyCommand::debugOpen)))
            .then(Commands.literal("give")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> give(context, true)))))
            .then(Commands.literal("take")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> give(context, false)))))
            .then(Commands.literal("treasury")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("balance")
                    .executes(context -> treasuryBalance(context.getSource())))
                .then(Commands.literal("pay")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", StringArgumentType.word())
                            .executes(context -> treasuryMove(context, true)))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", StringArgumentType.word())
                            .executes(context -> treasuryMove(context, false))))))
            .then(Commands.literal("config")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> showConfig(context.getSource()))
                .then(Commands.argument("key", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(EconomyConfig.KEYS, builder))
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(context -> setConfig(context.getSource(),
                            StringArgumentType.getString(context, "key"),
                            StringArgumentType.getString(context, "value")))))));
    }

    private static long parseAmount(CommandSourceStack source, String text) {
        long pice = Currency.parse(text);
        if (pice <= 0) {
            source.sendFailure(Component.literal("Enter an amount like 12.34 or a whole number of Pice."));
        }
        return pice;
    }

    private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!MmsEconomy.config().payEnabled) {
            source.sendFailure(Component.literal("Payments are disabled."));
            return 0;
        }

        ServerPlayer sender = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        if (sender == target) {
            source.sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Wallet.canAfford(sender, amount)) {
            source.sendFailure(Component.literal("You only have " + Currency.format(Wallet.balance(sender)) + "."));
            return 0;
        }

        Tax.Settlement settlement = Tax.settleTaxed(sender, target, amount, TaxKind.PAY);
        if (settlement == null) {
            source.sendFailure(Component.literal("Payment failed."));
            return 0;
        }

        long day = info.mudbourn.mmseconomy.history.History.currentDay(sender);
        info.mudbourn.mmseconomy.history.History.log(sender, new info.mudbourn.mmseconomy.history.HistoryEntry(
            day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), -settlement.gross(), target.getName().getString()));
        info.mudbourn.mmseconomy.history.History.log(target, new info.mudbourn.mmseconomy.history.HistoryEntry(
            day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), settlement.net(), sender.getName().getString()));

        sender.sendSystemMessage(Component.literal("Paid " + target.getName().getString()
            + ": sent " + Currency.format(settlement.gross())
            + ", tax " + Currency.format(settlement.tax())
            + ", net " + Currency.format(settlement.net()) + "."));
        target.sendSystemMessage(Component.literal("Received " + Currency.format(settlement.net())
            + " from " + sender.getName().getString()
            + " (tax " + Currency.format(settlement.tax()) + ")."));
        return 1;
    }

    private static int balanceSelf(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal("Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int balanceOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        context.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
            + " balance: " + Currency.format(Wallet.balance(target))), false);
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!info.mudbourn.mmseconomy.economy.DebugAccess.has(player)
            && BankAccess.nearestBank(player, MmsEconomy.config().bankRange) == null) {
            source.sendFailure(Component.literal("Stand near a bank block to use it."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Gems.removeExact(player, amount)) {
            source.sendFailure(Component.literal("You do not have gems worth exactly " + Currency.format(amount) + "."));
            return 0;
        }

        Wallet.deposit(player, amount);
        source.sendSuccess(() -> Component.literal("Deposited " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!info.mudbourn.mmseconomy.economy.DebugAccess.has(player)
            && BankAccess.nearestBank(player, MmsEconomy.config().bankRange) == null) {
            source.sendFailure(Component.literal("Stand near a bank block to use it."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Wallet.withdraw(player, amount)) {
            source.sendFailure(Component.literal("You only have " + Currency.format(Wallet.balance(player)) + "."));
            return 0;
        }

        Gems.give(player, amount);
        source.sendSuccess(() -> Component.literal("Withdrew " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        if (add) {
            Wallet.deposit(target, amount);
            source.sendSuccess(() -> Component.literal("Gave " + Currency.format(amount)
                + " to " + target.getName().getString() + "."), true);
        } else {
            if (!Wallet.withdraw(target, amount)) {
                source.sendFailure(Component.literal(target.getName().getString()
                    + " only has " + Currency.format(Wallet.balance(target)) + "."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Took " + Currency.format(amount)
                + " from " + target.getName().getString() + "."), true);
        }
        return 1;
    }

    private static int libraryList(CommandSourceStack source) {
        Map<String, Long> items = ServerLibrary.get(source.getServer()).view();
        if (items.isEmpty()) {
            source.sendSuccess(() -> Component.literal("The server library is empty."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Server library (" + items.size() + " kinds):"), false);
        for (Map.Entry<String, Long> entry : items.entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + Marketplace.displayName(entry.getKey())
                + " x" + entry.getValue() + "  (" + entry.getKey() + ")"), false);
        }
        return 1;
    }

    private static int libraryWithdraw(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String itemId = StringArgumentType.getString(context, "item");
        int count = IntegerArgumentType.getInteger(context, "count");
        Item item = Marketplace.itemOf(itemId);
        if (item == null || item == Items.AIR) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }

        long taken = ServerLibrary.get(source.getServer()).take(itemId, count);
        if (taken <= 0) {
            source.sendFailure(Component.literal("The library holds none of that."));
            return 0;
        }

        int maxStack = new ItemStack(item).getMaxStackSize();
        long remaining = taken;
        while (remaining > 0) {
            int per = (int) Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, per);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
            remaining -= per;
        }
        long withdrawn = taken;
        source.sendSuccess(() -> Component.literal("Withdrew " + withdrawn + " "
            + Marketplace.displayName(itemId) + " from the library."), true);
        return 1;
    }

    private static int treasuryBalance(CommandSourceStack source) {
        long balance = Treasury.get(source.getServer()).balance();
        source.sendSuccess(() -> Component.literal("Treasury: " + Currency.format(balance)), false);
        return 1;
    }

    private static int treasuryMove(CommandContext<CommandSourceStack> context, boolean pay)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        Treasury treasury = Treasury.get(source.getServer());
        if (pay) {
            if (!treasury.debit(amount)) {
                source.sendFailure(Component.literal("Treasury holds only " + Currency.format(treasury.balance()) + "."));
                return 0;
            }
            Wallet.deposit(target, amount);
            source.sendSuccess(() -> Component.literal("Disbursed " + Currency.format(amount)
                + " to " + target.getName().getString() + "."), true);
        } else {
            if (!Wallet.withdraw(target, amount)) {
                source.sendFailure(Component.literal(target.getName().getString()
                    + " only has " + Currency.format(Wallet.balance(target)) + "."));
                return 0;
            }
            treasury.credit(amount);
            source.sendSuccess(() -> Component.literal("Collected " + Currency.format(amount)
                + " from " + target.getName().getString() + " into the treasury."), true);
        }
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Economy config: " + MmsEconomy.config().describe()), false);
        return 1;
    }

    private static int setConfig(CommandSourceStack source, String key, String value) {
        if (!MmsEconomy.config().set(key, value)) {
            source.sendFailure(Component.literal("Unknown config key or bad value: " + key + " = " + value + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Set " + key + " = " + value + "."), true);
        return 1;
    }

    private static int debugMint(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        Wallet.deposit(player, amount);
        source.sendSuccess(() -> Component.literal("Minted " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int debugAccess(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = info.mudbourn.mmseconomy.economy.DebugAccess.toggle(player);
        context.getSource().sendSuccess(() -> Component.literal("Debug full-UI access "
            + (enabled ? "enabled" : "disabled") + "."), false);
        return 1;
    }

    private static int debugOpen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHub(player,
            BankAccess.nearestBank(player, MmsEconomy.config().bankRange));
        return 1;
    }

    private static int shopList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        long price = Currency.parse(StringArgumentType.getString(context, "price"));
        if (price <= 0) {
            source.sendFailure(Component.literal("Set a price like 12.34 or a whole number of Pice."));
            return 0;
        }
        info.mudbourn.mmseconomy.market.Marketplace.list(source.getPlayerOrException(), price, null);
        return 1;
    }

    private static int openHub(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHub(player, bank);
        return 1;
    }

    private static int history(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            source.sendFailure(Component.literal("Only a player has a history."));
            return 0;
        }
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHistory(player);
        return 1;
    }
}
