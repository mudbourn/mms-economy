package info.mudbourn.mmseconomy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmseconomy.display.DisplayKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

// Every depot by id, plus each owner's free display credits and the items waiting for them to log in.
public final class Depots extends SavedData {

    public static final Codec<Depots> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        DepotRecord.CODEC.listOf().fieldOf("records").forGetter(depots -> new ArrayList<>(depots.records.values())),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("frameCredits", Map.of())
            .forGetter(depots -> depots.frameCredits),
        Codec.unboundedMap(Codec.STRING, Codec.INT)
            .optionalFieldOf("mannequinCredits", Map.of())
            .forGetter(depots -> depots.mannequinCredits),
        Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf())
            .optionalFieldOf("pending", Map.of())
            .forGetter(depots -> depots.pending)
    ).apply(instance, Depots::new));

    public static final SavedDataType<Depots> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace("mms_economy_depots"),
        Depots::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final Map<UUID, DepotRecord> records = new LinkedHashMap<>();

    private final Map<String, Integer> frameCredits;

    private final Map<String, Integer> mannequinCredits;

    private final Map<String, List<ItemStack>> pending = new HashMap<>();

    public Depots() {
        this.frameCredits = new HashMap<>();
        this.mannequinCredits = new HashMap<>();
    }

    public Depots(List<DepotRecord> records, Map<String, Integer> frameCredits,
                  Map<String, Integer> mannequinCredits, Map<String, List<ItemStack>> pending) {
        records.forEach(record -> this.records.put(record.id, record));
        this.frameCredits = new HashMap<>(frameCredits);
        this.mannequinCredits = new HashMap<>(mannequinCredits);
        pending.forEach((owner, stacks) -> this.pending.put(owner, new ArrayList<>(stacks)));
    }

    public static Depots get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public DepotRecord find(UUID id) {
        return records.get(id);
    }

    public DepotRecord create(UUID id, UUID owner, String ownerName) {
        DepotRecord record = new DepotRecord(id, owner, ownerName);
        records.put(id, record);
        setDirty();
        return record;
    }

    public void remove(UUID id) {
        records.remove(id);
        setDirty();
    }

    public List<DepotRecord> ownedBy(UUID owner) {
        return records.values().stream().filter(record -> record.owner.equals(owner)).toList();
    }

    private Map<String, Integer> credits(DisplayKind kind) {
        return kind == DisplayKind.FRAME ? frameCredits : mannequinCredits;
    }

    public void addCredit(UUID owner, DisplayKind kind) {
        credits(kind).merge(owner.toString(), 1, Integer::sum);
        setDirty();
    }

    // Spends one free placement of the kind, returning false when the owner has none.
    public boolean useCredit(UUID owner, DisplayKind kind) {
        Map<String, Integer> map = credits(kind);
        int left = map.getOrDefault(owner.toString(), 0);
        if (left <= 0) {
            return false;
        }
        if (left == 1) {
            map.remove(owner.toString());
        } else {
            map.put(owner.toString(), left - 1);
        }
        setDirty();
        return true;
    }

    // Keeps a stack for an owner who is offline until their next join.
    public void hold(UUID owner, ItemStack stack) {
        pending.computeIfAbsent(owner.toString(), key -> new ArrayList<>()).add(stack);
        setDirty();
    }

    // Removes and returns everything held for the owner.
    public List<ItemStack> claim(UUID owner) {
        List<ItemStack> held = pending.remove(owner.toString());
        if (held == null) {
            return List.of();
        }
        setDirty();
        return held;
    }
}
