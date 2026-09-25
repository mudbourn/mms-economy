package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.function.Function;

// Registers the mod's gem currency items and the shop book.
public final class ModItems {

    public static Item PICE;

    public static Item COLT;

    public static Item SHOP_BOOK;

    public static void register() {
        PICE = register("pice", Item::new, new Item.Settings());
        COLT = register("colt", Item::new, new Item.Settings());
        SHOP_BOOK = register("shop_book", Item::new, new Item.Settings().maxCount(1));
    }

    private static Item register(String name, Function<Item.Settings, Item> factory,
                                 Item.Settings settings) {
        Identifier id = Identifier.of(MmsEconomy.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(Registries.ITEM.getKey(), id);
        Item item = factory.apply(settings.registryKey(key));

        return Registry.register(Registries.ITEM, id, item);
    }
}
