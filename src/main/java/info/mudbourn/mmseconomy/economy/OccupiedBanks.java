package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.List;

// The saved list of bank blocks currently flagged occupied, so a restart can unlight tellers no one is using.
public final class OccupiedBanks extends SavedData {

    public record Entry(String dimension, BlockPos pos) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos)
        ).apply(instance, Entry::new));
    }

    public static final Codec<OccupiedBanks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Entry.CODEC.listOf().fieldOf("entries").forGetter(state -> state.entries)
    ).apply(instance, OccupiedBanks::new));

    public static final SavedDataType<OccupiedBanks> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("mms_economy_occupied_banks"),
        OccupiedBanks::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final List<Entry> entries;

    public OccupiedBanks() {
        this.entries = new ArrayList<>();
    }

    public OccupiedBanks(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public static OccupiedBanks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> view() {
        return new ArrayList<>(entries);
    }

    public void add(String dimension, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        boolean present = entries.stream()
            .anyMatch(entry -> entry.dimension().equals(dimension) && entry.pos().equals(immutable));
        if (present) {
            return;
        }
        entries.add(new Entry(dimension, immutable));
        setDirty();
    }

    public void remove(String dimension, BlockPos pos) {
        if (entries.removeIf(entry -> entry.dimension().equals(dimension) && entry.pos().equals(pos))) {
            setDirty();
        }
    }

    public void clearAll() {
        if (!entries.isEmpty()) {
            entries.clear();
            setDirty();
        }
    }
}
