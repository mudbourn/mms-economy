package info.mudbourn.mmseconomy.block;

import info.mudbourn.mmseconomy.market.DepotShops;
import info.mudbourn.mmseconomy.market.Market;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

// A shop depot: a 2x2 wall of four barrels. The bottom left block as seen from the front holds the data.
public class DepotBlock extends HorizontalDirectionalBlock implements EntityBlock {

    // Column across the wall from the left as seen from the front, and row from the bottom.
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);

    public DepotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(COLUMN, 0)
            .setValue(ROW, 0));
    }

    public static boolean isMain(BlockState state) {
        return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    // Where the block of this column and row sits, for a depot whose main block is at main.
    public static BlockPos partPos(BlockPos main, Direction facing, int column, int row) {
        return main.relative(facing.getCounterClockWise(), column).above(row);
    }

    // The main block of the depot a part of it belongs to.
    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), state.getValue(COLUMN)).below(state.getValue(ROW));
    }

    // True when pos is one of the four blocks of the depot whose main block is at main.
    public static boolean isPartOf(Level level, BlockPos main, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DepotBlock && mainPos(pos, state).equals(main);
    }

    // The container index a part of the depot opens.
    public static int partIndex(BlockState state) {
        return state.getValue(ROW) * 2 + state.getValue(COLUMN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos main = context.getClickedPos();
        Level level = context.getLevel();
        for (int column = 0; column <= 1; column++) {
            for (int row = 0; row <= 1; row++) {
                BlockPos part = partPos(main, facing, column, row);
                if (!part.equals(main)
                    && (!level.getBlockState(part).canBeReplaced(context) || !level.isInsideBuildHeight(part))) {
                    return null;
                }
            }
        }
        if (level instanceof ServerLevel server
            && !(context.getPlayer() instanceof ServerPlayer player
                && DepotShops.mayPlace(server, player, context.getItemInHand()))) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMain(state) ? new DepotBlockEntity(pos, state) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (isMain(state)) {
            return state;
        }
        BlockState main = level.getBlockState(mainPos(pos, state));
        return main.is(this) && isMain(main) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos main = mainPos(pos, state);
            Direction facing = state.getValue(FACING);
            if (player.isCreative() && level.getBlockEntity(main) instanceof DepotBlockEntity depot) {
                depot.markLostOnBreak();
            }
            for (int column = 0; column <= 1; column++) {
                for (int row = 0; row <= 1; row++) {
                    BlockPos part = partPos(main, facing, column, row);
                    if (!part.equals(main) && !part.equals(pos) && level.getBlockState(part).is(this)) {
                        level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
            if (!isMain(state)) {
                level.destroyBlock(main, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int column = 0; column <= 1; column++) {
            for (int row = 0; row <= 1; row++) {
                if (column != 0 || row != 0) {
                    level.setBlock(partPos(pos, facing, column, row), state.setValue(COLUMN, column).setValue(ROW, row), Block.UPDATE_ALL);
                }
            }
        }
        if (level instanceof ServerLevel server
            && placer instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            DepotShops.onPlaced(server, player, depot, stack);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos main = mainPos(pos, state);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(main) instanceof DepotBlockEntity depot)) {
            return InteractionResult.SUCCESS;
        }
        if (!DepotShops.mayAccess(serverPlayer, depot)) {
            EconomyNetworking.notice(serverPlayer, "This depot belongs to " + depot.ownerName() + ".", true);
            return InteractionResult.FAIL;
        }
        int part = partIndex(state);
        String shopName = Market.get(serverPlayer.level().getServer())
            .shopName(serverPlayer.level().dimension().identifier().toString(), main);
        serverPlayer.openMenu(new SimpleMenuProvider(
            (syncId, inventory, opener) -> ChestMenu.threeRows(syncId, inventory, depot.container(part)),
            shopName.isEmpty() ? Component.translatable("block.mms_economy.depot") : Component.literal(shopName)));
        return InteractionResult.SUCCESS;
    }
}
