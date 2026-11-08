package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
        Codec.BOOL.fieldOf("matured").forGetter(bond -> bond.matured),
        Codec.STRING.optionalFieldOf("vault", "").forGetter(bond -> bond.vault),
        Gate.MAP_CODEC.forGetter(bond -> new Gate(bond.carriedInterest, bond.snapshot, bond.required))
    ).apply(instance, Bond::new));

    // The playtime gate fields, stored flat beside the bond terms.
    public record Gate(long carriedInterest, long snapshot, long required) {

        public static final MapCodec<Gate> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.LONG.optionalFieldOf("carriedInterest", 0L).forGetter(Gate::carriedInterest),
            Codec.LONG.optionalFieldOf("snapshot", 0L).forGetter(Gate::snapshot),
            Codec.LONG.optionalFieldOf("required", 0L).forGetter(Gate::required)
        ).apply(instance, Gate::new));
    }

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

    // The UUID of the player whose bank vault holds this bond, or empty while the paper is out.
    public String vault;

    // Coupon interest from periods that missed the playtime gate, owed with the next period that meets it.
    public long carriedInterest;

    // The vault owner's active tick count when the current coupon period began.
    public long snapshot;

    // Active ticks the current period needs, prorated when deposited mid-period, or 0 for a full period.
    public long required;

    public Bond(String id, String issuer, String issuerName, long principal, int couponBps,
                int couponDays, int lateBps, long issueDay, long maturityDay, long nextCoupon,
                long accruedInterest, long accruedPrincipal, boolean matured, String vault,
                long carriedInterest, long snapshot, long required) {
        this.id = id;
        this.issuer = issuer;
        this.issuerName = issuerName;
        this.principal = principal;
        this.couponBps = couponBps;
        this.couponDays = Bonds.normalizeDays(couponDays);
        this.lateBps = lateBps;
        this.issueDay = issueDay;
        this.maturityDay = maturityDay;
        this.nextCoupon = nextCoupon;
        this.accruedInterest = accruedInterest;
        this.accruedPrincipal = accruedPrincipal;
        this.matured = matured;
        this.vault = vault;
        this.carriedInterest = carriedInterest;
        this.snapshot = snapshot;
        this.required = required;
    }

    private Bond(String id, String issuer, String issuerName, long principal, int couponBps,
                 int couponDays, int lateBps, long issueDay, long maturityDay, long nextCoupon,
                 long accruedInterest, long accruedPrincipal, boolean matured, String vault, Gate gate) {
        this(id, issuer, issuerName, principal, couponBps, couponDays, lateBps, issueDay, maturityDay,
            nextCoupon, accruedInterest, accruedPrincipal, matured, vault, gate.carriedInterest(),
            gate.snapshot(), gate.required());
    }

    public boolean byTreasury() {
        return issuer.isEmpty();
    }

    public long accrued() {
        return accruedInterest + accruedPrincipal;
    }
}
