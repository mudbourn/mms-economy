package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// Money a borrower repays in equal installments of principal plus flat interest, timed by their active playtime.
public final class Loan {

    public static final Codec<Loan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(loan -> loan.id),
        Codec.STRING.fieldOf("borrower").forGetter(loan -> loan.borrower),
        Codec.STRING.fieldOf("borrowerName").forGetter(loan -> loan.borrowerName),
        Codec.STRING.fieldOf("lender").forGetter(loan -> loan.lender),
        Codec.STRING.fieldOf("lenderName").forGetter(loan -> loan.lenderName),
        Codec.LONG.fieldOf("principal").forGetter(loan -> loan.principal),
        Codec.INT.fieldOf("rateBps").forGetter(loan -> loan.rateBps),
        Codec.INT.fieldOf("periodDays").forGetter(loan -> loan.periodDays),
        Codec.INT.fieldOf("installments").forGetter(loan -> loan.installments),
        Codec.INT.fieldOf("lateBps").forGetter(loan -> loan.lateBps),
        Codec.LONG.fieldOf("principalLeft").forGetter(loan -> loan.principalLeft),
        Codec.INT.fieldOf("paymentsLeft").forGetter(loan -> loan.paymentsLeft),
        Codec.LONG.fieldOf("snapshot").forGetter(loan -> loan.snapshot)
    ).apply(instance, Loan::new));

    public final int id;

    public final String borrower;

    public final String borrowerName;

    // The lender's UUID, or empty when the bank lent it.
    public final String lender;

    public final String lenderName;

    public final long principal;

    // Interest on the original principal charged with every installment.
    public final int rateBps;

    public final int periodDays;

    public final int installments;

    public final int lateBps;

    public long principalLeft;

    public int paymentsLeft;

    // The borrower's active tick count when the current installment period began.
    public long snapshot;

    public Loan(int id, String borrower, String borrowerName, String lender, String lenderName,
                long principal, int rateBps, int periodDays, int installments, int lateBps,
                long principalLeft, int paymentsLeft, long snapshot) {
        this.id = id;
        this.borrower = borrower;
        this.borrowerName = borrowerName;
        this.lender = lender;
        this.lenderName = lenderName;
        this.principal = principal;
        this.rateBps = rateBps;
        this.periodDays = Loans.normalizeDays(periodDays);
        this.installments = Math.max(1, installments);
        this.lateBps = lateBps;
        this.principalLeft = principalLeft;
        this.paymentsLeft = paymentsLeft;
        this.snapshot = snapshot;
    }

    // Active borrower ticks one installment period lasts.
    public long periodTicks() {
        return periodDays * 24000L;
    }

    public boolean byBank() {
        return lender.isEmpty();
    }

    // The interest part of every installment.
    public long interest() {
        return Billing.bpsOf(principal, rateBps);
    }

    // The principal part of the next installment; the last one takes the remainder.
    public long principalDue() {
        return paymentsLeft <= 1 ? principalLeft : Math.min(principalLeft, principal / installments);
    }

    public long installment() {
        return principalDue() + interest();
    }

    // What settles the loan now: the principal left plus one period of interest.
    public long payoff() {
        return principalLeft + interest();
    }
}
