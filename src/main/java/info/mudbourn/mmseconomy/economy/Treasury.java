package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

// The server-scoped tax reserve, one long in Pice, saved on the overworld.
public final class Treasury extends PersistentState {

    public static final Codec<Treasury> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("balance").forGetter(state -> state.balance)
    ).apply(instance, Treasury::new));

    public static final PersistentStateType<Treasury> TYPE = new PersistentStateType<>(
        "mms_economy_treasury",
        Treasury::new,
        CODEC,
        DataFixTypes.LEVEL);

    private long balance;

    public Treasury() {
        this.balance = 0L;
    }

    public Treasury(long balance) {
        this.balance = balance;
    }

    public static Treasury get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public long balance() {
        return balance;
    }

    public void credit(long pice) {
        if (pice <= 0) {
            return;
        }
        this.balance += pice;
        markDirty();
    }

    public boolean debit(long pice) {
        if (pice <= 0 || balance < pice) {
            return false;
        }
        this.balance -= pice;
        markDirty();
        return true;
    }
}
