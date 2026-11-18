package info.mudbourn.mmseconomy.market;

import info.mudbourn.mmseconomy.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

// A book naming one shop by owner and depot; placed on a lectern it opens that shop.
public final class ShopBook {

    private ShopBook() {
    }

    public record Target(String owner, BlockPos depot, String dimension) {
    }

    public static ItemStack create(ShopListing listing, String shopName) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("owner", listing.ownerName());
        nbt.putInt("x", listing.depot().getX());
        nbt.putInt("y", listing.depot().getY());
        nbt.putInt("z", listing.depot().getZ());
        nbt.putString("dimension", listing.dimension());

        ItemStack stack = new ItemStack(ModItems.SHOP_BOOK);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(shopName.isEmpty()
            ? listing.ownerName() + "'s Shop"
            : shopName));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Depot at " + listing.depot().toShortString()),
            Component.literal("Place on a lectern to open this shop."))));
        return stack;
    }

    // The shop a book names, or null when the stack is not a shop book or carries no target.
    public static Target read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(ModItems.SHOP_BOOK) || data == null) {
            return null;
        }
        CompoundTag nbt = data.copyTag();
        String owner = nbt.getStringOr("owner", "");
        if (owner.isEmpty()) {
            return null;
        }
        return new Target(
            owner,
            new BlockPos(nbt.getIntOr("x", 0), nbt.getIntOr("y", 0), nbt.getIntOr("z", 0)),
            nbt.getStringOr("dimension", ""));
    }
}
