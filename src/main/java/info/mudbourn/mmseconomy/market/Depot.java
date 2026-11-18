package info.mudbourn.mmseconomy.market;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
    public static BlockPos detectNear(ServerPlayer player, int range) {
        ServerLevel world = player.level();
        BlockPos origin = player.blockPosition();
        double limit = (double) range * range;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int minChunkX = SectionPos.blockToSectionCoord(origin.getX() - range);
        int maxChunkX = SectionPos.blockToSectionCoord(origin.getX() + range);
        int minChunkZ = SectionPos.blockToSectionCoord(origin.getZ() - range);
        int maxChunkZ = SectionPos.blockToSectionCoord(origin.getZ() + range);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof BarrelBlockEntity)) {
                        continue;
                    }
                    BlockPos anchor = entity.getBlockPos();
                    double distance = player.distanceToSqr(
                        anchor.getX() + 0.5,
                        anchor.getY() + 0.5,
                        anchor.getZ() + 0.5);
                    if (distance > limit || distance >= bestDistance || completePlane(world, anchor) == null) {
                        continue;
                    }
                    best = anchor.immutable();
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // The depot whose barrel the player is looking directly at within reach, or null, so a shop targets exact barrels.
    @Nullable
    public static BlockPos lookedAtDepot(ServerPlayer player, double reach) {
        HitResult hit = player.pick(reach, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return anchorContaining(player.level(), ((BlockHitResult) hit).getBlockPos());
    }

    // The min-corner anchor of the complete 2x2 depot that includes this barrel, or null.
    @Nullable
    private static BlockPos anchorContaining(ServerLevel world, BlockPos barrel) {
        if (!(world.getBlockEntity(barrel) instanceof BarrelBlockEntity)) {
            return null;
        }
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos anchor = barrel.offset(dx, dy, dz);
                    BlockPos[] positions = completePlane(world, anchor);
                    if (positions == null) {
                        continue;
                    }
                    for (BlockPos member : positions) {
                        if (member.equals(barrel)) {
                            return anchor;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static boolean isValid(ServerLevel world, BlockPos anchor) {
        return !barrels(world, anchor).isEmpty();
    }

    public static boolean inRange(ServerPlayer player, BlockPos anchor, int range) {
        double distance = player.distanceToSqr(
            anchor.getX() + 0.5,
            anchor.getY() + 0.5,
            anchor.getZ() + 0.5);
        return distance <= (double) range * range;
    }

    // A cylinder check: within horizontalRange on the ground plane and verticalRange up or down, so a buried depot still counts.
    public static boolean withinReach(ServerPlayer player, BlockPos anchor,
                                      int horizontalRange, int verticalRange) {
        double dx = player.getX() - (anchor.getX() + 0.5);
        double dz = player.getZ() - (anchor.getZ() + 0.5);
        double dy = Math.abs(player.getY() - (anchor.getY() + 0.5));
        return dx * dx + dz * dz <= (double) horizontalRange * horizontalRange
            && dy <= verticalRange + 0.5;
    }

    // The four barrel inventories of the depot anchored at the given corner, or an empty list when incomplete.
    public static List<Container> barrels(ServerLevel world, BlockPos anchor) {
        BlockPos[] positions = completePlane(world, anchor);
        if (positions == null) {
            return List.of();
        }
        List<Container> found = new ArrayList<>(4);
        for (BlockPos pos : positions) {
            found.add((BarrelBlockEntity) world.getBlockEntity(pos));
        }
        return found;
    }

    // How many items equal to the template the barrels hold.
    public static int count(List<Container> barrels, ItemStack template) {
        int total = 0;
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (ItemStack.isSameItemSameComponents(stack, template)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    // Removes up to count items equal to the template from the barrels, returning how many were taken.
    public static int take(List<Container> barrels, ItemStack template, int count) {
        int left = count;
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize() && left > 0; slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (ItemStack.isSameItemSameComponents(stack, template)) {
                    int took = Math.min(left, stack.getCount());
                    stack.shrink(took);
                    left -= took;
                }
            }
            barrel.setChanged();
        }
        return count - left;
    }

    // Inserts up to count items equal to the template, topping up matching stacks before using empty slots, returning how many fit.
    public static int insert(List<Container> barrels, ItemStack template, int count) {
        int left = count;
        for (boolean useEmpty : new boolean[] {false, true}) {
            for (Container barrel : barrels) {
                int limit = Math.min(template.getMaxStackSize(), barrel.getMaxStackSize());
                for (int slot = 0; slot < barrel.getContainerSize() && left > 0; slot++) {
                    ItemStack stack = barrel.getItem(slot);
                    if (useEmpty && stack.isEmpty()) {
                        int put = Math.min(limit, left);
                        barrel.setItem(slot, template.copyWithCount(put));
                        left -= put;
                    } else if (!useEmpty
                        && !stack.isEmpty()
                        && ItemStack.isSameItemSameComponents(stack, template)
                        && stack.getCount() < limit) {
                        int put = Math.min(limit - stack.getCount(), left);
                        stack.grow(put);
                        left -= put;
                    }
                }
                barrel.setChanged();
            }
        }
        return count - left;
    }

    // True when the block at pos is one of the four barrels of the depot anchored here.
    public static boolean contains(ServerLevel world, BlockPos anchor, BlockPos pos) {
        if (!withinFootprint(anchor, pos)) {
            return false;
        }
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

    // True when pos lies in the 2x2x2 box a depot anchored here could occupy, checked without touching the world.
    private static boolean withinFootprint(BlockPos anchor, BlockPos pos) {
        int dx = pos.getX() - anchor.getX();
        int dy = pos.getY() - anchor.getY();
        int dz = pos.getZ() - anchor.getZ();
        return dx >= 0 && dx <= 1 && dy >= 0 && dy <= 1 && dz >= 0 && dz <= 1;
    }

    // The four barrel positions of the first complete plane at this anchor, or null.
    private static BlockPos[] completePlane(ServerLevel world, BlockPos anchor) {
        if (!(world.getBlockEntity(anchor) instanceof BarrelBlockEntity)) {
            return null;
        }
        for (Direction[] plane : PLANES) {
            BlockPos[] positions = {
                anchor,
                anchor.relative(plane[0]),
                anchor.relative(plane[1]),
                anchor.relative(plane[0]).relative(plane[1])
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
