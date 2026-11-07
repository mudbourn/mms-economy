package info.mudbourn.mmseconomy.economy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

// The bond paper; using it redeems what the bond has accrued.
public final class BondItem extends Item {

    public BondItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            Bonds.redeem(player, player.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }
}
