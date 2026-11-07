package info.mudbourn.mmseconomy.deal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

// Every wholesale offer and order, saved on the overworld.
public final class Wholesale extends PersistentState {

    public static final Codec<Wholesale> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("lastDay").forGetter(state -> state.lastDay),
        Codec.INT.fieldOf("nextId").forGetter(state -> state.nextId),
        lenient(WholesaleOffer.CODEC, (raw, error) -> { }).fieldOf("offers").forGetter(state -> state.offers),
        lenient(WholesaleOrder.CODEC, Wholesale::loseOrder).fieldOf("orders").forGetter(state -> state.orders)
    ).apply(instance, Wholesale::new));

    public static final PersistentStateType<Wholesale> TYPE = new PersistentStateType<>(
        "mms_economy_wholesale",
        Wholesale::new,
        CODEC,
        DataFixTypes.LEVEL);

    // Escrow of orders that could not be read back, waiting to be refunded by the next tick.
    private static final List<Lost> LOST = new ArrayList<>();

    // An unreadable order's buyer and the escrow owed back to them.
    public record Lost(UUID buyer, long escrow) {
    }

    public long lastDay;

    private int nextId;

    public final List<WholesaleOffer> offers;

    public final List<WholesaleOrder> orders;

    public Wholesale() {
        this(-1L, 1, List.of(), List.of());
    }

    public Wholesale(long lastDay, int nextId, List<WholesaleOffer> offers, List<WholesaleOrder> orders) {
        this.lastDay = lastDay;
        this.nextId = nextId;
        this.offers = new ArrayList<>(offers);
        this.orders = new ArrayList<>(orders);
    }

    // A list codec that drops entries it cannot decode instead of failing the whole list.
    private static <T> Codec<List<T>> lenient(Codec<T> element, BiConsumer<Dynamic<?>, String> onBad) {
        Decoder<List<T>> decoder = new Decoder<>() {
            @Override
            public <O> DataResult<Pair<List<T>, O>> decode(DynamicOps<O> ops, O input) {
                return ops.getList(input).map(source -> {
                    List<T> out = new ArrayList<>();
                    source.accept(item -> {
                        DataResult<T> parsed = element.parse(ops, item);
                        if (parsed.result().isPresent()) {
                            out.add(parsed.result().get());
                        } else {
                            onBad.accept(new Dynamic<>(ops, item), parsed.error().map(e -> e.message()).orElse(""));
                        }
                    });
                    return Pair.of(out, input);
                });
            }
        };
        return Codec.of(element.listOf(), decoder);
    }

    // Logs an undecodable order and queues its escrow for refund when the buyer can still be read.
    private static void loseOrder(Dynamic<?> raw, String error) {
        UUID buyer = raw.get("buyer").read(Uuids.CODEC).result().orElse(null);
        long escrow = raw.get("escrow").asLong(0L);
        MmsEconomy.LOGGER.error("Dropping unreadable wholesale order ({}), refunding {} to {}", error, escrow, buyer);
        if (buyer != null && escrow > 0) {
            LOST.add(new Lost(buyer, escrow));
        }
    }

    // Removes and returns the escrow queued for refund.
    public static List<Lost> drainLost() {
        List<Lost> drained = new ArrayList<>(LOST);
        LOST.clear();
        return drained;
    }

    public static Wholesale get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // The next id shared by offers and orders.
    public int allocateId() {
        markDirty();
        return nextId++;
    }

    public WholesaleOffer offer(int id) {
        return offers.stream().filter(offer -> offer.id() == id).findFirst().orElse(null);
    }

    public WholesaleOrder order(int id) {
        return orders.stream().filter(order -> order.id == id).findFirst().orElse(null);
    }
}
