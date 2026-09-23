package info.mudbourn.mmseconomy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// The global registry of shop listings, saved on the overworld so buyers can browse while owners are offline.
public final class Market extends PersistentState {

    public static final Codec<Market> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ShopListing.CODEC.listOf().fieldOf("listings").forGetter(market -> market.listings)
    ).apply(instance, Market::new));

    public static final PersistentStateType<Market> TYPE = new PersistentStateType<>(
        "mms_economy_market",
        Market::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final List<ShopListing> listings;

    public Market() {
        this.listings = new ArrayList<>();
    }

    public Market(List<ShopListing> listings) {
        this.listings = new ArrayList<>(listings);
    }

    public static Market get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // A read-only live view of every listing, in index order.
    public List<ShopListing> all() {
        return Collections.unmodifiableList(listings);
    }

    public boolean depotClaimedByOther(BlockPos depot, UUID owner) {
        return listings.stream()
            .anyMatch(listing -> listing.depot().equals(depot) && !listing.owner().equals(owner));
    }

    public boolean ownsAnyAt(BlockPos depot, UUID owner) {
        return listings.stream()
            .anyMatch(listing -> listing.depot().equals(depot) && listing.owner().equals(owner));
    }

    public void add(ShopListing listing) {
        listings.add(listing);
        markDirty();
    }

    public void removeAt(int index) {
        if (index >= 0 && index < listings.size()) {
            listings.remove(index);
            markDirty();
        }
    }

    // Drops every listing matching the predicate, returning how many were removed.
    public int removeIf(java.util.function.Predicate<ShopListing> predicate) {
        int before = listings.size();
        if (listings.removeIf(predicate)) {
            markDirty();
        }
        return before - listings.size();
    }
}
