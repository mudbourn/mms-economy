package info.mudbourn.mmseconomy.deal;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Billing;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.DebugAccess;
import info.mudbourn.mmseconomy.economy.PendingPayouts;
import info.mudbourn.mmseconomy.economy.Tax;
import info.mudbourn.mmseconomy.economy.TaxKind;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.market.Depot;
import info.mudbourn.mmseconomy.market.Market;
import info.mudbourn.mmseconomy.market.Marketplace;
import info.mudbourn.mmseconomy.market.ShopListing;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Server-authoritative wholesale trade between depot owners: offers, escrowed orders, acceptance and daily delivery.
public final class WholesaleManager {

    public static final int MAX_LOT_SIZE = 9999;

    public static final int MAX_LOTS = 9999;

    private WholesaleManager() {
    }

    // The depot the player trades from: their nearest owned depot in reach, or any owned depot for a debug player.
    public static Spot ownDepot(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos near = Marketplace.reachableOwnDepot(player);
        if (near != null) {
            return new Spot(near, world.getRegistryKey().getValue().toString());
        }
        if (!DebugAccess.has(player)) {
            return null;
        }
        for (ShopListing listing : Market.get(world.getServer()).all()) {
            if (listing.owner().equals(player.getUuid())) {
                return new Spot(listing.depot(), listing.dimension());
            }
        }
        return null;
    }

    // True when the player may trade wholesale right now.
    public static boolean canTrade(ServerPlayerEntity player) {
        return MmsEconomy.config().dealEnabled && ownDepot(player) != null;
    }

    private static Spot trader(ServerPlayerEntity player) {
        if (!MmsEconomy.config().dealEnabled) {
            player.sendMessage(Text.literal("Wholesale deals are disabled."), false);
            return null;
        }
        Spot spot = ownDepot(player);
        if (spot == null) {
            player.sendMessage(Text.literal("Stand at a depot you own to trade wholesale."), false);
            return null;
        }
        retarget(player, spot);
        return spot;
    }

    // Points the player's orders whose receiving depot is gone at the depot they stand at now.
    public static void retarget(ServerPlayerEntity player, Spot spot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        Wholesale state = Wholesale.get(server);
        for (WholesaleOrder order : state.orders) {
            if (order.buyer.equals(player.getUuid()) && !live(server, order.buyer, order.buyerDepot)) {
                order.buyerDepot = spot;
                state.markDirty();
            }
        }
    }

    // Retargets the player's orders from the depot they currently trade at, when there is one.
    public static void retarget(ServerPlayerEntity player) {
        Spot spot = ownDepot(player);
        if (spot != null && MmsEconomy.config().dealEnabled) {
            retarget(player, spot);
        }
    }

    // True when the seller still owns a shop at the offer's depot.
    public static boolean live(MinecraftServer server, UUID seller, Spot depot) {
        return Market.get(server).ownsAnyAt(depot.pos(), seller);
    }

