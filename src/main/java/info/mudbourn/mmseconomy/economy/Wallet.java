package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.component.ModComponents;
import info.mudbourn.mmseconomy.component.WalletComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

// Central server-authoritative access to player wallet balances, all amounts in Pice.
public final class Wallet {

    private Wallet() {
    }

    public static WalletComponent get(PlayerEntity player) {
        return ModComponents.WALLET.get(player);
    }

    public static long balance(PlayerEntity player) {
        return get(player).getBalance();
    }

    public static boolean canAfford(PlayerEntity player, long pice) {
        return pice >= 0 && balance(player) >= pice;
    }

    // Adds pice to the wallet, clamped to the configured cap, then syncs the owner.
    public static void deposit(ServerPlayerEntity player, long pice) {
        if (pice <= 0) {
            return;
        }
        WalletComponent wallet = get(player);
        long capped = cap(wallet.getBalance() + pice);
        wallet.setBalance(capped);
        ModComponents.WALLET.sync(player);
    }

    // Removes pice if affordable, returning false and leaving the balance untouched otherwise.
    public static boolean withdraw(ServerPlayerEntity player, long pice) {
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
    public static boolean transfer(ServerPlayerEntity from, ServerPlayerEntity to, long pice) {
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
