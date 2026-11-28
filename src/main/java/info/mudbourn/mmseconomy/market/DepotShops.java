package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.block.DepotBlockEntity;
import info.mudbourn.mmseconomy.display.Displays;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.registry.ModDataComponents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

// Ties a shop to its depot item: acquiring, parking when the depot is broken, restoring when it is placed again.
public final class DepotShops {

    private DepotShops() {
    }

    // True when the player may place this depot item, telling them why when not.
    public static boolean mayPlace(ServerLevel level, ServerPlayer player, ItemStack stack) {
        UUID id = stack.get(ModDataComponents.DEPOT_ID);
        DepotRecord record = id == null ? null : Depots.get(level.getServer()).find(id);
        if (record != null) {
            if (!record.owner.equals(player.getUUID()) && !player.canUseGameMasterBlocks()) {
                EconomyNetworking.notice(player, "This depot belongs to " + record.ownerName + ".", true);
                return false;
            }
            if (!record.parked()) {
                EconomyNetworking.notice(player, "This depot is already placed.", true);
                return false;
            }
            return true;
        }
        long fee = MmsEconomy.config().shopSetupFeePice;
        if (fee > 0 && !Wallet.canAfford(player, fee)) {
            EconomyNetworking.notice(player, "You need " + Currency.format(fee) + " to acquire a depot.", true);
            return false;
        }
        return true;
    }

    // True when the player may open or break the depot.
    public static boolean mayAccess(ServerPlayer player, DepotBlockEntity depot) {
        return player.getUUID().equals(depot.owner()) || player.canUseGameMasterBlocks();
    }

    // Binds a freshly placed depot: restores its parked shop, or charges the fee and starts a new one.
    public static void onPlaced(ServerLevel level, ServerPlayer player, DepotBlockEntity depot, ItemStack stack) {
        MinecraftServer server = level.getServer();
        Depots depots = Depots.get(server);
        UUID carried = stack.get(ModDataComponents.DEPOT_ID);
        DepotRecord record = carried == null ? null : depots.find(carried);
        if (record == null) {
            long fee = MmsEconomy.config().shopSetupFeePice;
            if (fee > 0 && Wallet.withdraw(player, fee)) {
                Treasury.get(server).credit(fee);
            }
            record = depots.create(
                carried != null ? carried : UUID.randomUUID(),
                player.getUUID(),
                player.getName().getString());
            adoptLost(depots, record);
        }
        depot.bind(record.id, record.owner, record.ownerName);
        record.dimension = level.dimension().identifier().toString();
        record.pos = depot.getBlockPos().immutable();
        restore(server, record, depot);
        depots.setDirty();
    }

    // Moves every lost parked shop of the owner into the record.
    private static void adoptLost(Depots depots, DepotRecord record) {
        for (DepotRecord old : depots.ownedBy(record.owner)) {
            if (old == record || !old.lost || !old.parked()) {
                continue;
            }
            record.listings.addAll(old.listings);
            record.stock.addAll(old.stock);
            if (record.shopName.isEmpty()) {
                record.shopName = old.shopName;
            }
            depots.remove(old.id);
        }
    }

    // Puts a record's parked listings, shop name and stock into the depot standing at its position.
    private static void restore(MinecraftServer server, DepotRecord record, DepotBlockEntity depot) {
        Market market = Market.get(server);
        for (ShopListing listing : record.listings) {
            market.add(new ShopListing(
                listing.owner(),
                listing.ownerName(),
                record.pos,
                record.dimension,
                listing.item(),
                listing.price(),
                listing.stack()));
        }
        if (!record.shopName.isEmpty()) {
            market.renameShop(record.dimension, record.pos, record.shopName);
        }
        List<Container> containers = depot.containers();
        for (ItemStack stack : record.stock) {
            int inserted = Depot.insert(containers, stack, stack.getCount());
            if (inserted < stack.getCount()) {
                give(server, record.owner, stack.copyWithCount(stack.getCount() - inserted));
            }
        }
        record.listings.clear();
        record.stock.clear();
        record.shopName = "";
    }

    // Hides the shop of a depot being removed: parks its listings, name and stock under its id.
    public static void park(ServerLevel level, DepotBlockEntity depot, boolean lost) {
        MinecraftServer server = level.getServer();
        Depots depots = Depots.get(server);
        DepotRecord record = depots.find(depot.depotId());
        if (record == null) {
            record = depots.create(depot.depotId(), depot.owner(), depot.ownerName());
        }
        String dimension = level.dimension().identifier().toString();
        BlockPos pos = depot.getBlockPos();
        Market market = Market.get(server);
        record.listings.addAll(market.extractAt(dimension, pos));
        String name = market.takeShopName(dimension, pos);
        if (!name.isEmpty()) {
            record.shopName = name;
        }
        record.stock.addAll(depot.drainStock());
        record.dimension = null;
        record.pos = null;
        record.lost = lost;
        depots.setDirty();
        Displays.despawnLinked(server, record.id);
    }

    // Marks a parked shop lost when its depot item is destroyed in the world.
    public static void onEntityUnload(Entity entity, ServerLevel level) {
        if (!(entity instanceof ItemEntity item)
            || entity.getRemovalReason() == null
            || !entity.getRemovalReason().shouldDestroy()) {
            return;
        }
        ItemStack stack = item.getItem();
        UUID id = stack.isEmpty() ? null : stack.get(ModDataComponents.DEPOT_ID);
        if (id == null) {
            return;
        }
        Depots depots = Depots.get(level.getServer());
        DepotRecord record = depots.find(id);
        if (record != null && record.parked()) {
            record.lost = true;
            depots.setDirty();
        }
    }

    // Gives an owner a stack now when online, into the inventory or at their feet, else on their next join.
    public static void give(MinecraftServer server, UUID owner, ItemStack stack) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        } else {
            Depots.get(server).hold(owner, stack);
        }
    }

    // Hands over everything held for a player who just joined.
    public static void deliverPending(ServerPlayer player) {
        for (ItemStack stack : Depots.get(player.level().getServer()).claim(player.getUUID())) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }
}
