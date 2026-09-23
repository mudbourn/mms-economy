package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.block.BankBlock;
import info.mudbourn.mmseconomy.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

// Server-side proximity checks against placed bank blocks.
public final class BankAccess {

    // Blocks in front of the face a player may stand and still reach the bank.
    private static final double MAX_FORWARD = 2.0;

    // How far off the face column's centre line a player may stand.
    private static final double MAX_LATERAL = 0.75;

    // Vertical band around the block a standing player must stay within.
    private static final double MIN_VERTICAL = -1.5;
    private static final double MAX_VERTICAL = 0.75;

    // The block offsets from the player beyond which no bank face can satisfy the limits above.
    private static final int SCAN_HORIZONTAL = 3;
    private static final int SCAN_BELOW = 1;
    private static final int SCAN_ABOVE = 2;

    private BankAccess() {
    }

    // The bank block whose face the player is standing at within range, or null when none qualifies.
    @Nullable
    public static BlockPos nearestBank(ServerPlayerEntity player, int range) {
        ServerWorld world = player.getEntityWorld();
        BlockPos origin = player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos best = null;
        double bestForward = Double.MAX_VALUE;
        int horizontal = Math.min(range, SCAN_HORIZONTAL);
        int below = Math.min(range, SCAN_BELOW);
        int above = Math.min(range, SCAN_ABOVE);

        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dy = -below; dy <= above; dy++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isOf(ModBlocks.BANK)) {
                        continue;
                    }
                    double forward = forwardIfAtFace(player, pos, state);
                    if (forward >= 0.0 && forward < bestForward) {
                        best = pos.toImmutable();
                        bestForward = forward;
                    }
                }
            }
        }
        return best;
    }

    // Forward distance from the face when the player stands at it, or a negative sentinel otherwise.
    private static double forwardIfAtFace(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        Direction front = state.get(BankBlock.FACING);
        double relX = player.getX() - (pos.getX() + 0.5);
        double relY = player.getY() - pos.getY();
        double relZ = player.getZ() - (pos.getZ() + 0.5);

        double forward = relX * front.getOffsetX() + relZ * front.getOffsetZ();
        double lateral = relX * front.getOffsetZ() - relZ * front.getOffsetX();

        if (forward <= 0.3 || forward > MAX_FORWARD + 0.5) {
            return -1.0;
        }
        if (Math.abs(lateral) > MAX_LATERAL) {
            return -1.0;
        }
        if (relY < MIN_VERTICAL || relY > MAX_VERTICAL) {
            return -1.0;
        }
        return forward;
    }
}
