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
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

// The /deal text fallback for wholesale offers and orders.
public final class DealCommand {

    private DealCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deal")
            .then(Commands.literal("offers")
                .executes(DealCommand::offers))
            .then(Commands.literal("list")
                .executes(DealCommand::orders))
            .then(Commands.literal("add")
                .then(Commands.argument("lotSize", IntegerArgumentType.integer(1))
                    .then(Commands.argument("price", StringArgumentType.word())
                        .executes(DealCommand::add))))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::removeOffer))))
            .then(Commands.literal("order")
                .then(Commands.argument("offerId", IntegerArgumentType.integer(0))
                    .then(Commands.argument("lots", IntegerArgumentType.integer(1))
                        .executes(DealCommand::order))))
            .then(Commands.literal("accept")
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::accept))))
            .then(Commands.literal("decline")
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::decline))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> byId(context, WholesaleManager::cancel)))));
    }

    private static int byId(CommandContext<CommandSourceStack> context,
                            BiConsumer<ServerPlayer, Integer> action) throws CommandSyntaxException {
        action.accept(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "id"));
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        long price = Currency.parse(StringArgumentType.getString(context, "price"));
        if (price <= 0) {
            source.sendFailure(Component.literal("Set a price like 12.34 or a whole number of Pice."));
            return 0;
        }
        WholesaleManager.addOffer(source.getPlayerOrException(), IntegerArgumentType.getInteger(context, "lotSize"), price);
        return 1;
    }

    private static int order(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WholesaleManager.orderOffer(
            context.getSource().getPlayerOrException(),
            IntegerArgumentType.getInteger(context, "offerId"),
            IntegerArgumentType.getInteger(context, "lots"));
        return 1;
    }

    private static int offers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Wholesale state = Wholesale.get(player.level().getServer());
        if (state.offers.isEmpty()) {
            player.sendSystemMessage(Component.literal("No wholesale offers."));
            return 1;
        }
        for (WholesaleOffer offer : state.offers) {
            player.sendSystemMessage(Component.literal("[" + offer.id() + "] " + offer.stack().getHoverName().getString()
                + " x" + offer.lotSize() + " per lot at " + Currency.format(offer.lotPrice())
                + " by " + offer.sellerName()));
        }
        return 1;
    }

    private static int orders(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Wholesale state = Wholesale.get(player.level().getServer());
        boolean any = false;
        for (WholesaleOrder order : state.orders) {
            boolean incoming = order.seller.equals(player.getUUID());
            if (!incoming && !order.buyer.equals(player.getUUID())) {
                continue;
            }
            any = true;
            player.sendSystemMessage(Component.literal("[" + order.id + "] " + (incoming ? "from " + order.buyerName : "to " + order.sellerName)
                + " " + order.items() + " x " + order.stack.getHoverName().getString()
                + " for " + Currency.format(order.escrow)
                + (order.inTransit ? " (in transit, " + order.remaining + " left)" : " (pending)")));
        }
        if (!any) {
            player.sendSystemMessage(Component.literal("No wholesale orders."));
        }
        return 1;
    }
}
