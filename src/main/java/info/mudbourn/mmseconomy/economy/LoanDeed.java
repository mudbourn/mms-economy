package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;

// The loan deed item: the lender's record of a loan, carrying only the loan id.
public final class LoanDeed {

    private LoanDeed() {
    }

    public static ItemStack create(Loan loan) {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("loan", loan.id);

        ItemStack stack = new ItemStack(ModItems.LOAN_DEED);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Loan Deed: " + loan.borrowerName
            + " " + Currency.format(loan.principal)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Borrower: " + loan.borrowerName),
            Text.literal("Lender: " + loan.lenderName),
            Text.literal("Principal: " + Currency.format(loan.principal)),
            Text.literal("Interest: " + BondPaper.percent(loan.rateBps) + " " + Loans.period(loan.periodDays)),
            Text.literal("Repaid in " + loan.installments + " " + Loans.period(loan.periodDays) + " payments"),
            Text.literal("Late rate: " + BondPaper.percent(loan.lateBps) + " per day"),
            Text.literal("Time passes only while the borrower plays."),
            Text.literal("Use to check what is still owed."))));
        return stack;
    }

    // The loan id on a stack, or -1 when it is not a written deed.
    public static int read(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (!stack.isOf(ModItems.LOAN_DEED) || data == null) {
            return -1;
        }
        return data.copyNbt().getInt("loan", -1);
    }
}
