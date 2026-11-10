package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A batch of bonds on sale at one set of terms, issued by the Treasury or a player.
public final class BondOffering {

    public static final Codec<BondOffering> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(offering -> offering.id),
        Codec.LONG.fieldOf("principal").forGetter(offering -> offering.principal),
        Codec.INT.fieldOf("couponBps").forGetter(offering -> offering.couponBps),
        Codec.INT.fieldOf("couponDays").forGetter(offering -> offering.couponDays),
        Codec.INT.fieldOf("termDays").forGetter(offering -> offering.termDays),
        Codec.INT.fieldOf("remaining").forGetter(offering -> offering.remaining),
        Codec.STRING.optionalFieldOf("issuer", "").forGetter(offering -> offering.issuer),
        Codec.STRING.optionalFieldOf("issuerName", "Treasury").forGetter(offering -> offering.issuerName),
        Codec.INT.optionalFieldOf("lateBps", 0).forGetter(offering -> offering.lateBps)
    ).apply(instance, BondOffering::new));

    public final int id;

    public final long principal;

    public final int couponBps;

    public final int couponDays;

    public final int termDays;

    public int remaining;

    // The issuer's UUID, or empty when the Treasury issued it.
    public final String issuer;

    public final String issuerName;

    public final int lateBps;

    public BondOffering(int id, long principal, int couponBps, int couponDays, int termDays,
                        int remaining, String issuer, String issuerName, int lateBps) {
        this.id = id;
        this.principal = principal;
        this.couponBps = couponBps;
        this.couponDays = Bonds.normalizeDays(couponDays);
        this.termDays = termDays;
        this.remaining = remaining;
        this.issuer = issuer;
        this.issuerName = issuerName;
        this.lateBps = lateBps;
    }

    public boolean byTreasury() {
        return issuer.isEmpty();
    }
}
