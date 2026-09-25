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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The global registry of shop listings, saved on the overworld so buyers can browse while owners are offline.
public final class Market extends PersistentState {

    public static final Codec<Market> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ShopListing.CODEC.listOf().fieldOf("listings").forGetter(market -> market.listings),
        Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .optionalFieldOf("shopNames", Map.of())
            .forGetter(market -> market.shopNames)
    ).apply(instance, Market::new));

    public static final PersistentStateType<Market> TYPE = new PersistentStateType<>(
        "mms_economy_market",
        Market::new,
        CODEC,
        DataFixTypes.LEVEL);

    private final List<ShopListing> listings;

    // Owner-chosen shop names keyed by dimension and depot anchor.
    private final Map<String, String> shopNames;

    public Market() {
        this.listings = new ArrayList<>();
        this.shopNames = new HashMap<>();
    }

    public Market(List<ShopListing> listings, Map<String, String> shopNames) {
        this.listings = new ArrayList<>(listings);
        this.shopNames = new HashMap<>(shopNames);
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

    private static String shopKey(String dimension, BlockPos depot) {
        return dimension + "|" + depot.getX() + "," + depot.getY() + "," + depot.getZ();
    }

    // The shop's custom name, or an empty string when the owner has not named it.
    public String shopName(String dimension, BlockPos depot) {
        return shopNames.getOrDefault(shopKey(dimension, depot), "");
    }

    // Sets or, with a blank name, clears a shop's custom name.
    public void renameShop(String dimension, BlockPos depot, String name) {
        if (name.isBlank()) {
            shopNames.remove(shopKey(dimension, depot));
        } else {
            shopNames.put(shopKey(dimension, depot), name);
        }
        markDirty();
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
