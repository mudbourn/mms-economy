package info.mudbourn.mmseconomy.economy;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Tracks players with debug full-UI access, who bypass bank and depot proximity checks.
public final class DebugAccess {

    private static final Set<UUID> ENABLED = new HashSet<>();

    private DebugAccess() {
    }

    public static boolean has(ServerPlayerEntity player) {
        return ENABLED.contains(player.getUuid());
    }

    // Flips the flag for the player and returns the new state.
    public static boolean toggle(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (ENABLED.remove(id)) {
            return false;
        }
        ENABLED.add(id);
        return true;
    }
}
