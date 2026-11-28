package info.mudbourn.mmseconomy;

import info.mudbourn.mmseconomy.command.EconomyCommand;
import info.mudbourn.mmseconomy.config.EconomyConfig;
import info.mudbourn.mmseconomy.economy.BankOccupancy;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.registry.ModBlockEntities;
import info.mudbourn.mmseconomy.registry.ModBlocks;
import info.mudbourn.mmseconomy.registry.ModDataComponents;
import info.mudbourn.mmseconomy.registry.ModEntities;
import info.mudbourn.mmseconomy.registry.ModItemGroups;
import info.mudbourn.mmseconomy.registry.ModItems;
import info.mudbourn.mmseconomy.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import info.mudbourn.mmseconomy.deal.WholesaleManager;
import info.mudbourn.mmseconomy.market.DepotShops;
import info.mudbourn.mmseconomy.market.Marketplace;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import info.mudbourn.mmseconomy.market.ShopBook;
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

        ModDataComponents.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModEntities.register();
        ModItems.register();
        ModItemGroups.register();
        ModSounds.register();

        EconomyNetworking.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            EconomyCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(BankOccupancy::scrubOnLoad);

        ServerTickEvents.END_SERVER_TICK.register(WholesaleManager::tick);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.economy.BankInterest::tick);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.economy.PlayTime::tick);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.economy.Billing::tick);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.economy.Loans::tick);

        ServerTickEvents.END_SERVER_TICK.register(info.mudbourn.mmseconomy.deal.OfferManager::tickAll);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            BankOccupancy.release(handler.player.level(), handler.player));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            info.mudbourn.mmseconomy.economy.PendingPayouts payouts =
                info.mudbourn.mmseconomy.economy.PendingPayouts.get(server);
            long owed = payouts.claim(player.getUUID());
            for (info.mudbourn.mmseconomy.history.HistoryEntry entry : payouts.claimHistory(player.getUUID())) {
                info.mudbourn.mmseconomy.history.History.log(player, entry);
            }
            if (owed <= 0) {
                return;
            }
            long paid = Math.min(owed, info.mudbourn.mmseconomy.economy.Wallet.headroom(player));
            info.mudbourn.mmseconomy.economy.Wallet.deposit(player, paid);
            payouts.hold(player.getUUID(), owed - paid);
            if (paid > 0) {
                EconomyNetworking.notice(
                    player,
                    "Paid while away: +" + info.mudbourn.mmseconomy.economy.Currency.format(paid),
                    false);
            }
            if (paid < owed) {
                EconomyNetworking.notice(
                    player,
                    "Your wallet is full. " + info.mudbourn.mmseconomy.economy.Currency.format(owed - paid)
                        + " is held until your next join.",
                    false);
            }
        });

        ServerPlayConnectionEvents.JOIN.register(info.mudbourn.mmseconomy.economy.Billing::onJoin);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            DepotShops.deliverPending(handler.player));

        ServerEntityEvents.ENTITY_UNLOAD.register(DepotShops::onEntityUnload);

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerLevel serverWorld
                && player instanceof ServerPlayer serverPlayer
                && serverWorld.getBlockState(hit.getBlockPos()).is(Blocks.BARREL)) {
                String owner = Marketplace.protectedOwner(serverPlayer, hit.getBlockPos());
                if (owner != null) {
                    EconomyNetworking.notice(serverPlayer, "This depot belongs to " + owner + ".", true);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerLevel serverWorld)
                || !(player instanceof ServerPlayer serverPlayer)
                || player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            BlockState state = serverWorld.getBlockState(hit.getBlockPos());
            if (!state.is(Blocks.LECTERN)
                || !state.getValue(LecternBlock.HAS_BOOK)
                || !(serverWorld.getBlockEntity(hit.getBlockPos()) instanceof LecternBlockEntity lectern)) {
                return InteractionResult.PASS;
            }
            ShopBook.Target target = ShopBook.read(lectern.getBook());
            if (target == null) {
                return InteractionResult.PASS;
            }
            EconomyNetworking.openShop(serverPlayer, target);
            return InteractionResult.SUCCESS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(state.is(Blocks.BARREL) || state.is(ModBlocks.DEPOT))) {
                return true;
            }
            String owner = Marketplace.protectedOwner(serverPlayer, pos);
            if (owner != null) {
                EconomyNetworking.notice(serverPlayer, "This depot belongs to " + owner + ".", true);
                return false;
            }
            if (state.is(ModBlocks.DEPOT)) {
                return true;
            }
            int closed = Marketplace.removeShopAtBrokenBarrel(serverPlayer, pos);
            if (closed > 0) {
                EconomyNetworking.notice(
                    serverPlayer,
                    "Breaking your depot closed " + closed + (closed == 1 ? " listing." : " listings."),
                    false);
            }
            return true;
        });

        LOGGER.info("mms-economy initialized");
    }

    public static EconomyConfig config() {
        return config;
    }
}
