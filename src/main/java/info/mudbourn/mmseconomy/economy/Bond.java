package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// The server-side terms and payout state of one bearer bond; the paper item only carries the id.
public final class Bond {

    public static final Codec<Bond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(bond -> bond.id),
        Codec.STRING.fieldOf("issuer").forGetter(bond -> bond.issuer),
        Codec.STRING.fieldOf("issuerName").forGetter(bond -> bond.issuerName),
        Codec.LONG.fieldOf("principal").forGetter(bond -> bond.principal),
        Codec.INT.fieldOf("couponBps").forGetter(bond -> bond.couponBps),
        Codec.INT.fieldOf("couponDays").forGetter(bond -> bond.couponDays),
        Codec.INT.fieldOf("lateBps").forGetter(bond -> bond.lateBps),
        Codec.LONG.fieldOf("issueDay").forGetter(bond -> bond.issueDay),
        Codec.LONG.fieldOf("maturityDay").forGetter(bond -> bond.maturityDay),
        Codec.LONG.fieldOf("nextCoupon").forGetter(bond -> bond.nextCoupon),
        Codec.LONG.fieldOf("accruedInterest").forGetter(bond -> bond.accruedInterest),
        Codec.LONG.fieldOf("accruedPrincipal").forGetter(bond -> bond.accruedPrincipal),
        Codec.BOOL.fieldOf("matured").forGetter(bond -> bond.matured)
    ).apply(instance, Bond::new));

    public final String id;

    // The issuer's UUID, or empty when the Treasury issued it.
    public final String issuer;

    public final String issuerName;

    public final long principal;

    public final int couponBps;

    public final int couponDays;

    public final int lateBps;

    public final long issueDay;

    public final long maturityDay;

    public long nextCoupon;

    public long accruedInterest;

    public long accruedPrincipal;

    public boolean matured;

    public Bond(String id, String issuer, String issuerName, long principal, int couponBps,
                int couponDays, int lateBps, long issueDay, long maturityDay, long nextCoupon,
                long accruedInterest, long accruedPrincipal, boolean matured) {
        this.id = id;
        this.issuer = issuer;
        this.issuerName = issuerName;
        this.principal = principal;
        this.couponBps = couponBps;
        this.couponDays = couponDays;
        this.lateBps = lateBps;
        this.issueDay = issueDay;
        this.maturityDay = maturityDay;
        this.nextCoupon = nextCoupon;
        this.accruedInterest = accruedInterest;
        this.accruedPrincipal = accruedPrincipal;
        this.matured = matured;
    }

    public boolean byTreasury() {
        return issuer.isEmpty();
    }

    public long accrued() {
        return accruedInterest + accruedPrincipal;
    }
}
