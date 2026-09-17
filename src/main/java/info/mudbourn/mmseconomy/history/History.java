package info.mudbourn.mmseconomy.history;

import info.mudbourn.mmseconomy.component.ModComponents;
import net.minecraft.server.network.ServerPlayerEntity;

// Server-side helper for reading and appending a player's ledger.
public final class History {

    private History() {
    }

    public static long currentDay(ServerPlayerEntity player) {
        return player.getEntityWorld().getTimeOfDay() / 24000L;
    }

    public static void log(ServerPlayerEntity player, HistoryEntry entry) {
        ModComponents.HISTORY.get(player).add(entry);
    }

    public static java.util.List<HistoryEntry> get(ServerPlayerEntity player, HistoryCategory category) {
        return ModComponents.HISTORY.get(player).get(category);
    }
}
