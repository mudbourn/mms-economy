package info.mudbourn.mmseconomy.deal;

import java.util.UUID;

// A bill request or a loan request waiting for its target to accept before it times out.
public final class PendingOffer {

    public enum Type {
        BILL,
        LOAN
    }

    public final int id;

    public final Type type;

    public final UUID proposer;

    public final String proposerName;

    public final UUID target;

    public final long amount;

    public final int rateBps;

    public final int days;

    public final int installments;

    public final int lateBps;

    public final String label;

    public int ticksLeft;

    public PendingOffer(int id, Type type, UUID proposer, String proposerName, UUID target, long amount,
                        int rateBps, int days, int installments, int lateBps, String label, int ticksLeft) {
        this.id = id;
        this.type = type;
        this.proposer = proposer;
        this.proposerName = proposerName;
        this.target = target;
        this.amount = amount;
        this.rateBps = rateBps;
        this.days = days;
        this.installments = installments;
        this.lateBps = lateBps;
        this.label = label;
        this.ticksLeft = ticksLeft;
    }
}
