package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// A recurring charge from a payer to a payee, due every set number of in-game days.
public final class Bill {

    public static final Codec<Bill> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(bill -> bill.id),
        Codec.STRING.fieldOf("payer").forGetter(bill -> bill.payer),
        Codec.STRING.fieldOf("payerName").forGetter(bill -> bill.payerName),
        Codec.STRING.fieldOf("payee").forGetter(bill -> bill.payee),
        Codec.STRING.fieldOf("payeeName").forGetter(bill -> bill.payeeName),
        Codec.LONG.fieldOf("amount").forGetter(bill -> bill.amount),
        Codec.INT.fieldOf("intervalDays").forGetter(bill -> bill.intervalDays),
        Codec.INT.fieldOf("lateBps").forGetter(bill -> bill.lateBps),
        Codec.STRING.fieldOf("label").forGetter(bill -> bill.label),
        Codec.LONG.fieldOf("nextDue").forGetter(bill -> bill.nextDue)
    ).apply(instance, Bill::new));

    public final int id;

    public final String payer;

    public final String payerName;

    public final String payee;

    public final String payeeName;

    public final long amount;

    public final int intervalDays;

    public final int lateBps;

    public final String label;

    public long nextDue;

    public Bill(int id, String payer, String payerName, String payee, String payeeName,
                long amount, int intervalDays, int lateBps, String label, long nextDue) {
        this.id = id;
        this.payer = payer;
        this.payerName = payerName;
        this.payee = payee;
        this.payeeName = payeeName;
        this.amount = amount;
        this.intervalDays = intervalDays;
        this.lateBps = lateBps;
        this.label = label;
        this.nextDue = nextDue;
    }
}
