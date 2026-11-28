package info.mudbourn.mmseconomy.display;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

// A decoration entity bound to one owner and one depot.
public interface ShopDisplay {

    UUID owner();

    UUID depot();

    DisplayKind kind();

    // Removes and returns the items the display holds.
    List<ItemStack> takeContents();
}
