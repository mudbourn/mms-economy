package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.block.BankBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.function.Function;

// Registers the mod's blocks and their matching block items.
public final class ModBlocks {

    public static Block PICE_BLOCK;

    public static Block COLT_BLOCK;

    public static Block BANK;

    public static void register() {
        PICE_BLOCK = register("pice_block", Block::new,
            AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK));
        COLT_BLOCK = register("colt_block", Block::new,
            AbstractBlock.Settings.copy(Blocks.GOLD_BLOCK));
        BANK = register("bank_block", BankBlock::new,
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK));
    }

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory,
                                  AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(MmsEconomy.MOD_ID, name);

        RegistryKey<Block> blockKey = RegistryKey.of(Registries.BLOCK.getKey(), id);
        Block block = factory.apply(settings.registryKey(blockKey));
        Registry.register(Registries.BLOCK, id, block);

        RegistryKey<Item> itemKey = RegistryKey.of(Registries.ITEM.getKey(), id);
        Item.Settings itemSettings = new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey();
        Registry.register(Registries.ITEM, id, new BlockItem(block, itemSettings));

        return block;
    }
}
