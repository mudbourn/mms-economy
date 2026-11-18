package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.component.ModComponents;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.registry.ModItems;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

// The read-only wallet readout drawn above the inventory panel.
public final class WalletHud {

    private static final int PANEL_WIDTH = 176;

    private static final int PANEL_HEIGHT = 166;

    private WalletHud() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterExtract(screen).register(WalletHud::draw);
            }
        });
    }

    private static void draw(net.minecraft.client.gui.screens.Screen screen, GuiGraphicsExtractor context,
                             int mouseX, int mouseY, float tickDelta) {
        if (!MmsEconomy.config().walletHudEnabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !ModComponents.WALLET.isProvidedBy(player)) {
            return;
        }

        long balance = ModComponents.WALLET.get(player).getBalance();
        int left = (screen.width - PANEL_WIDTH) / 2;
        int top = (screen.height - PANEL_HEIGHT) / 2;

        context.item(new ItemStack(ModItems.COLT), left, top - 20);
        context.text(client.font, Currency.format(balance), left + 20, top - 16, 0xFFFFFFFF, true);
    }
}
