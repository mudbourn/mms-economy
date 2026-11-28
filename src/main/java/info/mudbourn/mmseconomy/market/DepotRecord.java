package info.mudbourn.mmseconomy.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

// One depot's saved state: who owns it, where it stands, and the shop it parks while it is not placed.
public final class DepotRecord {

    public static final Codec<DepotRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(record -> record.id),
        UUIDUtil.CODEC.fieldOf("owner").forGetter(record -> record.owner),
        Codec.STRING.fieldOf("ownerName").forGetter(record -> record.ownerName),
        Codec.STRING.optionalFieldOf("dimension").forGetter(record -> Optional.ofNullable(record.dimension)),
        BlockPos.CODEC.optionalFieldOf("pos").forGetter(record -> Optional.ofNullable(record.pos)),
        ShopListing.CODEC.listOf().optionalFieldOf("listings", List.of()).forGetter(record -> record.listings),
        Codec.STRING.optionalFieldOf("shopName", "").forGetter(record -> record.shopName),
        ItemStack.CODEC.listOf().optionalFieldOf("stock", List.of()).forGetter(record -> record.stock),
        Codec.BOOL.optionalFieldOf("lost", false).forGetter(record -> record.lost)
    ).apply(instance, DepotRecord::new));

    public final UUID id;

    public final UUID owner;

    public String ownerName;

    public String dimension;

    public BlockPos pos;

    public final List<ShopListing> listings;

    public String shopName;

    public final List<ItemStack> stock;

    public boolean lost;

    public DepotRecord(UUID id, UUID owner, String ownerName) {
        this(id, owner, ownerName, Optional.empty(), Optional.empty(), List.of(), "", List.of(), false);
    }

    private DepotRecord(UUID id, UUID owner, String ownerName, Optional<String> dimension, Optional<BlockPos> pos,
                        List<ShopListing> listings, String shopName, List<ItemStack> stock, boolean lost) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.dimension = dimension.orElse(null);
        this.pos = pos.orElse(null);
        this.listings = new ArrayList<>(listings);
        this.shopName = shopName;
        this.stock = new ArrayList<>(stock);
        this.lost = lost;
    }

    // True while the depot is not standing in any world.
    public boolean parked() {
        return pos == null;
    }
}
