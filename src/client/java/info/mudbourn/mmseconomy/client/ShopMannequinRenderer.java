package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.display.ShopMannequin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

// Draws the shop mannequin as a humanoid on the 64x32 wooden skin, with armor, held items and a worn head item.
public class ShopMannequinRenderer
    extends LivingEntityRenderer<ShopMannequin, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "shop_mannequin"),
        "main");

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        MmsEconomy.MOD_ID,
        "textures/entity/shop_mannequin.png");

    private static final float SHADOW_RADIUS = 0.4f;

    public ShopMannequinRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(LAYER)), SHADOW_RADIUS);
        ArmorModelSet<HumanoidModel<HumanoidRenderState>> armor =
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new);
        this.addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
        this.addLayer(new ItemInHandLayer<>(this));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 32);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    @Override
    public void extractRenderState(ShopMannequin mannequin, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(mannequin, state, partialTick);
        HumanoidMobRenderer.extractHumanoidRenderState(mannequin, state, partialTick, this.itemModelResolver);
        state.bodyRot = Mth.rotLerp(partialTick, mannequin.yRotO, mannequin.getYRot());
        state.yRot = 0.0f;
        state.xRot = 0.0f;
    }
}
