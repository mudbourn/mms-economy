package info.mudbourn.mmseconomy.block;

import info.mudbourn.mmseconomy.market.DepotShops;
import info.mudbourn.mmseconomy.registry.ModBlockEntities;
import info.mudbourn.mmseconomy.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// The data of a depot: its id, its owner and the four chest-sized containers behind its four barrels.
public class DepotBlockEntity extends BlockEntity {

    public static final int PARTS = 4;

    public static final int SLOTS = 27;

    private final List<SimpleContainer> containers = new ArrayList<>(PARTS);

    private UUID depotId;

    private UUID owner;

    private String ownerName = "";

    private boolean lostOnBreak;

    public DepotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEPOT, pos, state);
        for (int part = 0; part < PARTS; part++) {
            containers.add(new SimpleContainer(SLOTS) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    DepotBlockEntity.this.setChanged();
                }

                @Override
                public boolean stillValid(Player player) {
                    return Container.stillValidBlockEntity(DepotBlockEntity.this, player);
                }
            });
        }
    }

    public void bind(UUID depotId, UUID owner, String ownerName) {
        this.depotId = depotId;
        this.owner = owner;
        this.ownerName = ownerName;
        setChanged();
    }

    public UUID depotId() {
        return depotId;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void markLostOnBreak() {
        lostOnBreak = true;
    }

    public Container container(int part) {
        return containers.get(part);
    }

    public List<Container> containers() {
        return new ArrayList<>(containers);
    }

    // Removes and returns every stack the depot holds.
    public List<ItemStack> drainStock() {
        List<ItemStack> stock = new ArrayList<>();
        for (SimpleContainer container : containers) {
            stock.addAll(container.removeAllItems());
        }
        return stock;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server && depotId != null) {
            DepotShops.park(server, this, lostOnBreak);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (depotId != null) {
            builder.set(ModDataComponents.DEPOT_ID, depotId);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("DepotId", UUIDUtil.CODEC, depotId);
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
        output.putString("OwnerName", ownerName);
        for (int part = 0; part < PARTS; part++) {
            containers.get(part).storeAsItemList(output.list("Items" + part, ItemStack.CODEC));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        depotId = input.read("DepotId", UUIDUtil.CODEC).orElse(null);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("OwnerName", "");
        for (int part = 0; part < PARTS; part++) {
            containers.get(part).fromItemList(input.listOrEmpty("Items" + part, ItemStack.CODEC));
        }
    }
}
