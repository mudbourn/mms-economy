package info.mudbourn.mmseconomy.component;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class WalletComponentImpl implements WalletComponent {

    private static final String KEY_BALANCE = "balance";

    private final Player owner;

    private long balance;

    public WalletComponentImpl(Player owner) {
        this.owner = owner;
        this.balance = info.mudbourn.mmseconomy.MmsEconomy.config().startingBalance;
    }

    @Override
    public long getBalance() {
        return balance;
    }

    @Override
    public void setBalance(long pice) {
        this.balance = Math.max(0L, pice);
    }

    @Override
    public void readData(ValueInput view) {
        this.balance = view.getLongOr(KEY_BALANCE, 0L);
    }

    @Override
    public void writeData(ValueOutput view) {
        view.putLong(KEY_BALANCE, balance);
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeLong(balance);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        this.balance = buf.readLong();
    }
}
