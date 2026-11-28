package info.mudbourn.mmseconomy.display;

import info.mudbourn.mmseconomy.market.DepotRecord;
import info.mudbourn.mmseconomy.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// The item that places a shop frame or a shop mannequin next to a depot.
public class DisplayItem extends Item {

    private final DisplayKind kind;

    public DisplayItem(Properties properties, DisplayKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return kind == DisplayKind.FRAME ? placeFrame(context) : placeMannequin(context);
    }

    private InteractionResult placeFrame(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(face);
        ItemStack stack = context.getItemInHand();
        if (player == null || !player.mayUseItemAt(pos, face, stack)) {
            return InteractionResult.FAIL;
        }
        ShopFrame frame = new ShopFrame(ModEntities.SHOP_FRAME, level, pos, face);
        if (!frame.fits()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            DepotRecord depot = Displays.claim(serverPlayer, kind, pos);
            if (depot == null) {
                return InteractionResult.FAIL;
            }
            frame.bind(player.getUUID(), depot.id);
            frame.playPlacementSound();
            server.addFreshEntity(frame);
            consume(player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult placeMannequin(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        if (player == null || face == Direction.DOWN) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        ItemStack stack = context.getItemInHand();
        Vec3 base = Vec3.atBottomCenterOf(pos);
        AABB box = ModEntities.SHOP_MANNEQUIN.getDimensions().makeBoundingBox(base.x, base.y, base.z);
        if (!level.noCollision(null, box) || !level.getEntities((Entity) null, box, entity -> true).isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            DepotRecord depot = Displays.claim(serverPlayer, kind, pos);
            if (depot == null) {
                return InteractionResult.FAIL;
            }
            ShopMannequin mannequin = new ShopMannequin(ModEntities.SHOP_MANNEQUIN, level);
            float yaw = Mth.floor((Mth.wrapDegrees(player.getYRot() - 180.0f) + 22.5f) / 45.0f) * 45.0f;
            mannequin.snapTo(base.x, base.y, base.z, yaw, 0.0f);
            mannequin.bind(player.getUUID(), depot.id);
            server.addFreshEntity(mannequin);
            mannequin.playSound(SoundEvents.ARMOR_STAND_PLACE, 0.75f, 0.8f);
            consume(player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static void consume(Player player, ItemStack stack) {
        if (!player.isCreative()) {
            stack.shrink(1);
        }
    }
}
