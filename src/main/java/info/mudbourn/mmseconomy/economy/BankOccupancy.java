package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.block.BankBlock;
import info.mudbourn.mmseconomy.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// The single-teller lock: the in-memory map is the source of truth and the block's occupied state only mirrors it.
public final class BankOccupancy {

    private static final Map<BlockPos, UUID> OCCUPANTS = new HashMap<>();

    private BankOccupancy() {
    }

    public static void clear() {
        OCCUPANTS.clear();
    }

    // On load, unlight every saved occupied bank, since no player is actively using a teller yet.
    public static void scrubOnLoad(MinecraftServer server) {
        OccupiedBanks saved = OccupiedBanks.get(server);
        for (OccupiedBanks.Entry entry : saved.view()) {
            ServerLevel world = worldFor(server, entry.dimension());
            if (world != null) {
                setLit(world, entry.pos(), false);
            }
        }
        saved.clearAll();
        OCCUPANTS.clear();
    }

    private static ServerLevel worldFor(MinecraftServer server, String dimension) {
        for (ServerLevel world : server.getAllLevels()) {
            if (world.dimension().identifier().toString().equals(dimension)) {
                return world;
            }
        }
        return null;
    }

    private static String dimensionOf(ServerLevel world) {
        return world.dimension().identifier().toString();
    }

    public static boolean isOccupiedByOther(BlockPos pos, ServerPlayer player) {
        UUID occupant = OCCUPANTS.get(pos);
        return occupant != null && !occupant.equals(player.getUUID());
    }

    // Binds the block to the player if free or already theirs, mirroring the lit state.
    public static boolean claim(ServerLevel world, BlockPos pos, ServerPlayer player) {
        UUID occupant = OCCUPANTS.get(pos);
        if (occupant != null && !occupant.equals(player.getUUID())) {
            return false;
        }
        OCCUPANTS.put(pos.immutable(), player.getUUID());
        setLit(world, pos, true);
        OccupiedBanks.get(world.getServer()).add(dimensionOf(world), pos);
        return true;
    }

    // Frees every block held by the player.
    public static void release(ServerLevel world, ServerPlayer player) {
        OccupiedBanks saved = OccupiedBanks.get(world.getServer());
        String dimension = dimensionOf(world);
        OCCUPANTS.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(player.getUUID())) {
                setLit(world, entry.getKey(), false);
                saved.remove(dimension, entry.getKey());
                return true;
            }
            return false;
        });
    }

    private static void setLit(ServerLevel world, BlockPos pos, boolean lit) {
        BlockState state = world.getBlockState(pos);
        if (state.is(ModBlocks.BANK) && state.getValue(BankBlock.OCCUPIED) != lit) {
            world.setBlockAndUpdate(pos, state.setValue(BankBlock.OCCUPIED, lit));
        }
    }
}
