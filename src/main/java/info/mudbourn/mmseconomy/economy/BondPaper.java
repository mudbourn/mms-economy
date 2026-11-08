package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;

// The bond item: it carries only the bond id, and its tooltip is written at creation.
public final class BondPaper {

    private BondPaper() {
    }

    public static ItemStack create(Bond bond) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("bond", bond.id);

        ItemStack stack = new ItemStack(ModItems.BOND);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Bond: " + bond.issuerName
            + " " + Currency.format(bond.principal)));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Issuer: " + bond.issuerName),
            Text.literal("Principal: " + Currency.format(bond.principal)),
            Text.literal("Coupon: " + percent(bond.couponBps) + " " + Bonds.period(bond.couponDays)),
            Text.literal("Matures on day " + bond.maturityDay),
            Text.literal("Late rate: " + percent(bond.lateBps) + " per day"),
            Text.literal("Pays " + Bonds.period(bond.couponDays) + " to an active vault holder."),
            Text.literal("Use at a bank to store it in your vault."))));
        return stack;
    }

    // The bond id on a stack, or null when it is not a bond paper.
    public static String read(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (!stack.isOf(ModItems.BOND) || data == null) {
            return null;
        }
        String id = data.copyNbt().getString("bond", "");
        return id.isEmpty() ? null : id;
    }

    // Basis points as a percent such as "2.5%".
    public static String percent(int bps) {
        return bps % 100 == 0 ? (bps / 100) + "%" : (bps / 100.0) + "%";
    }
}
