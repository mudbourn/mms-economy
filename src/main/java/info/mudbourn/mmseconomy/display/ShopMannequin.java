package info.mudbourn.mmseconomy.display;

import info.mudbourn.mmseconomy.network.EconomyNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

// A player-shaped display stand: click a body part to dress it, only the owner can change or break it.
public class ShopMannequin extends ArmorStand implements ShopDisplay {

    private static final double HEAD_Y = 1.35;

    private static final double TORSO_Y = 0.675;

    private static final double FEET_Y = 0.34;

    private static final double ARM_OFFSET = 0.25;

    private UUID owner;

    private UUID depot;

    public ShopMannequin(EntityType<? extends ArmorStand> type, Level level) {
        super(type, level);
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
        return DisplayKind.MANNEQUIN;
    }

    @Override
    public List<ItemStack> takeContents() {
        List<ItemStack> held = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = getItemBySlot(slot);
            if (canUseSlot(slot) && !stack.isEmpty()) {
                held.add(stack.copy());
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        return held;
    }

    @Override
    public void tick() {
        super.tick();
        Displays.upkeep(this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            kill(level, source.getEntity());
            return true;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        if (!Displays.mayManage(player, owner)) {
            EconomyNetworking.notice(player, "This mannequin is not yours.", true);
            return false;
        }
        kill(level, player);
        playSound(SoundEvents.ARMOR_STAND_BREAK, 1.0f, 1.0f);
        return true;
    }

    @Override
    public void kill(ServerLevel level, Entity killer) {
        Displays.release(this);
        super.kill(level, killer);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (!Displays.mayManage(serverPlayer, owner)) {
            EconomyNetworking.notice(serverPlayer, "This mannequin is not yours.", true);
            return InteractionResult.FAIL;
        }
        EquipmentSlot slot = clickedSlot(location);
        ItemStack held = player.getItemInHand(hand);
        ItemStack current = getItemBySlot(slot);
        if (held.isEmpty()) {
            if (!current.isEmpty()) {
                player.setItemInHand(hand, current);
                setItemSlot(slot, ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        if (!accepts(slot, held)) {
            EconomyNetworking.notice(serverPlayer, "That does not go there.", true);
            return InteractionResult.SUCCESS;
        }
        setItemSlot(slot, held.copyWithCount(1));
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (!current.isEmpty()) {
            player.getInventory().placeItemBackInInventory(current, Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }

    // The body part at a click point given relative to the mannequin's feet, in world axes.
    private EquipmentSlot clickedSlot(Vec3 location) {
        double yaw = Math.toRadians(getYRot());
        double left = location.x * Math.cos(yaw) + location.z * Math.sin(yaw);
        if (location.y >= HEAD_Y) {
            return EquipmentSlot.HEAD;
        }
        if (location.y >= TORSO_Y) {
            if (Math.abs(left) <= ARM_OFFSET) {
                return EquipmentSlot.CHEST;
            }
            return left > 0 ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        }
        return location.y >= FEET_Y ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
    }

    private static boolean accepts(EquipmentSlot slot, ItemStack stack) {
        return switch (slot) {
            case MAINHAND -> true;
            case OFFHAND -> isWeapon(stack);
            default -> {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                yield equippable != null && equippable.slot() == slot;
            }
        };
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
            || stack.is(ItemTags.AXES)
            || stack.is(ItemTags.SPEARS)
            || stack.is(Items.TRIDENT)
            || stack.is(Items.MACE)
            || stack.is(Items.BOW)
            || stack.is(Items.CROSSBOW);
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
