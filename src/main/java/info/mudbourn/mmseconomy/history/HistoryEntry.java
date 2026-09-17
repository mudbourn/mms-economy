package info.mudbourn.mmseconomy.history;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

// One ledger row: when it happened, what moved, the money split, and the other party.
public record HistoryEntry(long day, HistoryCategory category, String item, int count,
                           long gross, long tax, long net, String counterparty) {

    public static final Codec<HistoryEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("day").forGetter(HistoryEntry::day),
        Codec.STRING.xmap(HistoryCategory::valueOf, HistoryCategory::name)
            .fieldOf("category").forGetter(HistoryEntry::category),
        Codec.STRING.fieldOf("item").forGetter(HistoryEntry::item),
        Codec.INT.fieldOf("count").forGetter(HistoryEntry::count),
        Codec.LONG.fieldOf("gross").forGetter(HistoryEntry::gross),
        Codec.LONG.fieldOf("tax").forGetter(HistoryEntry::tax),
        Codec.LONG.fieldOf("net").forGetter(HistoryEntry::net),
        Codec.STRING.fieldOf("counterparty").forGetter(HistoryEntry::counterparty)
    ).apply(instance, HistoryEntry::new));

    public static final PacketCodec<RegistryByteBuf, HistoryEntry> PACKET_CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarLong(value.day);
            buf.writeVarInt(value.category.ordinal());
            buf.writeString(value.item);
            buf.writeVarInt(value.count);
            buf.writeVarLong(value.gross);
            buf.writeVarLong(value.tax);
            buf.writeVarLong(value.net);
            buf.writeString(value.counterparty);
        },
        buf -> new HistoryEntry(
            buf.readVarLong(),
            HistoryCategory.values()[buf.readVarInt()],
            buf.readString(),
            buf.readVarInt(),
            buf.readVarLong(),
            buf.readVarLong(),
            buf.readVarLong(),
            buf.readString()));

    public static final PacketCodec<RegistryByteBuf, java.util.List<HistoryEntry>> LIST_CODEC =
        PACKET_CODEC.collect(PacketCodecs.toList());
}
