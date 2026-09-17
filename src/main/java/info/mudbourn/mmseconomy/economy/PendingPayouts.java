package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Shop proceeds and their history rows owed to players who were offline when their listings sold, delivered on next join.
public final class PendingPayouts extends PersistentState {

    public static final Codec<PendingPayouts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("owed").forGetter(state -> state.owed),
        Codec.unboundedMap(Codec.STRING, HistoryEntry.CODEC.listOf())
            .fieldOf("history").forGetter(state -> state.history)
    ).apply(instance, PendingPayouts::new));

    public static final PersistentStateType<PendingPayouts> TYPE = new PersistentStateType<>(
        "mms_economy_pending_payouts",
        PendingPayouts::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final Map<String, Long> owed;

    private final Map<String, List<HistoryEntry>> history;

    public PendingPayouts() {
        this.owed = new HashMap<>();
        this.history = new HashMap<>();
    }

    public PendingPayouts(Map<String, Long> owed, Map<String, List<HistoryEntry>> history) {
        this.owed = new HashMap<>(owed);
        this.history = new HashMap<>();
        history.forEach((key, entries) -> this.history.put(key, new ArrayList<>(entries)));
    }

    public static PendingPayouts get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // Records a sale for an offline owner: the net owed plus their ledger row for it.
    public void record(UUID owner, long net, HistoryEntry entry) {
        if (net > 0) {
            owed.merge(owner.toString(), net, Long::sum);
        }
        history.computeIfAbsent(owner.toString(), key -> new ArrayList<>()).add(entry);
        markDirty();
    }

    // Removes and returns everything owed to the owner, or zero when nothing is owed.
    public long claim(UUID owner) {
        Long amount = owed.remove(owner.toString());
        if (amount == null || amount <= 0) {
            return 0L;
        }
        markDirty();
        return amount;
    }

    // Removes and returns the owner's escrowed ledger rows, oldest first.
    public List<HistoryEntry> claimHistory(UUID owner) {
        List<HistoryEntry> entries = history.remove(owner.toString());
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        markDirty();
        return entries;
    }
}
