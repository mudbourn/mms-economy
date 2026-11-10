package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// An unpaid amount that grows by its late rate and is collected from the payer when possible.
public final class Debt {

    public static final Codec<Debt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(debt -> debt.id),
        Codec.STRING.fieldOf("payer").forGetter(debt -> debt.payer),
        Codec.STRING.fieldOf("payerName").forGetter(debt -> debt.payerName),
        Codec.STRING.fieldOf("payee").forGetter(debt -> debt.payee),
        Codec.STRING.fieldOf("payeeName").forGetter(debt -> debt.payeeName),
        Codec.STRING.fieldOf("ref").forGetter(debt -> debt.ref),
        Codec.BOOL.fieldOf("bond").forGetter(debt -> debt.bond),
        Codec.BOOL.fieldOf("principalPart").forGetter(debt -> debt.principalPart),
        Codec.LONG.fieldOf("amount").forGetter(debt -> debt.amount),
        Codec.LONG.optionalFieldOf("base", -1L).forGetter(debt -> debt.base),
        Codec.INT.fieldOf("lateBps").forGetter(debt -> debt.lateBps),
        Codec.STRING.fieldOf("label").forGetter(debt -> debt.label),
        Codec.BOOL.optionalFieldOf("loan", false).forGetter(debt -> debt.loan),
        Codec.LONG.optionalFieldOf("snapshot", 0L).forGetter(debt -> debt.snapshot)
    ).apply(instance, Debt::new));

    public final int id;

    // The payer's UUID, or empty when the Treasury owes it.
    public final String payer;

    public final String payerName;

    // The payee's UUID, or empty for a bond debt, which is paid into the bond, and for a bank loan debt.
    public final String payee;

    public final String payeeName;

    // The bill id, bond id or loan id this debt came from.
    public final String ref;

    public final boolean bond;

    // For a bond or loan debt, whether it is principal rather than interest.
    public final boolean principalPart;

    public long amount;

    // The part of the amount still owed as principal; late growth is not part of it.
    public long base;

    public final int lateBps;

    public final String label;

    public final boolean loan;

    // For a loan debt, the payer's active tick count when its current late day began.
    public long snapshot;

    public Debt(int id, String payer, String payerName, String payee, String payeeName, String ref,
                boolean bond, boolean principalPart, long amount, long base, int lateBps,
                String label, boolean loan, long snapshot) {
        this.id = id;
        this.payer = payer;
        this.payerName = payerName;
        this.payee = payee;
        this.payeeName = payeeName;
        this.ref = ref;
        this.bond = bond;
        this.principalPart = principalPart;
        this.amount = amount;
        this.base = base >= 0 ? base : (principalPart ? amount : 0L);
        this.lateBps = lateBps;
        this.label = label;
        this.loan = loan;
        this.snapshot = snapshot;
    }
}
