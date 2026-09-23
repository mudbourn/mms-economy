package info.mudbourn.mmseconomy.market;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
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
        double limit = (double) range * range;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        int minChunkX = ChunkSectionPos.getSectionCoord(origin.getX() - range);
        int maxChunkX = ChunkSectionPos.getSectionCoord(origin.getX() + range);
        int minChunkZ = ChunkSectionPos.getSectionCoord(origin.getZ() - range);
        int maxChunkZ = ChunkSectionPos.getSectionCoord(origin.getZ() + range);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof BarrelBlockEntity)) {
                        continue;
                    }
                    BlockPos anchor = entity.getPos();
                    double distance = player.squaredDistanceTo(
                        anchor.getX() + 0.5,
                        anchor.getY() + 0.5,
                        anchor.getZ() + 0.5);
                    if (distance > limit || distance >= bestDistance || completePlane(world, anchor) == null) {
                        continue;
                    }
                    best = anchor.toImmutable();
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    // The depot whose barrel the player is looking directly at within reach, or null, so a shop targets exact barrels.
    @Nullable
    public static BlockPos lookedAtDepot(ServerPlayerEntity player, double reach) {
        HitResult hit = player.raycast(reach, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return anchorContaining(player.getEntityWorld(), ((BlockHitResult) hit).getBlockPos());
    }

    // The min-corner anchor of the complete 2x2 depot that includes this barrel, or null.
    @Nullable
    private static BlockPos anchorContaining(ServerWorld world, BlockPos barrel) {
        if (!(world.getBlockEntity(barrel) instanceof BarrelBlockEntity)) {
            return null;
        }
        for (int dx = -1; dx <= 0; dx++) {
            for (int dy = -1; dy <= 0; dy++) {
                for (int dz = -1; dz <= 0; dz++) {
                    BlockPos anchor = barrel.add(dx, dy, dz);
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
