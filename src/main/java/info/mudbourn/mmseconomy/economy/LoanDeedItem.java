package info.mudbourn.mmseconomy.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

// The loan deed; using it reports what its loan still owes.
public final class LoanDeedItem extends Item {

    public LoanDeedItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide() && user instanceof ServerPlayer player) {
            Loans.inspect(player, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }
}
