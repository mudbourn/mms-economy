package info.mudbourn.mmseconomy.component;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

public final class WalletComponentImpl implements WalletComponent {

    private static final String KEY_BALANCE = "balance";

    private final PlayerEntity owner;

    private long balance;

    public WalletComponentImpl(PlayerEntity owner) {
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
    public void readData(ReadView view) {
        this.balance = view.getLong(KEY_BALANCE, 0L);
    }

    @Override
    public void writeData(WriteView view) {
        view.putLong(KEY_BALANCE, balance);
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeLong(balance);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        this.balance = buf.readLong();
    }
}
