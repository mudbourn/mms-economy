package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

// A single creative tab that holds every mms-economy item and block.
public final class ModItemGroups {

    public static ItemGroup ECONOMY;

    public static void register() {
        Identifier id = Identifier.of(MmsEconomy.MOD_ID, "economy");
        RegistryKey<ItemGroup> key = RegistryKey.of(Registries.ITEM_GROUP.getKey(), id);

        ECONOMY = Registry.register(Registries.ITEM_GROUP, key, ItemGroup.create(ItemGroup.Row.TOP, 0)
            .displayName(Text.translatable("itemGroup.mms_economy.economy"))
            .icon(() -> new ItemStack(ModItems.COLT))
            .entries((context, entries) -> {
                entries.add(ModItems.COLT);
                entries.add(ModItems.PICE);
                entries.add(ModBlocks.COLT_BLOCK);
                entries.add(ModBlocks.PICE_BLOCK);
                entries.add(ModBlocks.BANK);
                entries.add(ModItems.SHOP_BOOK);
            })
            .build());
    }
}
