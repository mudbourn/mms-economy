package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

// The bond item: it carries only the bond id, and its tooltip is written at creation.
public final class BondPaper {

    private BondPaper() {
    }

    public static ItemStack create(Bond bond) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("bond", bond.id);

        ItemStack stack = new ItemStack(ModItems.BOND);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Bond: " + bond.issuerName
            + " " + Currency.format(bond.principal)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Issuer: " + bond.issuerName),
            Component.literal("Principal: " + Currency.format(bond.principal)),
            Component.literal("Coupon: " + percent(bond.couponBps) + " " + Bonds.period(bond.couponDays)),
            Component.literal("Matures on day " + bond.maturityDay),
            Component.literal("Late rate: " + percent(bond.lateBps) + " per day"),
            Component.literal("Pays " + Bonds.period(bond.couponDays) + " to an active vault holder."),
            Component.literal("Use at a bank to store it in your vault."))));
        return stack;
    }

    // The bond id on a stack, or null when it is not a bond paper.
    public static String read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(ModItems.BOND) || data == null) {
            return null;
        }
        String id = data.copyTag().getStringOr("bond", "");
        return id.isEmpty() ? null : id;
    }

    // Basis points as a percent such as "2.5%".
    public static String percent(int bps) {
        return bps % 100 == 0 ? (bps / 100) + "%" : (bps / 100.0) + "%";
    }
}
