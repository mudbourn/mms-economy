package info.mudbourn.mmseconomy.deal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.math.BlockPos;

// A depot anchor together with the dimension it stands in.
public record Spot(BlockPos pos, String dimension) {

    public static final Codec<Spot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BlockPos.CODEC.fieldOf("pos").forGetter(Spot::pos),
        Codec.STRING.fieldOf("dimension").forGetter(Spot::dimension)
    ).apply(instance, Spot::new));
}
