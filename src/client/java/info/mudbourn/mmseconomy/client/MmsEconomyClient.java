package info.mudbourn.mmseconomy.client;

import com.mojang.blaze3d.platform.InputConstants;
import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.client.screen.HistoryScreen;
import info.mudbourn.mmseconomy.client.screen.HubScreen;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class MmsEconomyClient implements ClientModInitializer {

    // The hub keybind, also used by open screens to close themselves.
    public static KeyMapping openHub;

    @Override
    public void onInitializeClient() {
        WalletHud.register();

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
}
