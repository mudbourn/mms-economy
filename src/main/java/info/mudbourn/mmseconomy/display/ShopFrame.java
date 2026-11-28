package info.mudbourn.mmseconomy.display;

import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

// A glowing item frame that only its owner can use or break and that follows its depot.
public class ShopFrame extends ItemFrame implements ShopDisplay {

    private UUID owner;

    private UUID depot;

    public ShopFrame(EntityType<? extends ItemFrame> type, Level level) {
        super(type, level);
    }

    public ShopFrame(EntityType<? extends ItemFrame> type, Level level, BlockPos pos, Direction direction) {
        super(type, level, pos, direction);
    }

    public void bind(UUID owner, UUID depot) {
        this.owner = owner;
        this.depot = depot;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public UUID depot() {
        return depot;
    }

    @Override
    public DisplayKind kind() {
        return DisplayKind.FRAME;
    }

    @Override
    public List<ItemStack> takeContents() {
        ItemStack held = getItem();
        if (held.isEmpty()) {
            return List.of();
        }
        setItem(ItemStack.EMPTY);
        return List.of(held);
    }

    // True when the frame has room and a wall to hang on, checked once when it is placed.
    public boolean fits() {
        return super.survives();
    }

    @Override
    public boolean survives() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        Displays.upkeep(this);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (!Displays.mayManage(player, owner)) {
            EconomyNetworking.notice(player, "This frame is not yours.", true);
            return false;
        }
        kill(level, player);
        playSound(getBreakSound(), 1.0f, 1.0f);
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && Displays.mayManage(serverPlayer, owner)) {
            return super.interact(player, hand, location);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            EconomyNetworking.notice(serverPlayer, "This frame is not yours.", true);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    protected void onKilled() {
        super.onKilled();
        Displays.release(this);
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity breaker) {
        Displays.release(this);
    }

    @Override
    protected ItemStack getFrameItemStack() {
        return new ItemStack(ModItems.SHOP_FRAME);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, owner);
        output.storeNullable("Depot", UUIDUtil.CODEC, depot);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        depot = input.read("Depot", UUIDUtil.CODEC).orElse(null);
    }
}
