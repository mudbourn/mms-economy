package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.component.ModComponents;
import info.mudbourn.mmseconomy.component.WalletComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

// Central server-authoritative access to player wallet balances, all amounts in Pice.
public final class Wallet {

    private Wallet() {
    }

    public static WalletComponent get(Player player) {
        return ModComponents.WALLET.get(player);
    }

    public static long balance(Player player) {
        return get(player).getBalance();
    }

    public static boolean canAfford(Player player, long pice) {
        return pice >= 0 && balance(player) >= pice;
    }

    // Adds pice to the wallet, clamped to the configured cap, then syncs the owner.
    public static void deposit(ServerPlayer player, long pice) {
        if (pice <= 0) {
            return;
        }
        WalletComponent wallet = get(player);
        long capped = cap(wallet.getBalance() + pice);
        wallet.setBalance(capped);
        ModComponents.WALLET.sync(player);
    }

    // How much more the wallet can take before the cap, or Long.MAX_VALUE with no cap.
    public static long headroom(Player player) {
        long max = MmsEconomy.config().maxBalance;
        return max > 0 ? Math.max(0L, max - balance(player)) : Long.MAX_VALUE;
    }

    // Removes pice if affordable, returning false and leaving the balance untouched otherwise.
    public static boolean withdraw(ServerPlayer player, long pice) {
        if (pice <= 0) {
            return false;
        }
        WalletComponent wallet = get(player);
        if (wallet.getBalance() < pice) {
            return false;
        }
        wallet.setBalance(wallet.getBalance() - pice);
        ModComponents.WALLET.sync(player);
        return true;
    }

    // Moves pice from one wallet to another with no tax, rolling back on failure.
    public static boolean transfer(ServerPlayer from, ServerPlayer to, long pice) {
        if (pice <= 0 || !withdraw(from, pice)) {
            return false;
        }
        deposit(to, pice);
        return true;
    }

    private static long cap(long value) {
        long max = MmsEconomy.config().maxBalance;
        if (max > 0 && value > max) {
            return max;
        }
        return value;
    }
}
