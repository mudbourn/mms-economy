package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

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

    // The Pice value of the smallest chip; chip amounts must be a multiple of it.
    public static long step() {
        return FACES[FACES.length - 1] * Currency.ratio();
    }

    // Removes chips worth exactly amount, or returns false and removes nothing.
    public static boolean removeExact(ServerPlayerEntity player, long amount) {
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

    // Gives chips worth amount with the fewest stacks, dropping any that do not fit.
    public static void give(ServerPlayerEntity player, long amount) {
        Item[] items = items();
        long remaining = amount;
        for (int i = 0; i < FACES.length; i++) {
            long unit = FACES[i] * Currency.ratio();
            long count = remaining / unit;
            remaining %= unit;
            while (count > 0) {
                int stackSize = (int) Math.min(count, 99L);
                ItemStack stack = new ItemStack(items[i], stackSize);
                if (!player.getInventory().insertStack(stack)) {
                    player.dropItem(stack, false);
                }
                count -= stackSize;
            }
        }
    }

    private static long count(ServerPlayerEntity player, Item item) {
        long total = 0L;
        int size = player.getInventory().size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void remove(ServerPlayerEntity player, Item item, long amount) {
        long remaining = amount;
        int size = player.getInventory().size();
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() != item) {
                continue;
            }
            int take = (int) Math.min(remaining, stack.getCount());
            player.getInventory().removeStack(slot, take);
            remaining -= take;
        }
    }
}
