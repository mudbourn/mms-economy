package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

// Registers the data components the mod adds to items.
public final class ModDataComponents {

    // The id a depot item carries so its shop follows it from block to block.
    public static DataComponentType<UUID> DEPOT_ID;

    private ModDataComponents() {
    }

    public static void register() {
        DEPOT_ID = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "depot_id"),
            DataComponentType.<UUID>builder()
                .persistent(UUIDUtil.CODEC)
                .networkSynchronized(UUIDUtil.STREAM_CODEC)
                .build());
    }
}
