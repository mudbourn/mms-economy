package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;

// Converts casino chip stacks to and from a Pice value; a chip is worth its face value in Colt.
public final class Chips {

    private Chips() {
    }

    // Mod id of MineVegas Casino, the only consumer of chips.
    private static final String CASINO_MOD_ID = "roulettecasino";

    private static final long[] FACES = {100L, 50L, 20L, 10L};

    public static boolean casinoLoaded() {
        return FabricLoader.getInstance().isModLoaded(CASINO_MOD_ID);
    }

    private static Item[] items() {
        return new Item[] {ModItems.CHIP_100, ModItems.CHIP_50, ModItems.CHIP_20, ModItems.CHIP_10};
    }

    // Each chip's item id mapped to its face value in Pice, largest first.
    public static Map<String, Long> faceValues() {
        Item[] items = items();
        Map<String, Long> values = new LinkedHashMap<>();
        for (int i = 0; i < FACES.length; i++) {
            values.put(BuiltInRegistries.ITEM.getKey(items[i]).toString(), FACES[i] * Currency.ratio());
        }
        return values;
    }

    public static boolean isChip(String itemId) {
        return faceValues().containsKey(itemId);
    }

    // The Pice value of the smallest chip; chip amounts must be a multiple of it.
    public static long step() {
        return FACES[FACES.length - 1] * Currency.ratio();
    }

    // Removes chips worth exactly amount, or returns false and removes nothing.
    public static boolean removeExact(ServerPlayer player, long amount) {
        if (amount <= 0 || amount % step() != 0) {
            return false;
        }
        Item[] items = items();
        long remaining = amount;
        long[] plan = new long[FACES.length];
        for (int i = 0; i < FACES.length; i++) {
            long unit = FACES[i] * Currency.ratio();
            plan[i] = Math.min(count(player, items[i]), remaining / unit);
            remaining -= plan[i] * unit;
        }
        if (remaining != 0) {
            return false;
        }
        for (int i = 0; i < FACES.length; i++) {
            remove(player, items[i], plan[i]);
        }
        return true;
    }

    private static long count(ServerPlayer player, Item item) {
        long total = 0L;
        int size = player.getInventory().getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void remove(ServerPlayer player, Item item, long amount) {
        long remaining = amount;
        int size = player.getInventory().getContainerSize();
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() != item) {
                continue;
            }
            int take = (int) Math.min(remaining, stack.getCount());
            player.getInventory().removeItem(slot, take);
            remaining -= take;
        }
    }
}
