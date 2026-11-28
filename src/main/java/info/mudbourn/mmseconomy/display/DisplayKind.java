package info.mudbourn.mmseconomy.display;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.registry.ModItems;
import net.minecraft.world.item.Item;

// The two shop display types and what each costs to place.
public enum DisplayKind {
    FRAME,
    MANNEQUIN;

    public long fee() {
        return this == FRAME
            ? MmsEconomy.config().displayFrameFeePice
            : MmsEconomy.config().displayMannequinFeePice;
    }

    public Item item() {
        return this == FRAME ? ModItems.SHOP_FRAME : ModItems.SHOP_MANNEQUIN;
    }
}
