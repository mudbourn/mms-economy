package info.mudbourn.mmseconomy.market;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// A resource depot: four vanilla barrels forming a 2x2 coplanar wall, keyed by the minimum-corner anchor.
public final class Depot {

    private Depot() {
    }

    // The four anchor offsets for each axis-aligned plane a 2x2 wall can lie in.
    private static final Direction[][] PLANES = {
        {Direction.EAST, Direction.UP},
        {Direction.SOUTH, Direction.UP},
        {Direction.EAST, Direction.SOUTH}
    };

    // The nearest complete depot anchor within range of the player, or null.
    @Nullable
    public static BlockPos detectNear(ServerPlayerEntity player, int range) {
        ServerWorld world = player.getEntityWorld();
        BlockPos origin = player.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos anchor = origin.add(dx, dy, dz);
                    if (barrels(world, anchor).isEmpty()) {
                        continue;
                    }
                    double distance = player.squaredDistanceTo(
                        anchor.getX() + 0.5,
                        anchor.getY() + 0.5,
                        anchor.getZ() + 0.5);
                    if (distance <= (double) range * range && distance < bestDistance) {
                        best = anchor.toImmutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    public static boolean isValid(ServerWorld world, BlockPos anchor) {
        return !barrels(world, anchor).isEmpty();
    }

    public static boolean inRange(ServerPlayerEntity player, BlockPos anchor, int range) {
        double distance = player.squaredDistanceTo(
            anchor.getX() + 0.5,
            anchor.getY() + 0.5,
            anchor.getZ() + 0.5);
        return distance <= (double) range * range;
    }

    // A cylinder check: within horizontalRange on the ground plane and verticalRange up or down, so a buried depot still counts.
    public static boolean withinReach(ServerPlayerEntity player, BlockPos anchor,
                                      int horizontalRange, int verticalRange) {
        double dx = player.getX() - (anchor.getX() + 0.5);
        double dz = player.getZ() - (anchor.getZ() + 0.5);
        double dy = Math.abs(player.getY() - (anchor.getY() + 0.5));
        return dx * dx + dz * dz <= (double) horizontalRange * horizontalRange
            && dy <= verticalRange + 0.5;
    }

    // The four barrel inventories of the depot anchored at the given corner, or an empty list when incomplete.
    public static List<Inventory> barrels(ServerWorld world, BlockPos anchor) {
        BlockPos[] positions = completePlane(world, anchor);
        if (positions == null) {
            return List.of();
        }
        List<Inventory> found = new ArrayList<>(4);
        for (BlockPos pos : positions) {
            found.add((BarrelBlockEntity) world.getBlockEntity(pos));
        }
        return found;
    }

    // True when the block at pos is one of the four barrels of the depot anchored here.
    public static boolean contains(ServerWorld world, BlockPos anchor, BlockPos pos) {
        BlockPos[] positions = completePlane(world, anchor);
        if (positions == null) {
            return false;
        }
        for (BlockPos member : positions) {
            if (member.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    // The four barrel positions of the first complete plane at this anchor, or null.
    private static BlockPos[] completePlane(ServerWorld world, BlockPos anchor) {
        if (!(world.getBlockEntity(anchor) instanceof BarrelBlockEntity)) {
            return null;
        }
        for (Direction[] plane : PLANES) {
            BlockPos[] positions = {
                anchor,
                anchor.offset(plane[0]),
                anchor.offset(plane[1]),
                anchor.offset(plane[0]).offset(plane[1])
            };
            boolean complete = true;
            for (BlockPos pos : positions) {
                if (!(world.getBlockEntity(pos) instanceof BarrelBlockEntity)) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return positions;
            }
        }
        return null;
    }
}
