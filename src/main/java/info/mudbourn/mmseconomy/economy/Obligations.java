package info.mudbourn.mmseconomy.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.List;

// Every active bill, bond, debt and Treasury bond offering, saved on the overworld.
public final class Obligations extends PersistentState {

    public static final Codec<Obligations> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("lastDay").forGetter(state -> state.lastDay),
        Codec.INT.fieldOf("nextId").forGetter(state -> state.nextId),
        Bill.CODEC.listOf().fieldOf("bills").forGetter(state -> state.bills),
        Bond.CODEC.listOf().fieldOf("bonds").forGetter(state -> state.bonds),
        Debt.CODEC.listOf().fieldOf("debts").forGetter(state -> state.debts),
        BondOffering.CODEC.listOf().fieldOf("offerings").forGetter(state -> state.offerings)
    ).apply(instance, Obligations::new));

    public static final PersistentStateType<Obligations> TYPE = new PersistentStateType<>(
        "mms_economy_obligations",
        Obligations::new,
        CODEC,
        DataFixTypes.LEVEL);

    public long lastDay;

    private int nextId;

    public final List<Bill> bills;

    public final List<Bond> bonds;

    public final List<Debt> debts;

    public final List<BondOffering> offerings;

    public Obligations() {
        this(-1L, 1, List.of(), List.of(), List.of(), List.of());
    }

    public Obligations(long lastDay, int nextId, List<Bill> bills, List<Bond> bonds,
                       List<Debt> debts, List<BondOffering> offerings) {
        this.lastDay = lastDay;
        this.nextId = nextId;
        this.bills = new ArrayList<>(bills);
        this.bonds = new ArrayList<>(bonds);
        this.debts = new ArrayList<>(debts);
        this.offerings = new ArrayList<>(offerings);
    }

    public static Obligations get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    // The next id shared by bills, debts and offerings.
    public int allocateId() {
        markDirty();
        return nextId++;
    }

    public Bill bill(int id) {
        return bills.stream().filter(bill -> bill.id == id).findFirst().orElse(null);
    }

    public Bond bond(String id) {
        return bonds.stream().filter(bond -> bond.id.equals(id)).findFirst().orElse(null);
    }

    public BondOffering offering(int id) {
        return offerings.stream().filter(offering -> offering.id == id).findFirst().orElse(null);
    }

    public boolean hasDebtFor(Bond bond) {
        return debts.stream().anyMatch(debt -> debt.bond && debt.ref.equals(bond.id));
    }
}
