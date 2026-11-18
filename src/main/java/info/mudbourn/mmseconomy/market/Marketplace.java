package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.DebugAccess;
import info.mudbourn.mmseconomy.economy.Gems;
import info.mudbourn.mmseconomy.economy.Tax;
import info.mudbourn.mmseconomy.economy.TaxKind;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

// Server-authoritative shop and market operations, all validated live.
public final class Marketplace {

    private Marketplace() {
    }

    // Blocks the player may be from the barrel wall they look at to claim it as a shop.
    private static final double SHOP_REACH = 5.0;

    // Longest shop name an owner may set.
    public static final int MAX_SHOP_NAME = 32;

    // Lists the held item at a unit price at the chosen own shop, or else the looked-at depot, charging the setup fee on a first claim.
    public static void list(ServerPlayer player, long price, BlockPos chosen) {
        if (!MmsEconomy.config().marketEnabled) {
            player.sendSystemMessage(Component.literal("The market is disabled."));
            return;
        }
        if (price <= 0) {
            player.sendSystemMessage(Component.literal("Set a positive price."));
            return;
        }

        ServerLevel world = player.level();
        Market market = Market.get(world.getServer());
        BlockPos depot = ownedReachableDepot(player, market, chosen)
            ? chosen
            : Depot.lookedAtDepot(player, SHOP_REACH);
        if (depot == null) {
            if (!DebugAccess.has(player)) {
                player.sendSystemMessage(Component.literal(
                    "Stand in front of and look directly at the 2x2 barrel wall you want as your shop."));
                return;
            }
            depot = player.blockPosition();
        }

        if (market.depotClaimedByOther(depot, player.getUUID())) {
            player.sendSystemMessage(Component.literal("That depot belongs to someone else."));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item you want to list."));
            return;
        }

        if (!market.ownsAnyAt(depot, player.getUUID())) {
            long fee = MmsEconomy.config().shopSetupFeePice;
            if (!Wallet.withdraw(player, fee)) {
                player.sendSystemMessage(Component.literal("You need " + Currency.format(fee)
                    + " to open a shop here."));
                return;
            }
            Treasury.get(world.getServer()).credit(fee);
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        market.add(new ShopListing(
            player.getUUID(),
            player.getName().getString(),
            depot,
            world.dimension().identifier().toString(),
            itemId,
            price,
            held.copyWithCount(1)));
        player.sendSystemMessage(Component.literal("Listed " + held.getHoverName().getString()
            + " at " + Currency.format(price) + " each."));
    }

    // True when the chosen depot already holds one of the player's listings in this dimension and is within shop reach.
    private static boolean ownedReachableDepot(ServerPlayer player, Market market, BlockPos chosen) {
        if (chosen == null || !market.ownsAnyAt(chosen, player.getUUID())) {
            return false;
        }
        ServerLevel world = player.level();
        String dimension = world.dimension().identifier().toString();
        boolean sameDimension = market.all().stream().anyMatch(listing ->
            listing.depot().equals(chosen)
                && listing.owner().equals(player.getUUID())
                && listing.dimension().equals(dimension));
        return sameDimension
            && Depot.isValid(world, chosen)
            && (DebugAccess.has(player) || Depot.withinReach(player, chosen,
                MmsEconomy.config().shopMarketRange, MmsEconomy.config().shopVerticalRange));
    }

    // Names one of the player's shops; a blank name restores the default.
    public static void rename(ServerPlayer player, BlockPos depot, String name) {
        Market market = Market.get(player.level().getServer());
        String trimmed = name.strip();
        if (trimmed.length() > MAX_SHOP_NAME) {
            trimmed = trimmed.substring(0, MAX_SHOP_NAME);
        }
        for (ShopListing listing : market.all()) {
            if (listing.depot().equals(depot) && listing.owner().equals(player.getUUID())) {
                market.renameShop(listing.dimension(), depot, trimmed);
                player.sendSystemMessage(Component.literal(trimmed.isEmpty()
                    ? "Shop name cleared."
                    : "Shop renamed to " + trimmed + "."));
                return;
            }
        }
        player.sendSystemMessage(Component.literal("That is not your shop."));
    }

    public static void unlist(ServerPlayer player, int index) {
        Market market = Market.get(player.level().getServer());
        List<ShopListing> all = market.all();
        if (index < 0 || index >= all.size() || !all.get(index).owner().equals(player.getUUID())) {
            player.sendSystemMessage(Component.literal("That is not your listing."));
            return;
        }
        market.removeAt(index);
        player.sendSystemMessage(Component.literal("Listing removed."));
    }

    public static void browse(ServerPlayer player) {
        Market market = Market.get(player.level().getServer());
        List<ShopListing> all = market.all();
        if (all.isEmpty()) {
            player.sendSystemMessage(Component.literal("No listings yet."));
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            ShopListing listing = all.get(i);
            String name = displayName(listing);
            player.sendSystemMessage(Component.literal("[" + i + "] " + name + " " + Currency.format(listing.price())
                + " by " + listing.ownerName() + " at " + listing.depot().toShortString()));
        }
    }

    public static void buy(ServerPlayer buyer, int index) {
        ServerLevel world = buyer.level();
        MinecraftServer server = world.getServer();
        Market market = Market.get(server);
        List<ShopListing> all = market.all();
        if (index < 0 || index >= all.size()) {
            buyer.sendSystemMessage(Component.literal("No such listing."));
            return;
        }

        ShopListing listing = all.get(index);
        boolean debug = DebugAccess.has(buyer);
        if (!debug && !canReach(buyer, listing, server)) {
            buyer.sendSystemMessage(Component.literal("Get within " + MmsEconomy.config().shopOwnerRange
                + " blocks of the owner or " + MmsEconomy.config().shopMarketRange
                + " blocks of the depot at " + listing.depot().toShortString() + " to buy this."));
            return;
        }

        ServerPlayer owner = server.getPlayerList().getPlayer(listing.owner());

        ItemStack template = listing.template();
        List<Container> barrels = Depot.barrels(world, listing.depot());
        if (template.isEmpty() || barrels.isEmpty() || stock(barrels, listing) == 0) {
            buyer.sendSystemMessage(Component.literal("Out of stock."));
            return;
        }
        long tendered = Gems.tender(buyer, listing.price());
        if (tendered < 0) {
            buyer.sendSystemMessage(Component.literal("Shops take cash only: carry "
                + Currency.format(listing.price()) + " in Colt and Pice gems."));
            return;
        }
        long change = tendered - listing.price();
        if (change > 0) {
            Gems.giveChange(buyer, change);
        }

        long tax = Tax.on(listing.price(), TaxKind.PURCHASE);
        Tax.Settlement settlement = new Tax.Settlement(listing.price(), tax, listing.price() - tax);
        if (tax > 0) {
            Treasury.get(server).credit(tax);
        }

        takeOne(barrels, listing);
        deliver(buyer, template.copy());
        long unbanked = Gems.value(template.getItem()) > 0
            ? settlement.net()
            : Gems.stockBarrels(barrels, settlement.net());
        if (owner != null && unbanked > 0) {
            Wallet.deposit(owner, unbanked);
        }

        long day = History.currentDay(buyer);
        String name = template.getHoverName().getString();
        History.log(buyer, new HistoryEntry(day, HistoryCategory.PURCHASE, name, 1,
            settlement.gross(), settlement.tax(), -settlement.gross(), listing.ownerName()));
        HistoryEntry ownerEntry = new HistoryEntry(day, HistoryCategory.PURCHASE, name, 1,
            settlement.gross(), settlement.tax(), settlement.net(), buyer.getName().getString());
        if (owner != null) {
            History.log(owner, ownerEntry);
        } else {
            info.mudbourn.mmseconomy.economy.PendingPayouts.get(server)
                .record(listing.owner(), unbanked, ownerEntry);
        }

        buyer.sendSystemMessage(Component.literal("Bought " + name + " for " + Currency.format(settlement.gross())
            + " (tax " + Currency.format(settlement.tax()) + ")"
            + (change > 0 ? ", change " + Currency.format(change) : "") + "."));
    }

    // A buyer may reach a listing within shopOwnerRange of its online owner, or within shopMarketRange
    // of its depot with the depot up to shopVerticalRange above or below, so it can be hidden underground.
    public static boolean canReach(ServerPlayer buyer, ShopListing listing, MinecraftServer server) {
        if (!listing.dimension().equals(buyer.level().dimension().identifier().toString())) {
            return false;
        }
        if (Depot.withinReach(buyer, listing.depot(),
            MmsEconomy.config().shopMarketRange, MmsEconomy.config().shopVerticalRange)) {
            return true;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(listing.owner());
        if (owner == null || owner == buyer || owner.level() != buyer.level()) {
            return false;
        }
        int ownerRange = MmsEconomy.config().shopOwnerRange;
        double distance = buyer.distanceToSqr(owner.getX(), owner.getY(), owner.getZ());
        return distance <= (double) ownerRange * ownerRange;
    }

    // The player's own listed depot nearest to them within shop reach in this dimension, or null.
    public static BlockPos reachableOwnDepot(ServerPlayer player) {
        ServerLevel world = player.level();
        String dimension = world.dimension().identifier().toString();
        int horizontal = MmsEconomy.config().shopMarketRange;
        int vertical = MmsEconomy.config().shopVerticalRange;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ShopListing listing : Market.get(world.getServer()).all()) {
            if (!listing.owner().equals(player.getUUID())
                || !listing.dimension().equals(dimension)
                || !Depot.withinReach(player, listing.depot(), horizontal, vertical)) {
                continue;
            }
            double distance = player.distanceToSqr(
                listing.depot().getX() + 0.5,
                listing.depot().getY() + 0.5,
                listing.depot().getZ() + 0.5);
            if (distance < bestDistance) {
                best = listing.depot();
                bestDistance = distance;
            }
        }
        return best;
    }

    // A shop owner reaches their own depot within shopMarketRange horizontally and shopVerticalRange up or down.
    public static boolean ownsReachableDepot(ServerPlayer player) {
        return reachableOwnDepot(player) != null;
    }

    // True when the player is at their own reachable shop or standing at a depot that is theirs or unclaimed.
    private static boolean atOwnDepot(ServerPlayer player) {
        if (ownsReachableDepot(player)) {
            return true;
        }
        BlockPos depot = Depot.detectNear(player, MmsEconomy.config().depotRange);
        if (depot == null) {
            return false;
        }
        return !Market.get(player.level().getServer()).depotClaimedByOther(depot, player.getUUID());
    }

    // Sells the held item to the server at buy-list prices, minting the payout, and requires standing at the player's own shop depot.
    public static void serverSell(ServerPlayer player, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendSystemMessage(Component.literal("Stand at your own shop depot to sell to the server."));
            return;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item you want to sell."));
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        long unit = ServerBuyList.priceOf(itemId);
        if (unit <= 0) {
            player.sendSystemMessage(Component.literal("The server does not buy that."));
            return;
        }

        int sold = Math.min(count, held.getCount());
        if (sold <= 0) {
            return;
        }
        held.shrink(sold);
        info.mudbourn.mmseconomy.economy.ServerLibrary library =
            info.mudbourn.mmseconomy.economy.ServerLibrary.get(player.level().getServer());
        long payout = ServerBuyList.sellTotal(itemId, library.count(itemId), sold);
        library.store(itemId, sold);
        Wallet.deposit(player, payout);
        player.sendSystemMessage(Component.literal("Sold " + sold + " for " + Currency.format(payout) + "."));
    }

    // Buys count of a catalog item from the server, drawing from library stock first and minting the rest.
    public static void serverBuy(ServerPlayer player, String itemId, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendSystemMessage(Component.literal("Stand at your own shop depot to trade with the server."));
            return;
        }
        if (count <= 0 || !ServerBuyList.sells(itemId)) {
            player.sendSystemMessage(Component.literal("The server does not stock that."));
            return;
        }
        Item item = itemOf(itemId);
        long sell = ServerBuyList.priceOf(itemId);
        if (item == null || sell <= 0) {
            player.sendSystemMessage(Component.literal("The server does not stock that."));
            return;
        }

        MinecraftServer server = player.level().getServer();
        info.mudbourn.mmseconomy.economy.ServerLibrary library =
            info.mudbourn.mmseconomy.economy.ServerLibrary.get(server);
        long budget = Wallet.balance(player);
        long stockPrice = ServerBuyList.stockBuyPrice(itemId, sell);
        long mintPrice = ServerBuyList.mintBuyPrice(itemId, sell);
        long fromStock = Math.min(Math.min(count, library.count(itemId)), budget / stockPrice);
        budget -= fromStock * stockPrice;
        long minted = Math.min(count - fromStock, budget / mintPrice);
        int bought = (int) (fromStock + minted);
        long spent = fromStock * stockPrice + minted * mintPrice;
        if (bought == 0 || !Wallet.withdraw(player, spent)) {
            player.sendSystemMessage(Component.literal("You cannot afford that."));
            return;
        }
        library.take(itemId, fromStock);
        Treasury.get(server).credit(spent);
        deliverMany(player, item, bought);

        String name = item.getName(item.getDefaultInstance()).getString();
        long day = History.currentDay(player);
        History.log(player, new HistoryEntry(day, HistoryCategory.PURCHASE, name, bought,
            spent, 0L, -spent, "Server"));
        player.sendSystemMessage(Component.literal("Bought " + bought + " " + name
            + " for " + Currency.format(spent) + "."));
    }

