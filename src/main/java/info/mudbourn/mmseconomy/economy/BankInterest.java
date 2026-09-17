package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

// The bank interest clock, saved on the overworld, that pays a percent of each stored balance every set number of in-game days.
public final class BankInterest extends PersistentState {

    public static final Codec<BankInterest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("lastDay").forGetter(state -> state.lastDay)
    ).apply(instance, BankInterest::new));

    public static final PersistentStateType<BankInterest> TYPE = new PersistentStateType<>(
        "mms_economy_bank_interest",
        BankInterest::new,
        CODEC,
        DataFixTypes.LEVEL);

    private long lastDay;

    public BankInterest() {
        this.lastDay = -1L;
    }

    public BankInterest(long lastDay) {
        this.lastDay = lastDay;
    }

    public static BankInterest get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // Pays interest to every online balance once each configured interval of in-game days has elapsed.
    public static void tick(MinecraftServer server) {
        EconomyConfig config = MmsEconomy.config();
        if (config.bankInterestDays <= 0 || config.bankInterestPercent <= 0) {
            return;
        }
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }
        long day = overworld.getTimeOfDay() / 24000L;
        BankInterest state = get(server);
        if (state.lastDay < 0L) {
            state.lastDay = day;
            state.markDirty();
            return;
        }
        if (day - state.lastDay < config.bankInterestDays) {
            return;
        }
        pay(server, config.bankInterestPercent);
        state.lastDay += config.bankInterestDays;
        state.markDirty();
    }

    // Credits each online player a percent of their stored balance.
    private static void pay(MinecraftServer server, int percent) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            long balance = Wallet.balance(player);
            long interest = balance / 100L * percent + balance % 100L * percent / 100L;
            if (interest <= 0L) {
                continue;
            }
            Wallet.deposit(player, interest);
            long day = info.mudbourn.mmseconomy.history.History.currentDay(player);
            info.mudbourn.mmseconomy.history.History.log(player,
                new info.mudbourn.mmseconomy.history.HistoryEntry(
                    day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
                    interest, 0L, interest, "Bank interest"));
            player.sendMessage(Text.literal("Bank interest: +" + Currency.format(interest)), false);
        }
    }
}
