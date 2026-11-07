package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A batch of Treasury bonds on sale at one set of terms.
public final class BondOffering {

    public static final Codec<BondOffering> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(offering -> offering.id),
        Codec.LONG.fieldOf("principal").forGetter(offering -> offering.principal),
        Codec.INT.fieldOf("couponBps").forGetter(offering -> offering.couponBps),
        Codec.INT.fieldOf("couponDays").forGetter(offering -> offering.couponDays),
        Codec.INT.fieldOf("termDays").forGetter(offering -> offering.termDays),
        Codec.INT.fieldOf("remaining").forGetter(offering -> offering.remaining)
    ).apply(instance, BondOffering::new));

    public final int id;

    public final long principal;

    public final int couponBps;

    public final int couponDays;

    public final int termDays;

    public int remaining;

    public BondOffering(int id, long principal, int couponBps, int couponDays, int termDays,
                        int remaining) {
        this.id = id;
        this.principal = principal;
        this.couponBps = couponBps;
        this.couponDays = couponDays;
        this.termDays = termDays;
        this.remaining = remaining;
    }
}
