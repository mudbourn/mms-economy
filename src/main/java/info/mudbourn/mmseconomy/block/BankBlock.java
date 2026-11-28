package info.mudbourn.mmseconomy.block;

import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

// The proximity anchor for the bank hub tab; a furnace-style directional block whose occupied flag mirrors the lock.
public class BankBlock extends HorizontalDirectionalBlock {

    public static final BooleanProperty OCCUPIED = BlockStateProperties.LIT;

    public BankBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(OCCUPIED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
            .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
            .setValue(OCCUPIED, false);
    }

    // Right-clicking opens the economy hub, honouring the same face and range rules as the /eco command.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                 Player player, BlockHitResult hit) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos bank = info.mudbourn.mmseconomy.economy.BankAccess.nearestBank(
                serverPlayer,
                info.mudbourn.mmseconomy.MmsEconomy.config().bankRange);
            if (bank == null) {
                EconomyNetworking.notice(serverPlayer, "Stand directly in front of the bank's face to use it.", true);
                return InteractionResult.SUCCESS;
            }
            EconomyNetworking.openHub(serverPlayer, bank);
        }
        return InteractionResult.SUCCESS;
    }
}
