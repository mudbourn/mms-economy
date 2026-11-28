package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Chips;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

// A single creative tab that holds every mms-economy item and block.
public final class ModItemGroups {

    public static CreativeModeTab ECONOMY;

    public static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "economy");
        ResourceKey<CreativeModeTab> key = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), id);

        ECONOMY = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.mms_economy.economy"))
            .icon(() -> new ItemStack(ModItems.COLT))
            .displayItems((context, entries) -> {
                entries.accept(ModItems.COLT);
                entries.accept(ModItems.PICE);
                entries.accept(ModBlocks.COLT_BLOCK);
                entries.accept(ModBlocks.PICE_BLOCK);
                entries.accept(ModBlocks.BANK);
                entries.accept(ModBlocks.DEPOT);
                entries.accept(ModItems.SHOP_FRAME);
                entries.accept(ModItems.SHOP_MANNEQUIN);
                entries.accept(ModItems.SHOP_BOOK);
                entries.accept(ModItems.BOND);
                entries.accept(ModItems.LOAN_DEED);
                if (Chips.casinoLoaded()) {
                    entries.accept(ModItems.CHIP_10);
                    entries.accept(ModItems.CHIP_20);
                    entries.accept(ModItems.CHIP_50);
                    entries.accept(ModItems.CHIP_100);
                }
            })
            .build());
    }
}