    // Sells count of a specific item from the player's inventory to the server at the buy-list price.
    public static void serverSellItem(ServerPlayer player, String itemId, int count) {
        if (!DebugAccess.has(player) && !atOwnDepot(player)) {
            player.sendSystemMessage(Component.literal("Stand at your own shop depot to sell to the server."));
            return;
        }
        long unit = ServerBuyList.priceOf(itemId);
        if (unit <= 0) {
            player.sendSystemMessage(Component.literal("The server does not buy that."));
            return;
        }
        Item item = itemOf(itemId);
        if (item == null) {
            return;
        }

        int removed = removeItems(player, item, count);
        if (removed <= 0) {
            player.sendSystemMessage(Component.literal("You have none of that to sell."));
            return;
        }
        info.mudbourn.mmseconomy.economy.ServerLibrary library =
            info.mudbourn.mmseconomy.economy.ServerLibrary.get(player.level().getServer());
        long payout = ServerBuyList.sellTotal(itemId, library.count(itemId), removed);
        library.store(itemId, removed);
        Wallet.deposit(player, payout);
        player.sendSystemMessage(Component.literal("Sold " + removed + " " + item.getName(item.getDefaultInstance()).getString()
            + " for " + Currency.format(payout) + "."));
    }

    // Removes up to count of an item across the player's inventory, returning how many were taken.
    private static int removeItems(ServerPlayer player, Item item, int count) {
        int remaining = count;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == item && !stack.isEmpty()) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        inventory.setChanged();
        return count - remaining;
    }

    // The owner name of a claimed depot this barrel belongs to when the player may not open it, else null.
    public static String protectedOwner(ServerPlayer player, BlockPos pos) {
        if (player.canUseGameMasterBlocks()) {
            return null;
        }
        ServerLevel world = player.level();
        String dimension = world.dimension().identifier().toString();
        for (ShopListing listing : Market.get(world.getServer()).all()) {
            if (!listing.dimension().equals(dimension)) {
                continue;
            }
            if (Depot.contains(world, listing.depot(), pos)) {
                return listing.owner().equals(player.getUUID()) ? null : listing.ownerName();
            }
        }
        return null;
    }

    // Removes the breaker's own listings whose depot includes the barrel at pos, returning how many were closed.
    public static int removeShopAtBrokenBarrel(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();
        String dimension = world.dimension().identifier().toString();
        return Market.get(world.getServer()).removeIf(listing ->
            listing.owner().equals(player.getUUID())
                && listing.dimension().equals(dimension)
                && Depot.contains(world, listing.depot(), pos));
    }

    public static Item itemOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? null : item;
    }

    public static String displayName(String itemId) {
        Item item = itemOf(itemId);
        return item != null ? item.getName(item.getDefaultInstance()).getString() : itemId;
    }

    // The listing's display name, including any custom name its stack carries.
    public static String displayName(ShopListing listing) {
        ItemStack template = listing.template();
        return template.isEmpty() ? listing.item() : template.getHoverName().getString();
    }

    // The live stock of a listing's exact item across a depot's barrels, or 0 when the depot is gone.
    public static int stock(List<Container> barrels, ShopListing listing) {
        int total = 0;
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (listing.matches(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static void takeOne(List<Container> barrels, ShopListing listing) {
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (listing.matches(stack)) {
                    stack.shrink(1);
                    barrel.setChanged();
                    return;
                }
            }
        }
    }

    // Gives count of an item in full stacks, dropping whatever does not fit.
    private static void deliverMany(ServerPlayer player, Item item, int count) {
        int maxStack = Math.max(1, item.getDefaultMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            deliver(player, new ItemStack(item, size));
            remaining -= size;
        }
    }

    private static void deliver(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
    }
}
