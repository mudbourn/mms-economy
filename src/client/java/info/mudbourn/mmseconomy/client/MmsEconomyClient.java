package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.client.screen.HistoryScreen;
import info.mudbourn.mmseconomy.client.screen.HubScreen;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MmsEconomyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WalletHud.register();

        ClientPlayNetworking.registerGlobalReceiver(EconomyNetworking.OpenHub.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof HubScreen hub) {
                    hub.apply(payload);
                } else {
                    context.client().setScreen(new HubScreen(payload));
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(EconomyNetworking.OpenHistory.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().currentScreen instanceof HubScreen hub) {
                    hub.applyHistory(payload.purchases(), payload.payments(), payload.deals());
                } else {
                    context.client().setScreen(new HistoryScreen(
                        payload.purchases(),
                        payload.payments(),
                        payload.deals()));
                }
            }));
    }
}
