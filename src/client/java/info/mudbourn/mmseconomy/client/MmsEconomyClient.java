package info.mudbourn.mmseconomy.client;

import com.mojang.blaze3d.platform.InputConstants;
import info.mudbourn.mmseconomy.client.screen.HistoryScreen;
import info.mudbourn.mmseconomy.client.screen.HubScreen;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.network.NoticePayload;
import info.mudbourn.mmseconomy.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MmsEconomyClient implements ClientModInitializer {

    // The hub keybind, also used by open screens to close themselves.
    public static KeyMapping openHub;

    @Override
    public void onInitializeClient() {
        WalletHud.register();

        ModelLayerRegistry.registerModelLayer(ShopMannequinRenderer.LAYER, ShopMannequinRenderer::createLayer);
        EntityRendererRegistry.register(ModEntities.SHOP_FRAME, ShopFrameRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHOP_MANNEQUIN, ShopMannequinRenderer::new);

        KeyMapping.Category category = mmsCategory();
        openHub = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.mms_economy.open_hub",
            InputConstants.KEY_SEMICOLON,
            category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHub.consumeClick()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new EconomyNetworking.RequestHub());
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(NoticePayload.TYPE, (payload, context) ->
            context.client().execute(() -> notice(context.client(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(EconomyNetworking.OpenHub.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof HubScreen hub) {
                    hub.apply(payload);
                } else {
                    context.client().gui.setScreen(new HubScreen(payload));
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(EconomyNetworking.OpenHistory.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof HubScreen hub) {
                    hub.applyHistory(
                        payload.purchases(),
                        payload.payments(),
                        payload.deals(),
                        payload.bills());
                } else {
                    context.client().gui.setScreen(new HistoryScreen(
                        payload.purchases(),
                        payload.payments(),
                        payload.deals(),
                        payload.bills()));
                }
            }));
    }
    // The shared MMS controls category. Every mms mod registers it, and the first one wins.
    private static KeyMapping.Category mmsCategory() {
        Identifier id = Identifier.fromNamespaceAndPath("mms", "main");
        try {
            return KeyMapping.Category.register(id);
        } catch (IllegalArgumentException alreadyRegistered) {
            return new KeyMapping.Category(id);
        }
    }

    // Shows a notice as an overlay message, or as a toast while a screen is open.
    private static void notice(Minecraft client, NoticePayload payload) {
        Component message = Component.literal(payload.message());
        if (client.gui.screen() == null) {
            client.player.sendOverlayMessage(message);
            return;
        }
        Component title = Component.translatable(payload.failed() ? "gui.mms_economy.notice_failed" : "gui.mms_economy.notice_done");
        SystemToast.addOrUpdate(client.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
    }
}
