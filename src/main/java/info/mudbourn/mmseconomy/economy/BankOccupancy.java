package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.block.BankBlock;
import info.mudbourn.mmseconomy.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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

    public static boolean isOccupiedByOther(BlockPos pos, ServerPlayerEntity player) {
        UUID occupant = OCCUPANTS.get(pos);
        return occupant != null && !occupant.equals(player.getUuid());
    }

    // Binds the block to the player if free or already theirs, mirroring the lit state.
    public static boolean claim(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        UUID occupant = OCCUPANTS.get(pos);
        if (occupant != null && !occupant.equals(player.getUuid())) {
            return false;
        }
        OCCUPANTS.put(pos.toImmutable(), player.getUuid());
        setLit(world, pos, true);
        return true;
    }

    // Frees every block held by the player.
    public static void release(ServerWorld world, ServerPlayerEntity player) {
        OCCUPANTS.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(player.getUuid())) {
                setLit(world, entry.getKey(), false);
                return true;
            }
            return false;
        });
    }

    private static void setLit(ServerWorld world, BlockPos pos, boolean lit) {
        BlockState state = world.getBlockState(pos);
        if (state.isOf(ModBlocks.BANK) && state.get(BankBlock.OCCUPIED) != lit) {
            world.setBlockState(pos, state.with(BankBlock.OCCUPIED, lit));
        }
    }
}
