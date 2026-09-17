package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Map;
import java.util.TreeMap;

// The admin-only archive of every item sold to the server, keyed by item id to a running count, saved on the overworld.
public final class ServerLibrary extends PersistentState {

    public static final Codec<ServerLibrary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("items").forGetter(state -> state.items)
    ).apply(instance, ServerLibrary::new));

    public static final PersistentStateType<ServerLibrary> TYPE = new PersistentStateType<>(
        "mms_economy_server_library",
        ServerLibrary::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final Map<String, Long> items;

    public ServerLibrary() {
        this.items = new TreeMap<>();
    }

    public ServerLibrary(Map<String, Long> items) {
        this.items = new TreeMap<>(items);
    }

    public static ServerLibrary get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // The full archive, item id to stored count, in sorted order.
    public Map<String, Long> view() {
        return new TreeMap<>(items);
    }

    public long count(String itemId) {
        return items.getOrDefault(itemId, 0L);
    }

    // Adds count of an item to the archive.
    public void store(String itemId, long count) {
        if (count <= 0) {
            return;
        }
        items.merge(itemId, count, Long::sum);
        markDirty();
    }

    // Removes up to count of an item, returning how many were actually taken.
    public long take(String itemId, long count) {
        if (count <= 0) {
            return 0L;
        }
        long have = items.getOrDefault(itemId, 0L);
        long taken = Math.min(have, count);
        if (taken <= 0) {
            return 0L;
        }
        long left = have - taken;
        if (left > 0) {
            items.put(itemId, left);
        } else {
            items.remove(itemId);
        }
        markDirty();
        return taken;
    }
}
