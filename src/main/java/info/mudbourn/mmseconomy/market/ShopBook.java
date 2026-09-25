package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

// A book naming one shop by owner and depot; placed on a lectern it opens that shop.
public final class ShopBook {

    private ShopBook() {
    }

    public record Target(String owner, BlockPos depot, String dimension) {
    }

    public static ItemStack create(ShopListing listing, String shopName) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("owner", listing.ownerName());
        nbt.putInt("x", listing.depot().getX());
        nbt.putInt("y", listing.depot().getY());
        nbt.putInt("z", listing.depot().getZ());
        nbt.putString("dimension", listing.dimension());

        ItemStack stack = new ItemStack(ModItems.SHOP_BOOK);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(shopName.isEmpty()
            ? listing.ownerName() + "'s Shop"
            : shopName));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Depot at " + listing.depot().toShortString()),
            Text.literal("Place on a lectern to open this shop."))));
        return stack;
    }

    // The shop a book names, or null when the stack is not a shop book or carries no target.
    public static Target read(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (!stack.isOf(ModItems.SHOP_BOOK) || data == null) {
            return null;
        }
        NbtCompound nbt = data.copyNbt();
        String owner = nbt.getString("owner", "");
        if (owner.isEmpty()) {
            return null;
        }
        return new Target(
            owner,
            new BlockPos(nbt.getInt("x", 0), nbt.getInt("y", 0), nbt.getInt("z", 0)),
            nbt.getString("dimension", ""));
    }
}
