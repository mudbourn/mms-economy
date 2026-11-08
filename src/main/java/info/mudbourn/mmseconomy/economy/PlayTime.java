package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Active play time per player in ticks; time spent idle for over five minutes does not count.
public final class PlayTime extends PersistentState {

    public static final Codec<PlayTime> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("ticks").forGetter(state -> state.ticks)
    ).apply(instance, PlayTime::new));

    public static final PersistentStateType<PlayTime> TYPE = new PersistentStateType<>(
        "mms_economy_playtime",
        PlayTime::new,
        CODEC,
        DataFixTypes.LEVEL);

    private static final int CHECK_INTERVAL = 20;

    private static final long IDLE_LIMIT = 6000L;

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private final Map<String, Long> ticks;

    private record Seen(double x, double y, double z, float yaw, float pitch, long activeAt) {
    }

    public PlayTime() {
        this.ticks = new HashMap<>();
    }

    public PlayTime(Map<String, Long> ticks) {
        this.ticks = new HashMap<>(ticks);
    }

    public static PlayTime get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public long ticks(String uuid) {
        return ticks.getOrDefault(uuid, 0L);
    }

    // A tick count as hours and minutes, such as "2h20m".
    public static String format(long ticks) {
        long minutes = ticks / 1200L;
        return (minutes / 60L) + "h" + (minutes % 60L) + "m";
    }

    // Once a second, credits each player who moved or looked around within the last five minutes.
    public static void tick(MinecraftServer server) {
        long now = server.getTicks();
        if (now % CHECK_INTERVAL != 0) {
            return;
        }

        PlayTime state = get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Seen previous = SEEN.get(player.getUuid());
            boolean moved = previous == null
                || previous.x != player.getX()
                || previous.y != player.getY()
                || previous.z != player.getZ()
                || previous.yaw != player.getYaw()
                || previous.pitch != player.getPitch();
            long activeAt = moved ? now : previous.activeAt;
            SEEN.put(player.getUuid(), new Seen(player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(), activeAt));
            if (now - activeAt <= IDLE_LIMIT) {
                state.ticks.merge(player.getUuid().toString(), (long) CHECK_INTERVAL, Long::sum);
                state.markDirty();
            }
        }
    }
}
