package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.display.ShopFrame;
import info.mudbourn.mmseconomy.display.ShopMannequin;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;

// Registers the shop display entity types.
public final class ModEntities {

    public static EntityType<ShopFrame> SHOP_FRAME;

    public static EntityType<ShopMannequin> SHOP_MANNEQUIN;

    private ModEntities() {
    }

    public static void register() {
        SHOP_FRAME = register("shop_frame", EntityType.Builder.<ShopFrame>of(ShopFrame::new, MobCategory.MISC)
            .sized(0.5f, 0.5f)
            .eyeHeight(0.0f)
            .clientTrackingRange(10)
            .updateInterval(Integer.MAX_VALUE)
            .fireImmune()
            .noSummon());
        SHOP_MANNEQUIN = register("shop_mannequin", EntityType.Builder.<ShopMannequin>of(ShopMannequin::new, MobCategory.MISC)
            .sized(0.8f, 1.8f)
            .eyeHeight(1.62f)
            .clientTrackingRange(10)
            .fireImmune()
            .noSummon());
        FabricDefaultAttributeRegistry.register(SHOP_MANNEQUIN, ArmorStand.createAttributes());
    }

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, name);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, builder.build(key));
    }
}
