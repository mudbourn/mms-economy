package info.mudbourn.mmseconomy.economy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

// The loan deed; using it reports what its loan still owes.
public final class LoanDeedItem extends Item {

    public LoanDeedItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            Loans.inspect(player, player.getStackInHand(hand));
        }
        return ActionResult.SUCCESS;
    }
}
