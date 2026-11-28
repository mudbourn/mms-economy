package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.display.ShopFrame;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;

// Draws the shop frame with the glow item frame's model and lighting.
public class ShopFrameRenderer extends ItemFrameRenderer<ShopFrame> {

    private static final int GLOW_LIGHT = 5;

    private final BlockModelResolver blockModelResolver;

    public ShopFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockModelResolver = context.getBlockModelResolver();
    }

    @Override
    protected int getBlockLightLevel(ShopFrame frame, BlockPos pos) {
        return Math.max(GLOW_LIGHT, super.getBlockLightLevel(frame, pos));
    }

    @Override
    public void extractRenderState(ShopFrame frame, ItemFrameRenderState state, float partialTick) {
        super.extractRenderState(frame, state, partialTick);
        state.isGlowFrame = true;
        if (!state.isInvisible) {
            blockModelResolver.updateForItemFrame(state.frameModel, true, state.mapId != null);
        }
    }
}
