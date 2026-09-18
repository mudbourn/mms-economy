package info.mudbourn.mmseconomy.client;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.client.screen.HistoryScreen;
import info.mudbourn.mmseconomy.client.screen.HubScreen;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MmsEconomyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WalletHud.register();

        KeyBinding.Category category = KeyBinding.Category.create(
            Identifier.of(MmsEconomy.MOD_ID, "economy"));
        KeyBinding openHub = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mms_economy.open_hub",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON,
            category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHub.wasPressed()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new EconomyNetworking.RequestHub());
                }
            }
        });

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
