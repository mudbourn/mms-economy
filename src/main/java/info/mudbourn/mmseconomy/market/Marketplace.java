package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.DebugAccess;
import info.mudbourn.mmseconomy.economy.Tax;
import info.mudbourn.mmseconomy.economy.TaxKind;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

// Server-authoritative shop and market operations, all validated live.
public final class Marketplace {

    private Marketplace() {
    }

    // Blocks the player may be from the barrel wall they look at to claim it as a shop.
    private static final double SHOP_REACH = 5.0;

    // Lists the held item at a unit price, charging the one-time setup fee on the first claim of the targeted depot.
    public static void list(ServerPlayerEntity player, long price) {
        if (!MmsEconomy.config().marketEnabled) {
            player.sendMessage(Text.literal("The market is disabled."), false);
            return;
        }
        if (price <= 0) {
            player.sendMessage(Text.literal("Set a positive price."), false);
            return;
        }

        ServerWorld world = player.getEntityWorld();
        Market market = Market.get(world.getServer());
        BlockPos depot = Depot.lookedAtDepot(player, SHOP_REACH);
        if (depot == null) {
            if (!DebugAccess.has(player)) {
                player.sendMessage(Text.literal(
                    "Stand in front of and look directly at the 2x2 barrel wall you want as your shop."), false);
                return;
            }
            depot = player.getBlockPos();
        }

        if (market.depotClaimedByOther(depot, player.getUuid())) {
            player.sendMessage(Text.literal("That depot belongs to someone else."), false);
            return;
        }

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("Hold the item you want to list."), false);
            return;
        }

        if (!market.ownsAnyAt(depot, player.getUuid())) {
            long fee = MmsEconomy.config().shopSetupFeePice;
            if (!Wallet.withdraw(player, fee)) {
                player.sendMessage(Text.literal("You need " + Currency.format(fee)
                    + " to open a shop here."), false);
                return;
            }
            Treasury.get(world.getServer()).credit(fee);
        }

        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        market.add(new ShopListing(
            player.getUuid(),
            player.getName().getString(),
            depot,
            world.getRegistryKey().getValue().toString(),
            itemId,
            price));
        player.sendMessage(Text.literal("Listed " + held.getItem().getName().getString()
            + " at " + Currency.format(price) + " each."), false);
    }

    public static void unlist(ServerPlayerEntity player, int index) {
        Market market = Market.get(player.getEntityWorld().getServer());
        List<ShopListing> all = market.all();
        if (index < 0 || index >= all.size() || !all.get(index).owner().equals(player.getUuid())) {
            player.sendMessage(Text.literal("That is not your listing."), false);
            return;
        }
        market.removeAt(index);
        player.sendMessage(Text.literal("Listing removed."), false);
    }

    public static void browse(ServerPlayerEntity player) {
        Market market = Market.get(player.getEntityWorld().getServer());
        List<ShopListing> all = market.all();
        if (all.isEmpty()) {
            player.sendMessage(Text.literal("No listings yet."), false);
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            ShopListing listing = all.get(i);
            Item item = itemOf(listing);
            String name = item != null ? item.getName().getString() : listing.item();
            player.sendMessage(Text.literal("[" + i + "] " + name + " " + Currency.format(listing.price())
                + " by " + listing.ownerName() + " at " + listing.depot().toShortString()), false);
        }
    }

    public static void buy(ServerPlayerEntity buyer, int index) {
        ServerWorld world = buyer.getEntityWorld();
        MinecraftServer server = world.getServer();
        Market market = Market.get(server);
        List<ShopListing> all = market.all();
        if (index < 0 || index >= all.size()) {
            buyer.sendMessage(Text.literal("No such listing."), false);
            return;
        }

        ShopListing listing = all.get(index);
        boolean debug = DebugAccess.has(buyer);
        if (!debug && !canReach(buyer, listing, server)) {
            buyer.sendMessage(Text.literal("Get within " + MmsEconomy.config().shopOwnerRange
                + " blocks of the owner or " + MmsEconomy.config().shopMarketRange
                + " blocks of the depot at " + listing.depot().toShortString() + " to buy this."), false);
            return;
        }

        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(listing.owner());

        Item item = itemOf(listing);
        List<Inventory> barrels = Depot.barrels(world, listing.depot());
        if (item == null || barrels.isEmpty() || !hasStock(barrels, item)) {
            buyer.sendMessage(Text.literal("Out of stock."), false);
            return;
        }
        if (!Wallet.canAfford(buyer, listing.price())) {
            buyer.sendMessage(Text.literal("You cannot afford that."), false);
            return;
        }

        Tax.Settlement settlement = owner != null
            ? Tax.settleTaxed(buyer, owner, listing.price(), TaxKind.PURCHASE)
            : Tax.settleTaxedOffline(buyer, listing.price(), TaxKind.PURCHASE);
        if (settlement == null) {
            buyer.sendMessage(Text.literal("Purchase failed."), false);
            return;
        }

        takeOne(barrels, item);
        deliver(buyer, new ItemStack(item, 1));

        long day = History.currentDay(buyer);
        String name = item.getName().getString();
        History.log(buyer, new HistoryEntry(day, HistoryCategory.PURCHASE, name, 1,
            settlement.gross(), settlement.tax(), -settlement.gross(), listing.ownerName()));
        HistoryEntry ownerEntry = new HistoryEntry(day, HistoryCategory.PURCHASE, name, 1,
            settlement.gross(), settlement.tax(), settlement.net(), buyer.getName().getString());
        if (owner != null) {
            History.log(owner, ownerEntry);
        } else {
            info.mudbourn.mmseconomy.economy.PendingPayouts.get(server)
                .record(listing.owner(), settlement.net(), ownerEntry);
        }

        buyer.sendMessage(Text.literal("Bought " + name + " for " + Currency.format(settlement.gross())
            + " (tax " + Currency.format(settlement.tax()) + ")."), false);
    }

    // A buyer may reach a listing within shopOwnerRange of its online owner, or within shopMarketRange
    // of its depot with the depot up to shopVerticalRange above or below, so it can be hidden underground.
    public static boolean canReach(ServerPlayerEntity buyer, ShopListing listing, MinecraftServer server) {
        if (!listing.dimension().equals(buyer.getEntityWorld().getRegistryKey().getValue().toString())) {
            return false;
        }
        if (Depot.withinReach(buyer, listing.depot(),
            MmsEconomy.config().shopMarketRange, MmsEconomy.config().shopVerticalRange)) {
            return true;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(listing.owner());
        if (owner == null || owner == buyer || owner.getEntityWorld() != buyer.getEntityWorld()) {
            return false;
        }
        int ownerRange = MmsEconomy.config().shopOwnerRange;
        double distance = buyer.squaredDistanceTo(owner.getX(), owner.getY(), owner.getZ());
        return distance <= (double) ownerRange * ownerRange;
    }

    // A shop owner reaches their own depot within shopMarketRange horizontally and shopVerticalRange up or down.
    public static boolean ownsReachableDepot(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        String dimension = world.getRegistryKey().getValue().toString();
        int horizontal = MmsEconomy.config().shopMarketRange;
        int vertical = MmsEconomy.config().shopVerticalRange;
        return Market.get(world.getServer()).all().stream().anyMatch(listing ->
            listing.owner().equals(player.getUuid())
                && listing.dimension().equals(dimension)
                && Depot.withinReach(player, listing.depot(), horizontal, vertical));
    }

    // True when the player is at their own reachable shop or standing at a depot that is theirs or unclaimed.
    private static boolean atOwnDepot(ServerPlayerEntity player) {
        if (ownsReachableDepot(player)) {
            return true;
        }
        BlockPos depot = Depot.detectNear(player, MmsEconomy.config().depotRange);
        if (depot == null) {
            return false;
        }
        return !Market.get(player.getEntityWorld().getServer()).depotClaimedByOther(depot, player.getUuid());
    }

    // Sells the held item to the server at buy-list prices, minting the payout, and requires standing at the player's own shop depot.
    public static void serverSell(ServerPlayerEntity player, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendMessage(Text.literal("Stand at your own shop depot to sell to the server."), false);
            return;
        }

        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("Hold the item you want to sell."), false);
            return;
        }

        String itemId = Registries.ITEM.getId(held.getItem()).toString();
        long unit = ServerBuyList.priceOf(itemId);
        if (unit <= 0) {
            player.sendMessage(Text.literal("The server does not buy that."), false);
            return;
        }

        int sold = Math.min(count, held.getCount());
        if (sold <= 0) {
            return;
        }
        held.decrement(sold);
        info.mudbourn.mmseconomy.economy.ServerLibrary.get(player.getEntityWorld().getServer())
            .store(itemId, sold);
        Wallet.deposit(player, unit * sold);
        player.sendMessage(Text.literal("Sold " + sold + " for " + Currency.format(unit * sold) + "."), false);
    }

    // Buys count of a catalog item from the server, drawing from library stock at 1.5x and minting the rest at 3x.
    public static void serverBuy(ServerPlayerEntity player, String itemId, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendMessage(Text.literal("Stand at your own shop depot to trade with the server."), false);
            return;
        }
        if (count <= 0 || !ServerBuyList.sells(itemId)) {
            player.sendMessage(Text.literal("The server does not stock that."), false);
            return;
        }
        Item item = itemOf(itemId);
        long sell = ServerBuyList.priceOf(itemId);
        if (item == null || sell <= 0) {
            player.sendMessage(Text.literal("The server does not stock that."), false);
            return;
        }

        MinecraftServer server = player.getEntityWorld().getServer();
        info.mudbourn.mmseconomy.economy.ServerLibrary library =
            info.mudbourn.mmseconomy.economy.ServerLibrary.get(server);
        long budget = Wallet.balance(player);
        long stockPrice = ServerBuyList.stockBuyPrice(sell);
        long mintPrice = ServerBuyList.mintBuyPrice(sell);
        long fromStock = Math.min(Math.min(count, library.count(itemId)), budget / stockPrice);
        budget -= fromStock * stockPrice;
        long minted = Math.min(count - fromStock, budget / mintPrice);
        int bought = (int) (fromStock + minted);
        long spent = fromStock * stockPrice + minted * mintPrice;
        if (bought == 0 || !Wallet.withdraw(player, spent)) {
            player.sendMessage(Text.literal("You cannot afford that."), false);
            return;
        }
        library.take(itemId, fromStock);
        Treasury.get(server).credit(spent);
        deliverMany(player, item, bought);

        String name = item.getName().getString();
        long day = History.currentDay(player);
        History.log(player, new HistoryEntry(day, HistoryCategory.PURCHASE, name, bought,
            spent, 0L, -spent, "Server"));
        player.sendMessage(Text.literal("Bought " + bought + " " + name
            + " for " + Currency.format(spent) + "."), false);
    }

    // Sells count of a specific item from the player's inventory to the server at the buy-list price.
    public static void serverSellItem(ServerPlayerEntity player, String itemId, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendMessage(Text.literal("Stand at your own shop depot to sell to the server."), false);
            return;
        }
        long unit = ServerBuyList.priceOf(itemId);
        if (unit <= 0) {
            player.sendMessage(Text.literal("The server does not buy that."), false);
            return;
        }
        Item item = itemOf(itemId);
        if (item == null) {
            return;
        }

        int removed = removeItems(player, item, count);
        if (removed <= 0) {
            player.sendMessage(Text.literal("You have none of that to sell."), false);
            return;
        }
        info.mudbourn.mmseconomy.economy.ServerLibrary.get(player.getEntityWorld().getServer())
            .store(itemId, removed);
        Wallet.deposit(player, unit * removed);
        player.sendMessage(Text.literal("Sold " + removed + " " + item.getName().getString()
            + " for " + Currency.format(unit * removed) + "."), false);
    }

    // Removes up to count of an item across the player's inventory, returning how many were taken.
    private static int removeItems(ServerPlayerEntity player, Item item, int count) {
        int remaining = count;
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() == item && !stack.isEmpty()) {
                int take = Math.min(remaining, stack.getCount());
                stack.decrement(take);
                remaining -= take;
            }
        }
        inventory.markDirty();
        return count - remaining;
    }

    // The owner name of a claimed depot this barrel belongs to when the player may not open it, else null.
    public static String protectedOwner(ServerPlayerEntity player, BlockPos pos) {
        if (player.isCreativeLevelTwoOp()) {
            return null;
        }
        ServerWorld world = player.getEntityWorld();
        String dimension = world.getRegistryKey().getValue().toString();
        for (ShopListing listing : Market.get(world.getServer()).all()) {
            if (!listing.dimension().equals(dimension)) {
                continue;
            }
            if (Depot.contains(world, listing.depot(), pos)) {
                return listing.owner().equals(player.getUuid()) ? null : listing.ownerName();
            }
        }
        return null;
    }

    // Removes the breaker's own listings whose depot includes the barrel at pos, returning how many were closed.
    public static int removeShopAtBrokenBarrel(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getEntityWorld();
        String dimension = world.getRegistryKey().getValue().toString();
        return Market.get(world.getServer()).removeIf(listing ->
            listing.owner().equals(player.getUuid())
                && listing.dimension().equals(dimension)
                && Depot.contains(world, listing.depot(), pos));
    }

    public static Item itemOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id != null ? Registries.ITEM.get(id) : null;
    }

    public static String displayName(String itemId) {
        Item item = itemOf(itemId);
        return item != null ? item.getName().getString() : itemId;
    }

    // The live stock of an item across a depot's barrels, or 0 when the depot is gone.
    public static int stock(List<Inventory> barrels, String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (Inventory barrel : barrels) {
            for (int slot = 0; slot < barrel.size(); slot++) {
                ItemStack stack = barrel.getStack(slot);
                if (stack.getItem() == item) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static Item itemOf(ShopListing listing) {
        return itemOf(listing.item());
    }

    private static boolean hasStock(List<Inventory> barrels, Item item) {
        for (Inventory barrel : barrels) {
            for (int slot = 0; slot < barrel.size(); slot++) {
                if (barrel.getStack(slot).getItem() == item && !barrel.getStack(slot).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void takeOne(List<Inventory> barrels, Item item) {
        for (Inventory barrel : barrels) {
            for (int slot = 0; slot < barrel.size(); slot++) {
                ItemStack stack = barrel.getStack(slot);
                if (stack.getItem() == item && !stack.isEmpty()) {
                    stack.decrement(1);
                    barrel.markDirty();
                    return;
                }
            }
        }
    }

    // Gives count of an item in full stacks, dropping whatever does not fit.
    private static void deliverMany(ServerPlayerEntity player, Item item, int count) {
        int maxStack = Math.max(1, item.getMaxCount());
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            deliver(player, new ItemStack(item, size));
            remaining -= size;
        }
    }

    private static void deliver(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
