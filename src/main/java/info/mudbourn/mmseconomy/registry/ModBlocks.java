package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.block.BankBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

// Registers the mod's blocks and their matching block items.
public final class ModBlocks {

    public static Block PICE_BLOCK;

    public static Block COLT_BLOCK;

    public static Block BANK;

    // Currency block stack cap, matching the stack mod the live server runs.
    private static final int CURRENCY_STACK = 99;

    public static void register() {
        PICE_BLOCK = register("pice_block", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK.weathering().unaffected()), CURRENCY_STACK);
        COLT_BLOCK = register("colt_block", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GOLD_BLOCK), CURRENCY_STACK);
        BANK = register("bank_block", BankBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(-1.0f, 3600000.0f), 64);
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties settings, int maxStack) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, name);

        ResourceKey<Block> blockKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), id);
        Block block = factory.apply(settings.setId(blockKey));
        Registry.register(BuiltInRegistries.BLOCK, id, block);

        ResourceKey<Item> itemKey = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
        Item.Properties itemSettings = new Item.Properties()
            .setId(itemKey)
            .useBlockDescriptionPrefix()
            .stacksTo(maxStack);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemSettings));

        return block;
    }
}
