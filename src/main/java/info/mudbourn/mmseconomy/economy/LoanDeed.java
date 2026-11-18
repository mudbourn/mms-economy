package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

// The loan deed item: the lender's record of a loan, carrying only the loan id.
public final class LoanDeed {

    private LoanDeed() {
    }

    public static ItemStack create(Loan loan) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("loan", loan.id);

        ItemStack stack = new ItemStack(ModItems.LOAN_DEED);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Loan Deed: " + loan.borrowerName
            + " " + Currency.format(loan.principal)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Borrower: " + loan.borrowerName),
            Component.literal("Lender: " + loan.lenderName),
            Component.literal("Principal: " + Currency.format(loan.principal)),
            Component.literal("Interest: " + BondPaper.percent(loan.rateBps) + " " + Loans.period(loan.periodDays)),
            Component.literal("Repaid in " + loan.installments + " " + Loans.period(loan.periodDays) + " payments"),
            Component.literal("Late rate: " + BondPaper.percent(loan.lateBps) + " per day"),
            Component.literal("Time passes only while the borrower plays."),
            Component.literal("Use to check what is still owed."))));
        return stack;
    }

    // The loan id on a stack, or -1 when it is not a written deed.
    public static int read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (!stack.is(ModItems.LOAN_DEED) || data == null) {
            return -1;
        }
        return data.copyTag().getIntOr("loan", -1);
    }
}
