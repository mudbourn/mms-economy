package info.mudbourn.mmseconomy;

import info.mudbourn.mmseconomy.command.EconomyCommand;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import info.mudbourn.mmseconomy.economy.BankOccupancy;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.registry.ModBlocks;
import info.mudbourn.mmseconomy.registry.ModItemGroups;
import info.mudbourn.mmseconomy.registry.ModItems;
import info.mudbourn.mmseconomy.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import info.mudbourn.mmseconomy.deal.DealManager;
import info.mudbourn.mmseconomy.market.Marketplace;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MmsEconomy implements ModInitializer {

    public static final String MOD_ID = "mms_economy";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EconomyConfig config;

    @Override
    public void onInitialize() {
        config = EconomyConfig.load();
        info.mudbourn.mmseconomy.market.ServerBuyList.load();

        ModBlocks.register();
        ModItems.register();
        ModItemGroups.register();
        ModSounds.register();

        EconomyNetworking.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            EconomyCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(BankOccupancy::scrubOnLoad);

        ServerTickEvents.END_SERVER_TICK.register(DealManager::tickAll);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.economy.BankInterest::tick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            BankOccupancy.release(handler.player.getEntityWorld(), handler.player));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld serverWorld
                && player instanceof ServerPlayerEntity serverPlayer
                && serverWorld.getBlockState(hit.getBlockPos()).isOf(Blocks.BARREL)) {
                String owner = Marketplace.protectedOwner(serverPlayer, hit.getBlockPos());
                if (owner != null) {
                    serverPlayer.sendMessage(Text.literal("This depot belongs to " + owner + "."), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });

        LOGGER.info("mms-economy initialized");
    }

    public static EconomyConfig config() {
        return config;
    }
}
