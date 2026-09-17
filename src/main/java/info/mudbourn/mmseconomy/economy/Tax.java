package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigInteger;

// The single money-movement path for taxed transfers, so the rule cannot drift.
public final class Tax {

    private Tax() {
    }

    // The tax owed on an amount for a given kind, in Pice, floored.
    public static long on(long amount, TaxKind kind) {
        EconomyConfig config = MmsEconomy.config();
        if (!config.taxEnabled || amount <= 0) {
            return 0L;
        }

        int bps = switch (kind) {
            case PAY -> config.taxPayBps;
            case DEAL -> config.taxDealBps;
            case PURCHASE -> config.taxPurchaseBps;
        };
        if (bps <= 0) {
            return 0L;
        }

        return BigInteger.valueOf(amount)
            .multiply(BigInteger.valueOf(bps))
            .divide(BigInteger.valueOf(10000L))
            .longValue();
    }

    // Debits from by amount, credits the treasury the tax, credits to the net, or returns null when unaffordable.
    public static Settlement settleTaxed(ServerPlayerEntity from, ServerPlayerEntity to,
                                         long amount, TaxKind kind) {
        if (amount <= 0 || !Wallet.canAfford(from, amount)) {
            return null;
        }

        long tax = on(amount, kind);
        long net = amount - tax;
        if (!Wallet.withdraw(from, amount)) {
            return null;
        }

        MinecraftServer server = from.getEntityWorld().getServer();
        if (server != null && tax > 0) {
            Treasury.get(server).credit(tax);
        }
        Wallet.deposit(to, net);

        return new Settlement(amount, tax, net);
    }

    public record Settlement(long gross, long tax, long net) {
    }
}
