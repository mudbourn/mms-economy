package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModBlocks;
import info.mudbourn.mmseconomy.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

// Converts physical gem stacks to and from a Pice value, largest denomination first.
public final class Gems {

    private Gems() {
    }

    // Denominations in descending Pice value, paired with their item.
    private static Item coltBlock() {
        return ModBlocks.COLT_BLOCK.asItem();
    }

    private static Item piceBlock() {
        return ModBlocks.PICE_BLOCK.asItem();
    }

    // A storage block is worth 20 of its base gem, not the crafting-standard nine.
    private static final long BLOCK_UNITS = 20L;

    public static long value(Item item) {
        int ratio = Currency.ratio();
        if (item == ModItems.COLT) {
            return ratio;
        }
        if (item == ModItems.PICE) {
            return 1L;
        }
        if (item == coltBlock()) {
            return BLOCK_UNITS * ratio;
        }
        if (item == piceBlock()) {
            return BLOCK_UNITS;
        }
        return 0L;
    }

    // The total Pice value of every gem the player is carrying.
    public static long inventoryValue(ServerPlayerEntity player) {
        long total = 0L;
        int size = player.getInventory().size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            long unit = value(stack.getItem());
            if (unit > 0) {
                total += unit * stack.getCount();
            }
        }
        return total;
    }

    // Removes gems worth exactly amount, or returns false and removes nothing when that value cannot be assembled.
    public static boolean removeExact(ServerPlayerEntity player, long amount) {
        if (amount <= 0 || inventoryValue(player) < amount) {
            return false;
        }

        long ratio = Currency.ratio();
        long[] denoms = {BLOCK_UNITS * ratio, ratio, BLOCK_UNITS, 1L};
        Item[][] items = {
            {coltBlock()},
            {ModItems.COLT},
            {piceBlock()},
            {ModItems.PICE}
        };

        long remaining = amount;
        long[][] plan = new long[denoms.length][];
        for (int d = 0; d < denoms.length; d++) {
            plan[d] = new long[items[d].length];
            for (int i = 0; i < items[d].length && remaining >= denoms[d]; i++) {
                long available = count(player, items[d][i]);
                long take = Math.min(available, remaining / denoms[d]);
                plan[d][i] = take;
                remaining -= take * denoms[d];
            }
        }
        if (remaining != 0) {
            return false;
        }

        for (int d = 0; d < denoms.length; d++) {
            for (int i = 0; i < items[d].length; i++) {
                remove(player, items[d][i], plan[d][i]);
            }
        }
        return true;
    }

    // Gives the player gems worth amount, making change with the fewest stacks and dropping any that do not fit.
    public static void give(ServerPlayerEntity player, long amount) {
        long ratio = Currency.ratio();
        long remaining = amount;

        remaining = grant(player, coltBlock(), remaining, BLOCK_UNITS * ratio);
        remaining = grant(player, ModItems.COLT, remaining, ratio);
        remaining = grant(player, piceBlock(), remaining, BLOCK_UNITS);
        grant(player, ModItems.PICE, remaining, 1L);
    }

    private static long grant(ServerPlayerEntity player, Item item, long remaining, long unit) {
        long count = remaining / unit;
        while (count > 0) {
            int stackSize = (int) Math.min(count, 99L);
            ItemStack stack = new ItemStack(item, stackSize);
            if (!player.getInventory().insertStack(stack)) {
                player.dropItem(stack, false);
            }
            count -= stackSize;
        }
        return remaining % unit;
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
