package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

// The bond offerings investors buy from: Treasury ones made by admins and ones players issue.
public final class BondMarket {

    public static final String TREASURY = "Treasury";

    private BondMarket() {
    }

    // Puts a batch of bonds on sale; an empty issuer id means the Treasury.
    public static BondOffering issue(MinecraftServer server, String issuerId, String issuerName,
                                     long principal, int couponBps, int couponDays, int termDays,
                                     int lateBps, int count) {
        Obligations state = Obligations.get(server);
        BondOffering offering = new BondOffering(
            state.allocateId(),
            principal,
            Math.min(couponBps, Bonds.MAX_RATE_BPS),
            couponDays,
            termDays,
            count,
            issuerId,
            issuerName,
            lateBps);
        state.offerings.add(offering);
        state.markDirty();
        return offering;
    }

    // Removes an offering's unsold bonds; a non-null issuer id must match the offering's issuer.
    public static boolean cancel(MinecraftServer server, int id, String issuerId) {
        Obligations state = Obligations.get(server);
        BondOffering offering = state.offering(id);
        if (offering == null || (issuerId != null && !offering.issuer.equals(issuerId))) {
            return false;
        }
        state.offerings.remove(offering);
        state.markDirty();
        return true;
    }

    public static List<String> describe(MinecraftServer server) {
        return Obligations.get(server).offerings.stream()
            .map(offering -> "#" + offering.id + ": " + offering.issuerName + " "
                + Currency.format(offering.principal) + ", " + BondPaper.percent(offering.couponBps) + " "
                + Bonds.period(offering.couponDays) + ", term " + offering.termDays + " days, "
                + offering.remaining + " left")
            .toList();
    }

    // Sells one bond of an offering to the player for its principal, paid to the issuer.
    public static void buy(ServerPlayerEntity player, int id) {
        MinecraftServer server = player.getEntityWorld().getServer();
        Obligations state = Obligations.get(server);
        BondOffering offering = state.offering(id);
        if (offering == null) {
            player.sendMessage(Text.literal("No bond offering " + id + "."), false);
            return;
        }
        if (offering.issuer.equals(player.getUuid().toString())) {
            player.sendMessage(Text.literal("You cannot invest in your own bond."), false);
            return;
        }
        if (!Wallet.withdraw(player, offering.principal)) {
            player.sendMessage(Text.literal("You only have " + Currency.format(Wallet.balance(player)) + "."), false);
            return;
        }

        long day = History.currentDay(player);
        String label = offering.issuerName + " bond";
        payIssuer(server, offering, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offering.principal, 0L, offering.principal, player.getName().getString() + " bought your bond"));
        Bond bond = Bonds.issue(server, offering.issuer, offering.issuerName, offering.principal,
            offering.couponBps, offering.couponDays, offering.termDays, offering.lateBps);
        Bonds.give(player, bond);
        offering.remaining--;
        if (offering.remaining <= 0) {
            state.offerings.remove(offering);
        }
        state.markDirty();

        History.log(player, new HistoryEntry(day, HistoryCategory.DEAL, "", 0,
            offering.principal, 0L, -offering.principal, label));
        player.sendMessage(Text.literal("Invested " + Currency.format(offering.principal) + " in a "
            + label + "; it matures on day " + bond.maturityDay + "."), false);
    }

    // Credits a sold bond's principal to the Treasury or the issuing player, escrowed when they are offline.
    private static void payIssuer(MinecraftServer server, BondOffering offering, HistoryEntry received) {
        if (offering.byTreasury()) {
            Treasury.get(server).credit(offering.principal);
            return;
        }

        UUID issuer = UUID.fromString(offering.issuer);
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(issuer);
        if (online == null) {
            PendingPayouts.get(server).record(issuer, offering.principal, received);
            return;
        }
        Wallet.deposit(online, offering.principal);
        History.log(online, received);
        online.sendMessage(Text.literal(received.counterparty() + " for "
            + Currency.format(offering.principal) + "."), false);
    }
}
