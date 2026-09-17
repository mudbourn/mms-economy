package info.mudbourn.mmseconomy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

// One market listing: who sells, from which depot, what item, at what unit price.
public record ShopListing(UUID owner, String ownerName, BlockPos depot, String dimension,
                          String item, long price) {

    public static final Codec<ShopListing> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        net.minecraft.util.Uuids.CODEC.fieldOf("owner").forGetter(ShopListing::owner),
        Codec.STRING.fieldOf("ownerName").forGetter(ShopListing::ownerName),
        BlockPos.CODEC.fieldOf("depot").forGetter(ShopListing::depot),
        Codec.STRING.fieldOf("dimension").forGetter(ShopListing::dimension),
        Codec.STRING.fieldOf("item").forGetter(ShopListing::item),
        Codec.LONG.fieldOf("price").forGetter(ShopListing::price)
    ).apply(instance, ShopListing::new));
}
