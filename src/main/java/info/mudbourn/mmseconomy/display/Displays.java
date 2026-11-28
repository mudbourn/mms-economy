package info.mudbourn.mmseconomy.display;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.Treasury;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.market.DepotRecord;
import info.mudbourn.mmseconomy.market.DepotShops;
import info.mudbourn.mmseconomy.market.Depots;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.registry.ModEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

// Placement, range upkeep and safe despawn shared by the shop frame and the shop mannequin.
public final class Displays {

    private static final int UPKEEP_INTERVAL = 20;

    private Displays() {
    }

    // True when the player owns the display or may bypass ownership.
    public static boolean mayManage(ServerPlayer player, UUID owner) {
        return player.getUUID().equals(owner) || player.canUseGameMasterBlocks();
    }

    // Checks that a placed depot of the player is in range of pos, then spends a free credit or charges the fee.
    public static DepotRecord claim(ServerPlayer player, DisplayKind kind, BlockPos pos) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        Depots depots = Depots.get(server);
        DepotRecord depot = nearestDepot(depots, player.getUUID(), level, pos);
        if (depot == null) {
            EconomyNetworking.notice(
                player,
                "Place it within " + MmsEconomy.config().displayDepotRange + " blocks of a depot you own.",
                true);
            return null;
        }
        if (depots.useCredit(player.getUUID(), kind)) {
            return depot;
        }
        long fee = kind.fee();
        if (fee > 0) {
            if (!Wallet.withdraw(player, fee)) {
                EconomyNetworking.notice(player, "You need " + Currency.format(fee) + " to place this.", true);
                return null;
            }
            Treasury.get(server).credit(fee);
        }
        return depot;
    }

    private static DepotRecord nearestDepot(Depots depots, UUID owner, ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().identifier().toString();
        DepotRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (DepotRecord record : depots.ownedBy(owner)) {
            if (record.parked() || !record.dimension.equals(dimension) || !within(record.pos, pos)) {
                continue;
            }
            double distance = record.pos.distSqr(pos);
            if (distance < bestDistance) {
                best = record;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean within(BlockPos depot, BlockPos pos) {
        int range = MmsEconomy.config().displayDepotRange;
        return Math.abs(depot.getX() - pos.getX()) <= range
            && Math.abs(depot.getY() - pos.getY()) <= range
            && Math.abs(depot.getZ() - pos.getZ()) <= range;
    }

    // Every 20 ticks despawns the display when its depot is parked, gone, elsewhere or out of range.
    public static <T extends Entity & ShopDisplay> void upkeep(T display) {
        if (!(display.level() instanceof ServerLevel level) || display.tickCount % UPKEEP_INTERVAL != 0) {
            return;
        }
        DepotRecord depot = Depots.get(level.getServer()).find(display.depot());
        if (depot == null
            || depot.parked()
            || !depot.dimension.equals(level.dimension().identifier().toString())
            || !within(depot.pos, display.blockPosition())) {
            despawn(display);
        }
    }

    // Returns the display and its contents to the owner, grants a free placement credit and removes it.
    public static <T extends Entity & ShopDisplay> void despawn(T display) {
        if (display.isRemoved() || !(display.level() instanceof ServerLevel level)) {
            return;
        }
        release(display);
        if (display.owner() != null) {
            Depots.get(level.getServer()).addCredit(display.owner(), display.kind());
        }
        display.discard();
    }

    // Returns the display item and everything the display holds to the owner, once.
    public static <T extends Entity & ShopDisplay> void release(T display) {
        if (display.isRemoved() || display.owner() == null || !(display.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        for (ItemStack stack : display.takeContents()) {
            DepotShops.give(server, display.owner(), stack);
        }
        DepotShops.give(server, display.owner(), new ItemStack(display.kind().item()));
    }

    // Despawns every loaded display linked to the depot, in any dimension.
    public static void despawnLinked(MinecraftServer server, UUID depot) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ShopFrame frame : level.getEntities(ModEntities.SHOP_FRAME, linked -> depot.equals(linked.depot()))) {
                despawn(frame);
            }
            for (ShopMannequin mannequin : level.getEntities(ModEntities.SHOP_MANNEQUIN, linked -> depot.equals(linked.depot()))) {
                despawn(mannequin);
            }
        }
    }
}
