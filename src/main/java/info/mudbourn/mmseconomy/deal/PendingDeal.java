package info.mudbourn.mmseconomy.deal;

import java.util.UUID;

// One in-progress currency trade between two players, torn down on confirm, cancel, or timeout.
public final class PendingDeal {

    private final UUID first;

    private final UUID second;

    private long offerFirst;

    private long offerSecond;

    private boolean confirmedFirst;

    private boolean confirmedSecond;

    private int ticksLeft;

    public PendingDeal(UUID first, UUID second, int timeoutTicks) {
        this.first = first;
        this.second = second;
        this.ticksLeft = timeoutTicks;
    }

    public UUID first() {
        return first;
    }

    public UUID second() {
        return second;
    }

    public UUID other(UUID who) {
        return who.equals(first) ? second : first;
    }

    public long offer(UUID who) {
        return who.equals(first) ? offerFirst : offerSecond;
    }

    // Staging a new offer clears both confirmations, so nothing settles on stale terms.
    public void setOffer(UUID who, long amount) {
        if (who.equals(first)) {
            offerFirst = amount;
        } else {
            offerSecond = amount;
        }
        confirmedFirst = false;
        confirmedSecond = false;
    }

    public void confirm(UUID who) {
        if (who.equals(first)) {
            confirmedFirst = true;
        } else {
            confirmedSecond = true;
        }
    }

    public boolean bothConfirmed() {
        return confirmedFirst && confirmedSecond;
    }

    public boolean tick() {
        return --ticksLeft <= 0;
    }
}
