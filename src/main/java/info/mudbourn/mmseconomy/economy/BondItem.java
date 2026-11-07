package info.mudbourn.mmseconomy.economy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

// The bond paper; using it at a bank stores it in the vault, elsewhere it redeems what has accrued.
public final class BondItem extends Item {

    public BondItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            Bonds.use(player, player.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }
}
