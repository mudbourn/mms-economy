package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.block.DepotBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

// Registers the mod's block entity types.
public final class ModBlockEntities {

    public static BlockEntityType<DepotBlockEntity> DEPOT;

    private ModBlockEntities() {
    }

    public static void register() {
        DEPOT = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "depot"),
            new BlockEntityType<>(DepotBlockEntity::new, Set.of(ModBlocks.DEPOT)));
    }
}
