package info.mudbourn.mmseconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import info.mudbourn.mmseconomy.deal.Wholesale;
import info.mudbourn.mmseconomy.deal.WholesaleManager;
import info.mudbourn.mmseconomy.deal.WholesaleOffer;
import info.mudbourn.mmseconomy.deal.WholesaleOrder;
import info.mudbourn.mmseconomy.economy.Currency;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;

// The /deal text fallback for wholesale offers and orders.
public final class DealCommand {

    private DealCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("deal")
            .then(CommandManager.literal("offers")
                .executes(DealCommand::offers))
            .then(CommandManager.literal("list")
                .executes(DealCommand::orders))
            .then(CommandManager.literal("add")
                .then(CommandManager.argument("lotSize", IntegerArgumentType.integer(1))
                    .then(CommandManager.argument("price", StringArgumentType.word())
                        .executes(DealCommand::add))))
            .then(CommandManager.literal("remove")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::removeOffer))))
            .then(CommandManager.literal("order")
                .then(CommandManager.argument("offerId", IntegerArgumentType.integer(0))
                    .then(CommandManager.argument("lots", IntegerArgumentType.integer(1))
                        .executes(DealCommand::order))))
            .then(CommandManager.literal("accept")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::accept))))
            .then(CommandManager.literal("decline")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::decline))))
            .then(CommandManager.literal("cancel")
                .then(CommandManager.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::cancel)))));
    }

    private static int byId(CommandContext<ServerCommandSource> context,
                            BiConsumer<ServerPlayerEntity, Integer> action) throws CommandSyntaxException {
        action.accept(context.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(context, "id"));
        return 1;
    }

    private static int add(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        long price = Currency.parse(StringArgumentType.getString(context, "price"));
        if (price <= 0) {
            source.sendError(Text.literal("Set a price like 12.34 or a whole number of Pice."));
            return 0;
        }
        WholesaleManager.addOffer(source.getPlayerOrThrow(), IntegerArgumentType.getInteger(context, "lotSize"), price);
        return 1;
    }

    private static int order(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        WholesaleManager.orderOffer(
            context.getSource().getPlayerOrThrow(),
            IntegerArgumentType.getInteger(context, "offerId"),
            IntegerArgumentType.getInteger(context, "lots"));
        return 1;
    }

    private static int offers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Wholesale state = Wholesale.get(player.getEntityWorld().getServer());
        if (state.offers.isEmpty()) {
            player.sendMessage(Text.literal("No wholesale offers."), false);
            return 1;
        }
        for (WholesaleOffer offer : state.offers) {
            player.sendMessage(Text.literal("[" + offer.id() + "] " + offer.stack().getName().getString()
                + " x" + offer.lotSize() + " per lot at " + Currency.format(offer.lotPrice())
                + " by " + offer.sellerName()), false);
        }
        return 1;
    }

    private static int orders(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Wholesale state = Wholesale.get(player.getEntityWorld().getServer());
        boolean any = false;
        for (WholesaleOrder order : state.orders) {
            boolean incoming = order.seller.equals(player.getUuid());
            if (!incoming && !order.buyer.equals(player.getUuid())) {
                continue;
            }
            any = true;
            player.sendMessage(Text.literal("[" + order.id + "] " + (incoming ? "from " + order.buyerName : "to " + order.sellerName)
                + " " + order.items() + " x " + order.stack.getName().getString()
                + " for " + Currency.format(order.escrow)
                + (order.inTransit ? " (in transit, " + order.remaining + " left)" : " (pending)")), false);
        }
        if (!any) {
            player.sendMessage(Text.literal("No wholesale orders."), false);
        }
        return 1;
    }
}
