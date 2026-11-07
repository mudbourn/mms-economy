package info.mudbourn.mmseconomy.deal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Uuids;

import java.util.UUID;

// A buyer's order against an offer, holding the escrowed payment and, once accepted, the goods still in transit.
public final class WholesaleOrder {

    public static final Codec<WholesaleOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("id").forGetter(order -> order.id),
        Uuids.CODEC.fieldOf("seller").forGetter(order -> order.seller),
        Codec.STRING.fieldOf("sellerName").forGetter(order -> order.sellerName),
        Spot.CODEC.fieldOf("sellerDepot").forGetter(order -> order.sellerDepot),
        ItemStack.CODEC.fieldOf("stack").forGetter(order -> order.stack),
        Codec.INT.fieldOf("lotSize").forGetter(order -> order.lotSize),
        Codec.LONG.fieldOf("lotPrice").forGetter(order -> order.lotPrice),
        Uuids.CODEC.fieldOf("buyer").forGetter(order -> order.buyer),
        Codec.STRING.fieldOf("buyerName").forGetter(order -> order.buyerName),
        Spot.CODEC.fieldOf("buyerDepot").forGetter(order -> order.buyerDepot),
        Codec.INT.fieldOf("lots").forGetter(order -> order.lots),
        Codec.LONG.fieldOf("escrow").forGetter(order -> order.escrow),
        Codec.BOOL.fieldOf("inTransit").forGetter(order -> order.inTransit),
        Codec.LONG.fieldOf("created").forGetter(order -> order.created),
        Codec.INT.fieldOf("remaining").forGetter(order -> order.remaining),
        Codec.INT.fieldOf("offerId").forGetter(order -> order.offerId)
    ).apply(instance, WholesaleOrder::new));

    public final int id;

    public final UUID seller;

    public final String sellerName;

    public final Spot sellerDepot;

    public final ItemStack stack;

    public final int lotSize;

    public final long lotPrice;

    public final UUID buyer;

    public final String buyerName;

    // The buyer's depot that receives the goods, re-targeted when they lose it.
    public Spot buyerDepot;

    public final int lots;

    // Pice still held for the seller, paid out as the goods are delivered.
    public long escrow;

    public boolean inTransit;

    public final long created;

    // Items accepted but not yet delivered to the buyer's depot.
    public int remaining;

    // The wholesale offer this order came from, or 0 for a request against a shop listing.
    public final int offerId;

    public WholesaleOrder(int id, UUID seller, String sellerName, Spot sellerDepot, ItemStack stack,
                          int lotSize, long lotPrice, UUID buyer, String buyerName, Spot buyerDepot,
                          int lots, long escrow, boolean inTransit, long created, int remaining, int offerId) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.sellerDepot = sellerDepot;
        this.stack = stack;
        this.lotSize = lotSize;
        this.lotPrice = lotPrice;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.buyerDepot = buyerDepot;
        this.lots = lots;
        this.escrow = escrow;
        this.inTransit = inTransit;
        this.created = created;
        this.remaining = remaining;
        this.offerId = offerId;
    }

    public int items() {
        return lots * lotSize;
    }
}
