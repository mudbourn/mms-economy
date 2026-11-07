package info.mudbourn.mmseconomy.deal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Uuids;

import java.util.UUID;

// A seller's standing wholesale offer: an exact item sold in lots of a fixed size at a fixed price per lot.
public record WholesaleOffer(int id, UUID seller, String sellerName, Spot depot,
                             ItemStack stack, int lotSize, long lotPrice) {

    public static final Codec<WholesaleOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(WholesaleOffer::id),
        Uuids.CODEC.fieldOf("seller").forGetter(WholesaleOffer::seller),
        Codec.STRING.fieldOf("sellerName").forGetter(WholesaleOffer::sellerName),
        Spot.CODEC.fieldOf("depot").forGetter(WholesaleOffer::depot),
        ItemStack.CODEC.fieldOf("stack").forGetter(WholesaleOffer::stack),
        Codec.INT.fieldOf("lotSize").forGetter(WholesaleOffer::lotSize),
        Codec.LONG.fieldOf("lotPrice").forGetter(WholesaleOffer::lotPrice)
    ).apply(instance, WholesaleOffer::new));
}