    public static void addOffer(ServerPlayerEntity player, int lotSize, long lotPrice) {
        Spot spot = trader(player);
        if (spot == null) {
            return;
        }
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("Hold the item you want to offer."), false);
            return;
        }
        if (lotSize < 1 || lotSize > MAX_LOT_SIZE || lotPrice <= 0) {
            player.sendMessage(Text.literal("Set a lot size from 1 to " + MAX_LOT_SIZE
                + " and a positive price."), false);
            return;
        }
        Wholesale state = Wholesale.get(player.getEntityWorld().getServer());
        state.offers.add(new WholesaleOffer(
            state.allocateId(),
            player.getUuid(),
            player.getName().getString(),
            spot,
            held.copyWithCount(1),
            lotSize,
            lotPrice));
        player.sendMessage(Text.literal("Offered " + lotSize + " x " + held.getName().getString()
            + " per lot at " + Currency.format(lotPrice) + "."), false);
    }

    public static void removeOffer(ServerPlayerEntity player, int offerId) {
        if (trader(player) == null) {
            return;
        }
        MinecraftServer server = player.getEntityWorld().getServer();
        Wholesale state = Wholesale.get(server);
        WholesaleOffer offer = state.offer(offerId);
        if (offer == null || !offer.seller().equals(player.getUuid())) {
            player.sendMessage(Text.literal("That is not your offer."), false);
            return;
        }
        state.offers.remove(offer);
        state.markDirty();
        for (WholesaleOrder order : new ArrayList<>(state.orders)) {
            if (order.offerId == offerId && !order.inTransit) {
                refund(server, state, order, "withdrawn by the seller");
            }
        }
        player.sendMessage(Text.literal("Offer removed."), false);
    }

    // Orders lots of a wholesale offer, escrowing the full price from the buyer's wallet.
    public static void orderOffer(ServerPlayerEntity buyer, int offerId, int lots) {
        Spot buyerDepot = trader(buyer);
        if (buyerDepot == null) {
            return;
        }
        MinecraftServer server = buyer.getEntityWorld().getServer();
        WholesaleOffer offer = Wholesale.get(server).offer(offerId);
        if (offer == null || !live(server, offer.seller(), offer.depot())) {
            buyer.sendMessage(Text.literal("That offer is gone."), false);
            return;
        }
        if (lots < 1 || lots > MAX_LOTS || offer.lotPrice() > Long.MAX_VALUE / lots) {
            buyer.sendMessage(Text.literal("Order between 1 and " + MAX_LOTS + " lots."), false);
            return;
        }
        place(buyer, buyerDepot, offer.seller(), offer.sellerName(), offer.depot(), offer.stack(),
            offer.lotSize(), offer.lotPrice(), lots, offer.lotPrice() * lots, offer.id());
    }

    // Requests quantity of a regular shop listing's item for an offered total, as a one-item-lot order.
    public static void requestListing(ServerPlayerEntity buyer, UUID seller, Spot depot, ItemStack item,
                                      int quantity, long total) {
        Spot buyerDepot = trader(buyer);
        if (buyerDepot == null) {
            return;
        }
        MinecraftServer server = buyer.getEntityWorld().getServer();
        ShopListing listing = null;
        for (ShopListing candidate : Market.get(server).all()) {
            if (candidate.owner().equals(seller)
                && candidate.depot().equals(depot.pos())
                && candidate.dimension().equals(depot.dimension())
                && candidate.matches(item)) {
                listing = candidate;
                break;
            }
        }
        ItemStack template = listing == null ? ItemStack.EMPTY : listing.template();
        if (template.isEmpty()) {
            buyer.sendMessage(Text.literal("That listing is gone."), false);
            return;
        }
        if (quantity < 1 || quantity > MAX_LOT_SIZE * MAX_LOTS || total <= 0) {
            buyer.sendMessage(Text.literal("Set a quantity and a positive total price."), false);
            return;
        }
        place(buyer, buyerDepot, listing.owner(), listing.ownerName(),
            new Spot(listing.depot(), listing.dimension()), template, 1, Math.max(1L, total / quantity),
            quantity, total, 0);
    }

    private static void place(ServerPlayerEntity buyer, Spot buyerDepot, UUID seller, String sellerName,
                              Spot sellerDepot, ItemStack stack, int lotSize, long lotPrice, int lots,
                              long total, int offerId) {
        if (seller.equals(buyer.getUuid())) {
            buyer.sendMessage(Text.literal("You cannot order from yourself."), false);
            return;
        }
        if (!Wallet.withdraw(buyer, total)) {
            buyer.sendMessage(Text.literal("You need " + Currency.format(total) + " in your wallet."), false);
            return;
        }
        MinecraftServer server = buyer.getEntityWorld().getServer();
        Wholesale state = Wholesale.get(server);
        WholesaleOrder order = new WholesaleOrder(
            state.allocateId(),
            seller,
            sellerName,
            sellerDepot,
            stack.copyWithCount(1),
            lotSize,
            lotPrice,
            buyer.getUuid(),
            buyer.getName().getString(),
            buyerDepot,
            lots,
            total,
            false,
            Billing.today(server),
            0,
            offerId);
        state.orders.add(order);
        state.markDirty();

        String name = stack.getName().getString();
        String buyerName = order.buyerName;
        long day = order.created;
        History.log(buyer, new HistoryEntry(day, HistoryCategory.DEAL, name, order.items(),
            total, 0L, -total, sellerName + " (ordered, escrowed)"));
        note(server, seller, new HistoryEntry(day, HistoryCategory.DEAL, name, order.items(),
            total, 0L, 0L, buyerName + " (order received)"));
        tell(server, seller, buyerName + " ordered " + order.items() + " x " + name + " for "
            + Currency.format(total) + ". Accept or decline in the Deals tab.");
        buyer.sendMessage(Text.literal("Ordered " + order.items() + " x " + name + " for "
            + Currency.format(total) + ". The money is held until " + sellerName + " answers."), false);
    }

    // The seller accepts: the goods leave their depot at once and travel until the next day.
    public static void accept(ServerPlayerEntity player, int orderId) {
        if (trader(player) == null) {
            return;
        }
        MinecraftServer server = player.getEntityWorld().getServer();
        WholesaleOrder order = Wholesale.get(server).order(orderId);
        if (order == null || order.inTransit || !order.seller.equals(player.getUuid())) {
            player.sendMessage(Text.literal("No such order to accept."), false);
            return;
        }
        if (!live(server, order.seller, order.sellerDepot)) {
            player.sendMessage(Text.literal("You no longer own the depot this order draws from. Decline it."), false);
            return;
        }
        if (!DebugAccess.has(player) && !order.sellerDepot.equals(ownDepot(player))) {
            player.sendMessage(Text.literal("Stand at the depot this order draws from to accept it."), false);
            return;
        }
        List<Inventory> barrels = barrels(server, order.sellerDepot);
        int items = order.items();
        if (barrels.isEmpty() || Depot.count(barrels, order.stack) < items) {
            player.sendMessage(Text.literal("Your depot does not hold " + items + " x "
                + order.stack.getName().getString() + ". Stock it or decline."), false);
            return;
        }
        Depot.take(barrels, order.stack, items);
        order.inTransit = true;
        order.remaining = items;
        Wholesale.get(server).markDirty();

        long day = Billing.today(server);
        String name = order.stack.getName().getString();
        note(server, order.seller, new HistoryEntry(day, HistoryCategory.DEAL, name, items,
            order.escrow, 0L, 0L, order.buyerName + " (accepted)"));
        note(server, order.buyer, new HistoryEntry(day, HistoryCategory.DEAL, name, items,
            order.escrow, 0L, 0L, order.sellerName + " (accepted)"));
        tell(server, order.buyer, order.sellerName + " accepted your order of " + items + " x " + name
            + ". It arrives at your depot at the next day.");
        player.sendMessage(Text.literal("Accepted. The goods are in transit and you are paid on delivery."), false);
    }

    public static void decline(ServerPlayerEntity player, int orderId) {
        if (trader(player) == null) {
            return;
        }
        MinecraftServer server = player.getEntityWorld().getServer();
        Wholesale state = Wholesale.get(server);
        WholesaleOrder order = state.order(orderId);
        if (order == null || order.inTransit || !order.seller.equals(player.getUuid())) {
            player.sendMessage(Text.literal("No such order to decline."), false);
            return;
        }
        refund(server, state, order, "declined");
        player.sendMessage(Text.literal("Order declined and the buyer refunded."), false);
    }

    public static void cancel(ServerPlayerEntity player, int orderId) {
        if (trader(player) == null) {
            return;
        }
        MinecraftServer server = player.getEntityWorld().getServer();
        Wholesale state = Wholesale.get(server);
        WholesaleOrder order = state.order(orderId);
        if (order == null || order.inTransit || !order.buyer.equals(player.getUuid())) {
            player.sendMessage(Text.literal("No such order to cancel."), false);
            return;
        }
        refund(server, state, order, "cancelled");
        tell(server, order.seller, order.buyerName + " cancelled an order for "
            + order.items() + " x " + order.stack.getName().getString() + ".");
        player.sendMessage(Text.literal("Order cancelled and refunded."), false);
    }

    private static void refund(MinecraftServer server, Wholesale state, WholesaleOrder order, String reason) {
        state.orders.remove(order);
        state.markDirty();
        pay(server, order.buyer, order.escrow, new HistoryEntry(Billing.today(server), HistoryCategory.DEAL,
            order.stack.getName().getString(), order.items(), order.escrow, 0L, order.escrow,
            order.sellerName + " (" + reason + ", refunded)"));
        tell(server, order.buyer, "Your order of " + order.items() + " x "
            + order.stack.getName().getString() + " was " + reason + ". " + Currency.format(order.escrow)
            + " refunded.");
    }

    // Runs once per new in-game day: expires stale pending orders and delivers goods in transit.
    public static void tick(MinecraftServer server) {
        if (server.getOverworld() == null) {
            return;
        }
        long day = Billing.today(server);
        Wholesale state = Wholesale.get(server);
        for (Wholesale.Lost lost : Wholesale.drainLost()) {
            pay(server, lost.buyer(), lost.escrow(), new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
                lost.escrow(), 0L, lost.escrow(), "Wholesale (order lost, refunded)"));
        }
        if (state.lastDay < 0L) {
            state.lastDay = day;
            state.markDirty();
            return;
        }
        if (day <= state.lastDay) {
            return;
        }
        state.lastDay = day;
        state.markDirty();

        for (WholesaleOrder order : new ArrayList<>(state.orders)) {
            if (order.inTransit) {
                deliver(server, state, order, day);
            } else if (day - order.created >= MmsEconomy.config().wholesaleOrderDays) {
                refund(server, state, order, "expired");
            }
        }
    }

    // Moves what fits into the buyer's depot and pays the seller for it, keeping the rest in transit.
    private static void deliver(MinecraftServer server, Wholesale state, WholesaleOrder order, long day) {
        String name = order.stack.getName().getString();
        if (!live(server, order.buyer, order.buyerDepot)) {
            tell(server, order.buyer, "Your delivery of " + order.remaining + " x " + name
                + " is waiting: you no longer own its depot. Open the menu at a depot you own.");
            return;
        }
        List<Inventory> barrels = barrels(server, order.buyerDepot);
        int inserted = barrels.isEmpty() ? 0 : Depot.insert(barrels, order.stack, order.remaining);
        if (inserted <= 0) {
            tell(server, order.buyer, "Your delivery of " + order.remaining + " x " + name
                + " is waiting: your depot is full or gone.");
            return;
        }

        boolean done = inserted >= order.remaining;
        long portion = done
            ? order.escrow
            : BigInteger.valueOf(order.escrow)
                .multiply(BigInteger.valueOf(inserted))
                .divide(BigInteger.valueOf(order.remaining))
                .longValue();
        order.escrow -= portion;
        order.remaining -= inserted;
        long tax = Tax.on(portion, TaxKind.DEAL);
        if (tax > 0) {
            Treasury.get(server).credit(tax);
        }
        pay(server, order.seller, portion - tax, new HistoryEntry(day, HistoryCategory.DEAL, name, inserted,
            portion, tax, portion - tax, order.buyerName + " (delivered)"));
        note(server, order.buyer, new HistoryEntry(day, HistoryCategory.DEAL, name, inserted,
            0L, 0L, 0L, order.sellerName + " (delivered)"));

        if (done) {
            state.orders.remove(order);
            tell(server, order.buyer, "Delivered " + inserted + " x " + name + " from " + order.sellerName + ".");
        } else {
            tell(server, order.buyer, "Delivered " + inserted + " x " + name + "; " + order.remaining
                + " wait for room in your depot.");
        }
        tell(server, order.seller, "Delivery to " + order.buyerName + " paid " + Currency.format(portion - tax)
            + " for " + inserted + " x " + name + ".");
        state.markDirty();
    }

    private static List<Inventory> barrels(MinecraftServer server, Spot spot) {
        Identifier id = Identifier.tryParse(spot.dimension());
        if (id == null) {
            return List.of();
        }
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        return world == null ? List.of() : Depot.barrels(world, spot.pos());
    }

    // Credits a player's wallet and ledger, or escrows both until they next join.
    private static void pay(MinecraftServer server, UUID who, long amount, HistoryEntry entry) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(who);
        if (online != null) {
            Wallet.deposit(online, amount);
            History.log(online, entry);
        } else {
            PendingPayouts.get(server).record(who, amount, entry);
        }
    }

    // Writes a money-free ledger row for a player.
    private static void note(MinecraftServer server, UUID who, HistoryEntry entry) {
        pay(server, who, 0L, entry);
    }

    private static void tell(MinecraftServer server, UUID who, String message) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(who);
        if (online != null) {
            online.sendMessage(Text.literal(message), false);
        }
    }
}
