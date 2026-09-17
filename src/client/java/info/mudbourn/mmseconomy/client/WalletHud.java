package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.component.ModComponents;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.registry.ModItems;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

// The read-only wallet readout drawn above the inventory panel.
public final class WalletHud {

    private static final int PANEL_WIDTH = 176;

    private static final int PANEL_HEIGHT = 166;

    private WalletHud() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register(WalletHud::draw);
            }
        });
    }

    private static void draw(net.minecraft.client.gui.screen.Screen screen, DrawContext context,
                             int mouseX, int mouseY, float tickDelta) {
        if (!MmsEconomy.config().walletHudEnabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || !ModComponents.WALLET.isProvidedBy(player)) {
            return;
        }

        long balance = ModComponents.WALLET.get(player).getBalance();
        int left = (screen.width - PANEL_WIDTH) / 2;
        int top = (screen.height - PANEL_HEIGHT) / 2;

        context.drawItem(new ItemStack(ModItems.COLT), left, top - 20);
        context.drawText(client.textRenderer, Currency.format(balance), left + 20, top - 16, 0xFFFFFFFF, true);
    }
}
