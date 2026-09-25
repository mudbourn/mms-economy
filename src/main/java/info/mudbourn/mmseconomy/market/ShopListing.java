package info.mudbourn.mmseconomy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

// One market listing: who sells, from which depot, what exact item, at what unit price.
public record ShopListing(UUID owner, String ownerName, BlockPos depot, String dimension,
                          String item, long price, ItemStack stack) {

    public static final Codec<ShopListing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        net.minecraft.util.Uuids.CODEC.fieldOf("owner").forGetter(ShopListing::owner),
        Codec.STRING.fieldOf("ownerName").forGetter(ShopListing::ownerName),
        BlockPos.CODEC.fieldOf("depot").forGetter(ShopListing::depot),
        Codec.STRING.fieldOf("dimension").forGetter(ShopListing::dimension),
        Codec.STRING.fieldOf("item").forGetter(ShopListing::item),
        Codec.LONG.fieldOf("price").forGetter(ShopListing::price),
        ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter(ShopListing::stack)
    ).apply(instance, ShopListing::new));

    // The single-count stack a buyer receives, falling back to the bare item for listings saved without one.
    public ItemStack template() {
        if (!stack.isEmpty()) {
            return stack.copyWithCount(1);
        }
        net.minecraft.item.Item bare = Marketplace.itemOf(item);
        return bare != null ? new ItemStack(bare) : ItemStack.EMPTY;
    }

    // True when a barrel stack is this listing's item, components included when the listing recorded them.
    public boolean matches(ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (!stack.isEmpty()) {
            return ItemStack.areItemsAndComponentsEqual(stack, candidate);
        }
        return Registries.ITEM.getId(candidate.getItem()).toString().equals(item);
    }
}
