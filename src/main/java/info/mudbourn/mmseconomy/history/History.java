package info.mudbourn.mmseconomy.history;

import info.mudbourn.mmseconomy.component.ModComponents;
import net.minecraft.server.level.ServerPlayer;

// Server-side helper for reading and appending a player's ledger.
public final class History {

    private History() {
    }

    public static long currentDay(ServerPlayer player) {
        return player.level().getOverworldClockTime() / 24000L;
    }

    public static void log(ServerPlayer player, HistoryEntry entry) {
        ModComponents.HISTORY.get(player).add(entry);
    }

    public static java.util.List<HistoryEntry> get(ServerPlayer player, HistoryCategory category) {
        return ModComponents.HISTORY.get(player).get(category);
    }
}
