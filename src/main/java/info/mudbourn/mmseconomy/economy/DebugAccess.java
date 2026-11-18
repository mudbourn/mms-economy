package info.mudbourn.mmseconomy.economy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

// Tracks players with debug full-UI access, who bypass bank and depot proximity checks.
public final class DebugAccess {

    private static final Set<UUID> ENABLED = new HashSet<>();

    private DebugAccess() {
    }

    public static boolean has(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    // Flips the flag for the player and returns the new state.
    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (ENABLED.remove(id)) {
            return false;
        }
        ENABLED.add(id);
        return true;
    }
}
