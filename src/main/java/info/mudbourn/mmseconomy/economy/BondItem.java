package info.mudbourn.mmseconomy.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

// The bond paper; using it at a bank stores it in the vault, elsewhere it redeems what has accrued.
public final class BondItem extends Item {

    public BondItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (!world.isClientSide() && user instanceof ServerPlayer player) {
            Bonds.use(player, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }
}
