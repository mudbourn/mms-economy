package info.mudbourn.mmseconomy.network;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.BankAccess;
import info.mudbourn.mmseconomy.economy.BankOccupancy;
import info.mudbourn.mmseconomy.economy.Gems;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.market.Depot;
import info.mudbourn.mmseconomy.market.Market;
import info.mudbourn.mmseconomy.market.Marketplace;
import info.mudbourn.mmseconomy.market.ServerBuyList;
import info.mudbourn.mmseconomy.market.ShopListing;
import info.mudbourn.mmseconomy.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// The economy hub and history packets: the server opens a screen, the client sends actions, the server re-validates them.
public final class EconomyNetworking {

    private EconomyNetworking() {
    }

    public record MarketRow(int index, String name, long price, String owner,
                            int x, int y, int z, boolean buyable) {
    }

    public record ShopRow(int index, String name, long price, int stock) {
    }

    public record BuyRow(String itemId, String name, long price) {
    }

    public record OpenHub(long balance, long pocket, int coltRatio, int interestPercent, int interestDays,
                          boolean hasBank, boolean bankOccupiedByOther, boolean nearDepot, int mode,
                          List<MarketRow> listings, List<ShopRow> myListings,
                          List<BuyRow> serverBuy) implements CustomPayload {
        public static final Id<OpenHub> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "open_hub"));
        public static final PacketCodec<RegistryByteBuf, OpenHub> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeLong(value.balance);
                buf.writeLong(value.pocket);
                buf.writeVarInt(value.coltRatio);
                buf.writeVarInt(value.interestPercent);
                buf.writeVarInt(value.interestDays);
                buf.writeBoolean(value.hasBank);
                buf.writeBoolean(value.bankOccupiedByOther);
                buf.writeBoolean(value.nearDepot);
                buf.writeVarInt(value.mode);
                buf.writeVarInt(value.listings.size());
                for (MarketRow row : value.listings) {
                    buf.writeVarInt(row.index());
                    buf.writeString(row.name());
                    buf.writeLong(row.price());
                    buf.writeString(row.owner());
                    buf.writeVarInt(row.x());
                    buf.writeVarInt(row.y());
                    buf.writeVarInt(row.z());
                    buf.writeBoolean(row.buyable());
                }
                buf.writeVarInt(value.myListings.size());
                for (ShopRow row : value.myListings) {
                    buf.writeVarInt(row.index());
                    buf.writeString(row.name());
                    buf.writeLong(row.price());
                    buf.writeVarInt(row.stock());
                }
                buf.writeVarInt(value.serverBuy.size());
                for (BuyRow row : value.serverBuy) {
                    buf.writeString(row.itemId());
                    buf.writeString(row.name());
                    buf.writeLong(row.price());
                }
            },
            buf -> {
                long balance = buf.readLong();
                long pocket = buf.readLong();
                int coltRatio = buf.readVarInt();
                int interestPercent = buf.readVarInt();
                int interestDays = buf.readVarInt();
                boolean hasBank = buf.readBoolean();
                boolean occupied = buf.readBoolean();
                boolean nearDepot = buf.readBoolean();
                int mode = buf.readVarInt();
                int listingCount = buf.readVarInt();
                List<MarketRow> listings = new ArrayList<>(listingCount);
                for (int i = 0; i < listingCount; i++) {
                    listings.add(new MarketRow(
                        buf.readVarInt(), buf.readString(), buf.readLong(), buf.readString(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
                }
                int myCount = buf.readVarInt();
                List<ShopRow> mine = new ArrayList<>(myCount);
                for (int i = 0; i < myCount; i++) {
                    mine.add(new ShopRow(buf.readVarInt(), buf.readString(), buf.readLong(), buf.readVarInt()));
                }
                int buyCount = buf.readVarInt();
                List<BuyRow> serverBuy = new ArrayList<>(buyCount);
                for (int i = 0; i < buyCount; i++) {
                    serverBuy.add(new BuyRow(buf.readString(), buf.readString(), buf.readLong()));
                }
                return new OpenHub(balance, pocket, coltRatio, interestPercent, interestDays,
                    hasBank, occupied, nearDepot, mode, listings, mine, serverBuy);
            });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record OpenHistory(List<HistoryEntry> purchases, List<HistoryEntry> payments,
                              List<HistoryEntry> deals) implements CustomPayload {
        public static final Id<OpenHistory> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "open_history"));
        public static final PacketCodec<RegistryByteBuf, OpenHistory> CODEC = PacketCodec.of(
            (value, buf) -> {
                HistoryEntry.LIST_CODEC.encode(buf, value.purchases);
                HistoryEntry.LIST_CODEC.encode(buf, value.payments);
                HistoryEntry.LIST_CODEC.encode(buf, value.deals);
            },
            buf -> new OpenHistory(
                HistoryEntry.LIST_CODEC.decode(buf),
                HistoryEntry.LIST_CODEC.decode(buf),
                HistoryEntry.LIST_CODEC.decode(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BankAction(boolean deposit, long amount) implements CustomPayload {
        public static final Id<BankAction> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "bank_action"));
        public static final PacketCodec<RegistryByteBuf, BankAction> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.deposit);
                buf.writeLong(value.amount);
            },
            buf -> new BankAction(buf.readBoolean(), buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BuyListing(int index) implements CustomPayload {
        public static final Id<BuyListing> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "buy_listing"));
        public static final PacketCodec<RegistryByteBuf, BuyListing> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new BuyListing(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ListHeld(long price) implements CustomPayload {
        public static final Id<ListHeld> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "list_held"));
        public static final PacketCodec<RegistryByteBuf, ListHeld> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeLong(value.price),
            buf -> new ListHeld(buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record Unlist(int index) implements CustomPayload {
        public static final Id<Unlist> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "unlist"));
        public static final PacketCodec<RegistryByteBuf, Unlist> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new Unlist(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerSell(long count) implements CustomPayload {
        public static final Id<ServerSell> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "server_sell"));
        public static final PacketCodec<RegistryByteBuf, ServerSell> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeLong(value.count),
            buf -> new ServerSell(buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestHistory() implements CustomPayload {
        public static final Id<RequestHistory> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "request_history"));
        public static final PacketCodec<RegistryByteBuf, RequestHistory> CODEC = PacketCodec.unit(new RequestHistory());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record CloseHub() implements CustomPayload {
        public static final Id<CloseHub> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "close_hub"));
        public static final PacketCodec<RegistryByteBuf, CloseHub> CODEC = PacketCodec.unit(new CloseHub());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenHub.ID, OpenHub.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenHistory.ID, OpenHistory.CODEC);
        PayloadTypeRegistry.playC2S().register(BankAction.ID, BankAction.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyListing.ID, BuyListing.CODEC);
        PayloadTypeRegistry.playC2S().register(ListHeld.ID, ListHeld.CODEC);
        PayloadTypeRegistry.playC2S().register(Unlist.ID, Unlist.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerSell.ID, ServerSell.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestHistory.ID, RequestHistory.CODEC);
        PayloadTypeRegistry.playC2S().register(CloseHub.ID, CloseHub.CODEC);

        registerAction(BankAction.ID, EconomyNetworking::handleBankAction);
        registerAction(BuyListing.ID, (player, payload) -> {
            Marketplace.buy(player, payload.index());
            refresh(player);
        });
        registerAction(ListHeld.ID, (player, payload) -> {
            Marketplace.list(player, payload.price());
            refresh(player);
        });
        registerAction(Unlist.ID, (player, payload) -> {
            Marketplace.unlist(player, payload.index());
            refresh(player);
        });
        registerAction(ServerSell.ID, (player, payload) -> {
            Marketplace.serverSell(player, (int) Math.min(Integer.MAX_VALUE, payload.count()));
            refresh(player);
        });
        registerAction(RequestHistory.ID, (player, payload) -> openHistory(player));
        registerAction(CloseHub.ID, (player, payload) -> handleClose(player));
    }

    private static <T extends CustomPayload> void registerAction(CustomPayload.Id<T> id,
                                                                 java.util.function.BiConsumer<ServerPlayerEntity, T> action) {
        ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getEntityWorld().getServer().execute(() -> action.accept(player, payload));
        });
    }

    public static void openHub(ServerPlayerEntity player, BlockPos bankPos) {
        ServerWorld world = player.getEntityWorld();
        boolean hasBank = bankPos != null;
        boolean occupiedByOther = false;
        if (hasBank) {
            occupiedByOther = BankOccupancy.isOccupiedByOther(bankPos, player);
            if (!occupiedByOther) {
                BankOccupancy.claim(world, bankPos, player);
                playAt(world, bankPos, ModSounds.BANK_OPEN);
            }
        }
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther));
    }

    private static void refresh(ServerPlayerEntity player) {
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        boolean hasBank = bank != null;
        boolean occupiedByOther = hasBank && BankOccupancy.isOccupiedByOther(bank, player);
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther));
    }

    private static OpenHub snapshot(ServerPlayerEntity player, boolean hasBank, boolean occupiedByOther) {
        ServerWorld world = player.getEntityWorld();
        MinecraftServer server = world.getServer();
        Market market = Market.get(server);
        List<ShopListing> all = market.all();
        String dimension = world.getRegistryKey().getValue().toString();
        int depotRange = MmsEconomy.config().depotRange;

        List<MarketRow> listings = new ArrayList<>();
        List<ShopRow> mine = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            ShopListing listing = all.get(i);
            String name = Marketplace.displayName(listing.item());
            boolean sameDim = listing.dimension().equals(dimension);
            boolean reachable = Marketplace.canReach(player, listing, server);
            boolean ownerOnline = server.getPlayerManager().getPlayer(listing.owner()) != null;
            int stock = sameDim ? Marketplace.stock(world, listing.depot(), listing.item()) : 0;
            boolean buyable = reachable && ownerOnline && stock > 0
                && !listing.owner().equals(player.getUuid());
            listings.add(new MarketRow(i, name, listing.price(), listing.ownerName(),
                listing.depot().getX(), listing.depot().getY(), listing.depot().getZ(), buyable));
            if (listing.owner().equals(player.getUuid())) {
                mine.add(new ShopRow(i, name, listing.price(),
                    Marketplace.stock(world, listing.depot(), listing.item())));
            }
        }

        BlockPos depot = Depot.detectNear(player, depotRange);
        boolean nearDepot = depot != null && !market.depotClaimedByOther(depot, player.getUuid());

        List<BuyRow> serverBuy = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ServerBuyList.entries().entrySet()) {
            serverBuy.add(new BuyRow(entry.getKey(), Marketplace.displayName(entry.getKey()), entry.getValue()));
        }

        boolean debug = info.mudbourn.mmseconomy.economy.DebugAccess.has(player);
        boolean effectiveBank = hasBank || debug;
        boolean effectiveDepot = nearDepot || debug;
        int mode = hasBank ? 2 : (nearDepot ? 1 : 0);
        return new OpenHub(Wallet.balance(player),
            info.mudbourn.mmseconomy.economy.Gems.inventoryValue(player),
            MmsEconomy.config().piceToColtRatio,
            MmsEconomy.config().bankInterestPercent,
            MmsEconomy.config().bankInterestDays,
            effectiveBank,
            occupiedByOther && !debug,
            effectiveDepot,
            mode,
            listings, mine, serverBuy);
    }

    public static void openHistory(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new OpenHistory(
            History.get(player, HistoryCategory.PURCHASE),
            History.get(player, HistoryCategory.PAYMENT),
            History.get(player, HistoryCategory.DEAL)));
    }

    private static void handleBankAction(ServerPlayerEntity player, BankAction action) {
        boolean debug = info.mudbourn.mmseconomy.economy.DebugAccess.has(player);
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        if (!debug && (bank == null || BankOccupancy.isOccupiedByOther(bank, player))) {
            player.sendMessage(Text.literal("This bank is not available."), false);
            return;
        }
        long amount = action.amount();
        if (amount <= 0) {
            return;
        }

        if (action.deposit()) {
            if (!Gems.removeExact(player, amount)) {
                player.sendMessage(Text.literal("You lack gems worth exactly that amount."), false);
                return;
            }
            Wallet.deposit(player, amount);
        } else {
            if (!Wallet.withdraw(player, amount)) {
                player.sendMessage(Text.literal("Your balance is too low."), false);
                return;
            }
            Gems.give(player, amount);
        }

        long day = info.mudbourn.mmseconomy.history.History.currentDay(player);
        info.mudbourn.mmseconomy.history.History.log(player,
            new info.mudbourn.mmseconomy.history.HistoryEntry(
                day, info.mudbourn.mmseconomy.history.HistoryCategory.PAYMENT, "", 0,
                amount, 0L, action.deposit() ? amount : -amount,
                action.deposit() ? "Bank deposit" : "Bank withdrawal"));

        refresh(player);
    }

    private static void handleClose(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        if (bank != null) {
            playAt(world, bank, ModSounds.BANK_CLOSE);
        }
        BankOccupancy.release(world, player);
    }

    private static void playAt(ServerWorld world, BlockPos pos, SoundEvent sound) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}
