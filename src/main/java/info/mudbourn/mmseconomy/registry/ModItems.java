package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.display.DisplayItem;
import info.mudbourn.mmseconomy.display.DisplayKind;
import info.mudbourn.mmseconomy.economy.BondItem;
import info.mudbourn.mmseconomy.economy.LoanDeedItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

// Registers the mod's gem currency items, casino chips, the shop book, the bond, the loan deed and the shop display items.
public final class ModItems {

    public static Item PICE;

    public static Item COLT;

    public static Item SHOP_BOOK;

    public static Item BOND;

    public static Item LOAN_DEED;

    public static Item SHOP_FRAME;

    public static Item SHOP_MANNEQUIN;

    public static Item CHIP_10;

    public static Item CHIP_20;

    public static Item CHIP_50;

    public static Item CHIP_100;

    public static void register() {
        PICE = register("pice", Item::new, new Item.Properties());
        COLT = register("colt", Item::new, new Item.Properties());
        SHOP_BOOK = register("shop_book", Item::new, new Item.Properties().stacksTo(1));
        BOND = register("bond", BondItem::new, new Item.Properties().stacksTo(1));
        LOAN_DEED = register("loan_deed", LoanDeedItem::new, new Item.Properties().stacksTo(1));
        SHOP_FRAME = register("shop_frame", settings -> new DisplayItem(settings, DisplayKind.FRAME), new Item.Properties());
        SHOP_MANNEQUIN = register("shop_mannequin", settings -> new DisplayItem(settings, DisplayKind.MANNEQUIN), new Item.Properties());
        CHIP_10 = register("chip_10", Item::new, new Item.Properties());
        CHIP_20 = register("chip_20", Item::new, new Item.Properties());
        CHIP_50 = register("chip_50", Item::new, new Item.Properties());
        CHIP_100 = register("chip_100", Item::new, new Item.Properties());
    }

    private static Item register(String name, Function<Item.Properties, Item> factory,
                                 Item.Properties settings) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
        Item item = factory.apply(settings.setId(key));

        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }
}
