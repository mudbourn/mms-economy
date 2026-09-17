package info.mudbourn.mmseconomy.block;

import com.mojang.serialization.MapCodec;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

// The proximity anchor for the bank hub tab; a furnace-style directional block whose occupied flag mirrors the lock.
public class BankBlock extends HorizontalFacingBlock {

    public static final MapCodec<BankBlock> CODEC = createCodec(BankBlock::new);

    public static final BooleanProperty OCCUPIED = Properties.LIT;

    public BankBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState()
            .with(FACING, net.minecraft.util.math.Direction.NORTH)
            .with(OCCUPIED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
            .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
            .with(OCCUPIED, false);
    }

    // Right-clicking opens the economy hub on the bank tab for the interacting player.
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            EconomyNetworking.openHub(serverPlayer, pos);
        }
        return ActionResult.SUCCESS;
    }
}
