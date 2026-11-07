package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

// Treasury bond offerings that admins create and players buy.
public final class TreasuryBonds {

    private TreasuryBonds() {
    }

    public static BondOffering issue(MinecraftServer server, long principal, int couponBps, int couponDays,
                                     int termDays, int count) {
        Obligations state = Obligations.get(server);
        BondOffering offering = new BondOffering(state.allocateId(), principal, Math.min(couponBps, 1000), couponDays,
            termDays, count);
        state.offerings.add(offering);
        state.markDirty();
        return offering;
    }

    // Removes an offering's unsold bonds, returning false for an unknown id.
    public static boolean cancel(MinecraftServer server, int id) {
        Obligations state = Obligations.get(server);
        BondOffering offering = state.offering(id);
        if (offering == null) {
            return false;
        }
        state.offerings.remove(offering);
        state.markDirty();
        return true;
    }

    public static List<String> describe(MinecraftServer server) {
        return Obligations.get(server).offerings.stream()
            .map(offering -> "#" + offering.id + ": " + Currency.format(offering.principal) + ", "
                + BondPaper.percent(offering.couponBps) + " every " + offering.couponDays + " days, term "
                + offering.termDays + " days, " + offering.remaining + " left")
            .toList();
    }

    // Sells one bond of an offering to the player for its principal, paid into the Treasury.
    public static void buy(ServerPlayerEntity player, int id) {
        MinecraftServer server = player.getEntityWorld().getServer();
        Obligations state = Obligations.get(server);
        BondOffering offering = state.offering(id);
        if (offering == null) {
            player.sendMessage(Text.literal("No bond offering " + id + "."), false);
            return;
        }
        if (!Wallet.withdraw(player, offering.principal)) {
            player.sendMessage(Text.literal("You only have " + Currency.format(Wallet.balance(player)) + "."), false);
            return;
        }

        Treasury.get(server).credit(offering.principal);
        Bond bond = Bonds.issue(server, "", "Treasury", offering.principal, offering.couponBps,
            offering.couponDays, offering.termDays, 0);
        Bonds.give(player, bond);
        offering.remaining--;
        if (offering.remaining <= 0) {
            state.offerings.remove(offering);
        }
        state.markDirty();

        History.log(player, new HistoryEntry(History.currentDay(player), HistoryCategory.DEAL, "", 0,
            offering.principal, 0L, -offering.principal, "Treasury bond"));
        player.sendMessage(Text.literal("Bought a Treasury bond for " + Currency.format(offering.principal)
            + "; it matures on day " + bond.maturityDay + "."), false);
    }
}
