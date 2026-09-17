package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.BankAccess;
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

    // Lists the held item at a unit price, charging the one-time setup fee on the first claim of the nearest depot.
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
        BlockPos depot = Depot.detectNear(player, MmsEconomy.config().depotRange);
        if (depot == null) {
            if (!DebugAccess.has(player)) {
                player.sendMessage(Text.literal("Stand near a 2x2 barrel depot to run a shop."), false);
                return;
            }
            depot = player.getBlockPos();
        }

        Market market = Market.get(world.getServer());
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
        if (!debug && (!listing.dimension().equals(world.getRegistryKey().getValue().toString())
            || !Depot.inRange(buyer, listing.depot(), MmsEconomy.config().depotRange))) {
            buyer.sendMessage(Text.literal("Go to the depot at " + listing.depot().toShortString()
                + " to buy this."), false);
            return;
        }

        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(listing.owner());
        if (owner == null) {
            buyer.sendMessage(Text.literal("The owner is offline. Try again later."), false);
            return;
        }

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

        Tax.Settlement settlement = Tax.settleTaxed(buyer, owner, listing.price(), TaxKind.PURCHASE);
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
        History.log(owner, new HistoryEntry(day, HistoryCategory.PURCHASE, name, 1,
            settlement.gross(), settlement.tax(), settlement.net(), buyer.getName().getString()));

        buyer.sendMessage(Text.literal("Bought " + name + " for " + Currency.format(settlement.gross())
            + " (tax " + Currency.format(settlement.tax()) + ")."), false);
    }

    // Sells the held item to the server at buy-list prices, minting the payout, and requires standing at a bank block.
    public static void serverSell(ServerPlayerEntity player, int count) {
        if (!DebugAccess.has(player)
            && BankAccess.nearestBank(player, MmsEconomy.config().bankRange) == null) {
            player.sendMessage(Text.literal("Stand near a bank block to sell to the server."), false);
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

    public static Item itemOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id != null ? Registries.ITEM.get(id) : null;
    }

    public static String displayName(String itemId) {
        Item item = itemOf(itemId);
        return item != null ? item.getName().getString() : itemId;
    }

    // The live stock of an item across a depot's barrels, or 0 when the depot is gone.
    public static int stock(ServerWorld world, BlockPos depot, String itemId) {
        Item item = itemOf(itemId);
        if (item == null) {
            return 0;
        }
        int total = 0;
        for (Inventory barrel : Depot.barrels(world, depot)) {
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

    private static void deliver(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }
}
