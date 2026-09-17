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
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

// The /eco command tree plus the top-level /pay command.
public final class EconomyCommand {

    private EconomyCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("pay")
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("amount", StringArgumentType.word())
                    .executes(EconomyCommand::pay))));

        dispatcher.register(CommandManager.literal("deal")
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(EconomyCommand::dealOpen))
            .then(CommandManager.literal("offer")
                .then(CommandManager.argument("amount", StringArgumentType.word())
                    .executes(EconomyCommand::dealOffer)))
            .then(CommandManager.literal("confirm")
                .executes(context -> dealSimple(context, 0)))
            .then(CommandManager.literal("cancel")
                .executes(context -> dealSimple(context, 1)))
            .then(CommandManager.literal("info")
                .executes(context -> dealSimple(context, 2))));

        dispatcher.register(CommandManager.literal("eco")
            .executes(EconomyCommand::openHub)
            .then(CommandManager.literal("balance")
                .executes(context -> balanceSelf(context.getSource()))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .executes(EconomyCommand::balanceOther)))
            .then(CommandManager.literal("deposit")
                .then(CommandManager.argument("amount", StringArgumentType.word())
                    .executes(context -> deposit(context))))
            .then(CommandManager.literal("withdraw")
                .then(CommandManager.argument("amount", StringArgumentType.word())
                    .executes(context -> withdraw(context))))
            .then(CommandManager.literal("history")
                .executes(context -> history(context.getSource(), context.getSource().getPlayer())))
            .then(CommandManager.literal("market")
                .executes(context -> {
                    info.mudbourn.mmseconomy.market.Marketplace.browse(context.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.literal("buy")
                    .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            info.mudbourn.mmseconomy.market.Marketplace.buy(
                                context.getSource().getPlayerOrThrow(),
                                IntegerArgumentType.getInteger(context, "index"));
                            return 1;
                        }))))
            .then(CommandManager.literal("shop")
                .then(CommandManager.literal("list")
                    .then(CommandManager.argument("price", StringArgumentType.word())
                        .executes(EconomyCommand::shopList)))
                .then(CommandManager.literal("unlist")
                    .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            info.mudbourn.mmseconomy.market.Marketplace.unlist(
                                context.getSource().getPlayerOrThrow(),
                                IntegerArgumentType.getInteger(context, "index"));
                            return 1;
                        }))))
            .then(CommandManager.literal("sell")
                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        info.mudbourn.mmseconomy.market.Marketplace.serverSell(
                            context.getSource().getPlayerOrThrow(),
                            IntegerArgumentType.getInteger(context, "count"));
                        return 1;
                    })))
            .then(CommandManager.literal("buylist")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("reload")
                    .executes(context -> {
                        info.mudbourn.mmseconomy.market.ServerBuyList.load();
                        context.getSource().sendFeedback(() -> Text.literal("Reloaded the server buy-list."), true);
                        return 1;
                    })))
            .then(CommandManager.literal("library")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(context -> libraryList(context.getSource()))
                .then(CommandManager.literal("list")
                    .executes(context -> libraryList(context.getSource())))
                .then(CommandManager.literal("withdraw")
                    .then(CommandManager.argument("item", StringArgumentType.string())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(
                            ServerLibrary.get(ctx.getSource().getServer()).view().keySet(), builder))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                            .executes(EconomyCommand::libraryWithdraw)))))
            .then(CommandManager.literal("debug")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("mint")
                    .then(CommandManager.argument("amount", StringArgumentType.word())
                        .executes(EconomyCommand::debugMint)))
                .then(CommandManager.literal("access")
                    .executes(EconomyCommand::debugAccess))
                .then(CommandManager.literal("open")
                    .executes(EconomyCommand::debugOpen)))
            .then(CommandManager.literal("give")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("amount", StringArgumentType.word())
                        .executes(context -> give(context, true)))))
            .then(CommandManager.literal("take")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("amount", StringArgumentType.word())
                        .executes(context -> give(context, false)))))
            .then(CommandManager.literal("treasury")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("balance")
                    .executes(context -> treasuryBalance(context.getSource())))
                .then(CommandManager.literal("pay")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", StringArgumentType.word())
                            .executes(context -> treasuryMove(context, true)))))
                .then(CommandManager.literal("take")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", StringArgumentType.word())
                            .executes(context -> treasuryMove(context, false))))))
            .then(CommandManager.literal("config")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .executes(context -> showConfig(context.getSource()))
                .then(CommandManager.argument("key", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(EconomyConfig.KEYS, builder))
                    .then(CommandManager.argument("value", StringArgumentType.word())
                        .executes(context -> setConfig(context.getSource(),
                            StringArgumentType.getString(context, "key"),
                            StringArgumentType.getString(context, "value")))))));
    }

    private static long parseAmount(ServerCommandSource source, String text) {
        long pice = Currency.parse(text);
        if (pice <= 0) {
            source.sendError(Text.literal("Enter an amount like 12.34 or a whole number of Pice."));
        }
        return pice;
    }

    private static int pay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (!MmsEconomy.config().payEnabled) {
            source.sendError(Text.literal("Payments are disabled."));
            return 0;
        }

        ServerPlayerEntity sender = source.getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        if (sender == target) {
            source.sendError(Text.literal("You cannot pay yourself."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Wallet.canAfford(sender, amount)) {
            source.sendError(Text.literal("You only have " + Currency.format(Wallet.balance(sender)) + "."));
            return 0;
        }

        Tax.Settlement settlement = Tax.settleTaxed(sender, target, amount, TaxKind.PAY);
        if (settlement == null) {
            source.sendError(Text.literal("Payment failed."));
            return 0;
        }

        long day = info.mudbourn.mmseconomy.history.History.currentDay(sender);
        info.mudbourn.mmseconomy.history.History.log(sender, new info.mudbourn.mmseconomy.history.HistoryEntry(
            day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), -settlement.gross(), target.getName().getString()));
        info.mudbourn.mmseconomy.history.History.log(target, new info.mudbourn.mmseconomy.history.HistoryEntry(
            day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
            settlement.gross(), settlement.tax(), settlement.net(), sender.getName().getString()));

        sender.sendMessage(Text.literal("Paid " + target.getName().getString()
            + ": sent " + Currency.format(settlement.gross())
            + ", tax " + Currency.format(settlement.tax())
            + ", net " + Currency.format(settlement.net()) + "."), false);
        target.sendMessage(Text.literal("Received " + Currency.format(settlement.net())
            + " from " + sender.getName().getString()
            + " (tax " + Currency.format(settlement.tax()) + ")."), false);
        return 1;
    }

    private static int balanceSelf(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        source.sendFeedback(() -> Text.literal("Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int balanceOther(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        context.getSource().sendFeedback(() -> Text.literal(target.getName().getString()
            + " balance: " + Currency.format(Wallet.balance(target))), false);
        return 1;
    }

    private static int deposit(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!info.mudbourn.mmseconomy.economy.DebugAccess.has(player)
            && BankAccess.nearestBank(player, MmsEconomy.config().bankRange) == null) {
            source.sendError(Text.literal("Stand near a bank block to use it."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Gems.removeExact(player, amount)) {
            source.sendError(Text.literal("You do not have gems worth exactly " + Currency.format(amount) + "."));
            return 0;
        }

        Wallet.deposit(player, amount);
        source.sendFeedback(() -> Text.literal("Deposited " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int withdraw(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!info.mudbourn.mmseconomy.economy.DebugAccess.has(player)
            && BankAccess.nearestBank(player, MmsEconomy.config().bankRange) == null) {
            source.sendError(Text.literal("Stand near a bank block to use it."));
            return 0;
        }

        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        if (!Wallet.withdraw(player, amount)) {
            source.sendError(Text.literal("You only have " + Currency.format(Wallet.balance(player)) + "."));
            return 0;
        }

        Gems.give(player, amount);
        source.sendFeedback(() -> Text.literal("Withdrew " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> context, boolean add) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        if (add) {
            Wallet.deposit(target, amount);
            source.sendFeedback(() -> Text.literal("Gave " + Currency.format(amount)
                + " to " + target.getName().getString() + "."), true);
        } else {
            if (!Wallet.withdraw(target, amount)) {
                source.sendError(Text.literal(target.getName().getString()
                    + " only has " + Currency.format(Wallet.balance(target)) + "."));
                return 0;
            }
            source.sendFeedback(() -> Text.literal("Took " + Currency.format(amount)
                + " from " + target.getName().getString() + "."), true);
        }
        return 1;
    }

    private static int libraryList(ServerCommandSource source) {
        Map<String, Long> items = ServerLibrary.get(source.getServer()).view();
        if (items.isEmpty()) {
            source.sendFeedback(() -> Text.literal("The server library is empty."), false);
            return 1;
        }
        source.sendFeedback(() -> Text.literal("Server library (" + items.size() + " kinds):"), false);
        for (Map.Entry<String, Long> entry : items.entrySet()) {
            source.sendFeedback(() -> Text.literal("  " + Marketplace.displayName(entry.getKey())
                + " x" + entry.getValue() + "  (" + entry.getKey() + ")"), false);
        }
        return 1;
    }

    private static int libraryWithdraw(CommandContext<ServerCommandSource> context)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String itemId = StringArgumentType.getString(context, "item");
        int count = IntegerArgumentType.getInteger(context, "count");
        Item item = Marketplace.itemOf(itemId);
        if (item == null || item == Items.AIR) {
            source.sendError(Text.literal("Unknown item: " + itemId));
            return 0;
        }

        long taken = ServerLibrary.get(source.getServer()).take(itemId, count);
        if (taken <= 0) {
            source.sendError(Text.literal("The library holds none of that."));
            return 0;
        }

        int maxStack = new ItemStack(item).getMaxCount();
        long remaining = taken;
        while (remaining > 0) {
            int per = (int) Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, per);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            remaining -= per;
        }
        long withdrawn = taken;
        source.sendFeedback(() -> Text.literal("Withdrew " + withdrawn + " "
            + Marketplace.displayName(itemId) + " from the library."), true);
        return 1;
    }

    private static int treasuryBalance(ServerCommandSource source) {
        long balance = Treasury.get(source.getServer()).balance();
        source.sendFeedback(() -> Text.literal("Treasury: " + Currency.format(balance)), false);
        return 1;
    }

    private static int treasuryMove(CommandContext<ServerCommandSource> context, boolean pay)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }

        Treasury treasury = Treasury.get(source.getServer());
        if (pay) {
            if (!treasury.debit(amount)) {
                source.sendError(Text.literal("Treasury holds only " + Currency.format(treasury.balance()) + "."));
                return 0;
            }
            Wallet.deposit(target, amount);
            source.sendFeedback(() -> Text.literal("Disbursed " + Currency.format(amount)
                + " to " + target.getName().getString() + "."), true);
        } else {
            if (!Wallet.withdraw(target, amount)) {
                source.sendError(Text.literal(target.getName().getString()
                    + " only has " + Currency.format(Wallet.balance(target)) + "."));
                return 0;
            }
            treasury.credit(amount);
            source.sendFeedback(() -> Text.literal("Collected " + Currency.format(amount)
                + " from " + target.getName().getString() + " into the treasury."), true);
        }
        return 1;
    }

    private static int showConfig(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("Economy config: " + MmsEconomy.config().describe()), false);
        return 1;
    }

    private static int setConfig(ServerCommandSource source, String key, String value) {
        if (!MmsEconomy.config().set(key, value)) {
            source.sendError(Text.literal("Unknown config key or bad value: " + key + " = " + value + "."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Set " + key + " = " + value + "."), true);
        return 1;
    }

    private static int dealOpen(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (!MmsEconomy.config().dealEnabled) {
            source.sendError(Text.literal("Deals are disabled."));
            return 0;
        }
        ServerPlayerEntity initiator = source.getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        if (initiator == target) {
            source.sendError(Text.literal("You cannot deal with yourself."));
            return 0;
        }
        info.mudbourn.mmseconomy.deal.DealManager.open(initiator, target);
        return 1;
    }

    private static int dealOffer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        long amount = Currency.parse(StringArgumentType.getString(context, "amount"));
        if (amount < 0) {
            source.sendError(Text.literal("Enter an amount like 12.34 or a whole number of Pice."));
            return 0;
        }
        info.mudbourn.mmseconomy.deal.DealManager.offer(source.getPlayerOrThrow(), amount);
        return 1;
    }

    private static int dealSimple(CommandContext<ServerCommandSource> context, int op)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        switch (op) {
            case 0 -> info.mudbourn.mmseconomy.deal.DealManager.confirm(player);
            case 1 -> info.mudbourn.mmseconomy.deal.DealManager.cancel(player);
            default -> info.mudbourn.mmseconomy.deal.DealManager.info(player);
        }
        return 1;
    }

    private static int debugMint(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        long amount = parseAmount(source, StringArgumentType.getString(context, "amount"));
        if (amount <= 0) {
            return 0;
        }
        Wallet.deposit(player, amount);
        source.sendFeedback(() -> Text.literal("Minted " + Currency.format(amount)
            + ". Balance: " + Currency.format(Wallet.balance(player))), false);
        return 1;
    }

    private static int debugAccess(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        boolean enabled = info.mudbourn.mmseconomy.economy.DebugAccess.toggle(player);
        context.getSource().sendFeedback(() -> Text.literal("Debug full-UI access "
            + (enabled ? "enabled" : "disabled") + "."), false);
        return 1;
    }

    private static int debugOpen(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHub(player,
            BankAccess.nearestBank(player, MmsEconomy.config().bankRange));
        return 1;
    }

    private static int shopList(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        long price = Currency.parse(StringArgumentType.getString(context, "price"));
        if (price <= 0) {
            source.sendError(Text.literal("Set a price like 12.34 or a whole number of Pice."));
            return 0;
        }
        info.mudbourn.mmseconomy.market.Marketplace.list(source.getPlayerOrThrow(), price);
        return 1;
    }

    private static int openHub(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHub(player, bank);
        return 1;
    }

    private static int history(ServerCommandSource source, ServerPlayerEntity player) {
        if (player == null) {
            source.sendError(Text.literal("Only a player has a history."));
            return 0;
        }
        info.mudbourn.mmseconomy.network.EconomyNetworking.openHistory(player);
        return 1;
    }
}
