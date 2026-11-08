package info.mudbourn.mmseconomy.network;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.deal.Spot;
import info.mudbourn.mmseconomy.deal.Wholesale;
import info.mudbourn.mmseconomy.deal.WholesaleManager;
import info.mudbourn.mmseconomy.deal.WholesaleOffer;
import info.mudbourn.mmseconomy.deal.WholesaleOrder;
import info.mudbourn.mmseconomy.economy.BankAccess;
import info.mudbourn.mmseconomy.economy.BankOccupancy;
import info.mudbourn.mmseconomy.economy.Bond;
import info.mudbourn.mmseconomy.economy.BondOffering;
import info.mudbourn.mmseconomy.economy.Bonds;
import info.mudbourn.mmseconomy.economy.Chips;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.DebugAccess;
import info.mudbourn.mmseconomy.economy.Gems;
import info.mudbourn.mmseconomy.economy.Obligations;
import info.mudbourn.mmseconomy.economy.TreasuryBonds;
import info.mudbourn.mmseconomy.economy.Wallet;
import info.mudbourn.mmseconomy.history.History;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.market.Depot;
import info.mudbourn.mmseconomy.market.Market;
import info.mudbourn.mmseconomy.market.Marketplace;
import info.mudbourn.mmseconomy.market.ServerBuyList;
import info.mudbourn.mmseconomy.market.ShopBook;
import info.mudbourn.mmseconomy.market.ShopListing;
import info.mudbourn.mmseconomy.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The economy hub and history packets: the server opens a screen, the client sends actions, the server re-validates them.
public final class EconomyNetworking {

    private EconomyNetworking() {
    }

    public record MarketRow(int index, String name, long price, String owner,
                            int x, int y, int z, boolean buyable, String shopName, ItemStack stack,
                            UUID ownerId, String dimension) {
    }

    public record ShopRow(int index, String name, long price, int stock,
                          BlockPos depot, String shopName, ItemStack stack) {
    }

    public record ServerShopRow(String itemId, String name, long sell, long buyPrice,
                                int stock, int category) {
    }

    public record BondOfferingRow(int id, long principal, int couponBps, int couponDays, int termDays,
                                  int remaining) {
    }

    public record VaultBondRow(int index, String text) {
    }

    public record WholesaleOfferRow(int id, ItemStack stack, String name, int lotSize, long lotPrice,
                                    String seller, boolean own) {
    }

    public record WholesaleOrderRow(int id, ItemStack stack, String name, int items, long escrow,
                                    String other, boolean incoming, boolean inTransit, int remaining) {
    }

    public record LibraryRow(String itemId, String name, long count, long bought) {
    }

    public record OpenHub(long balance, long pocket, int coltRatio, int interestPercent, int interestDays,
                          boolean hasBank, boolean bankOccupiedByOther, boolean nearDepot, int mode,
                          List<MarketRow> listings, List<ShopRow> myListings,
                          List<ServerShopRow> shopCatalog, boolean admin, long treasury,
                          List<LibraryRow> library, String focusOwner, BlockPos focusDepot,
                          List<BondOfferingRow> offerings, List<VaultBondRow> vault,
                          boolean wholesale, List<WholesaleOfferRow> wholesaleOffers,
                          List<WholesaleOrderRow> wholesaleOrders)
        implements CustomPayload {
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
                    buf.writeString(row.shopName());
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, row.stack());
                    buf.writeUuid(row.ownerId());
                    buf.writeString(row.dimension());
                }
                buf.writeVarInt(value.myListings.size());
                for (ShopRow row : value.myListings) {
                    buf.writeVarInt(row.index());
                    buf.writeString(row.name());
                    buf.writeLong(row.price());
                    buf.writeVarInt(row.stock());
                    buf.writeBlockPos(row.depot());
                    buf.writeString(row.shopName());
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, row.stack());
                }
                buf.writeVarInt(value.shopCatalog.size());
                for (ServerShopRow row : value.shopCatalog) {
                    buf.writeString(row.itemId());
                    buf.writeString(row.name());
                    buf.writeLong(row.sell());
                    buf.writeLong(row.buyPrice());
                    buf.writeVarInt(row.stock());
                    buf.writeVarInt(row.category());
                }
                buf.writeBoolean(value.admin);
                buf.writeLong(value.treasury);
                buf.writeVarInt(value.library.size());
                for (LibraryRow row : value.library) {
                    buf.writeString(row.itemId());
                    buf.writeString(row.name());
                    buf.writeLong(row.count());
                    buf.writeLong(row.bought());
                }
                buf.writeString(value.focusOwner);
                buf.writeBlockPos(value.focusDepot);
                buf.writeVarInt(value.offerings.size());
                for (BondOfferingRow row : value.offerings) {
                    buf.writeVarInt(row.id());
                    buf.writeLong(row.principal());
                    buf.writeVarInt(row.couponBps());
                    buf.writeVarInt(row.couponDays());
                    buf.writeVarInt(row.termDays());
                    buf.writeVarInt(row.remaining());
                }
                buf.writeVarInt(value.vault.size());
                for (VaultBondRow row : value.vault) {
                    buf.writeVarInt(row.index());
                    buf.writeString(row.text());
                }
                buf.writeBoolean(value.wholesale);
                buf.writeVarInt(value.wholesaleOffers.size());
                for (WholesaleOfferRow row : value.wholesaleOffers) {
                    buf.writeVarInt(row.id());
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, row.stack());
                    buf.writeString(row.name());
                    buf.writeVarInt(row.lotSize());
                    buf.writeLong(row.lotPrice());
                    buf.writeString(row.seller());
                    buf.writeBoolean(row.own());
                }
                buf.writeVarInt(value.wholesaleOrders.size());
                for (WholesaleOrderRow row : value.wholesaleOrders) {
                    buf.writeVarInt(row.id());
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, row.stack());
                    buf.writeString(row.name());
                    buf.writeVarInt(row.items());
                    buf.writeLong(row.escrow());
                    buf.writeString(row.other());
                    buf.writeBoolean(row.incoming());
                    buf.writeBoolean(row.inTransit());
                    buf.writeVarInt(row.remaining());
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
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readString(), ItemStack.OPTIONAL_PACKET_CODEC.decode(buf),
                        buf.readUuid(), buf.readString()));
                }
                int myCount = buf.readVarInt();
                List<ShopRow> mine = new ArrayList<>(myCount);
                for (int i = 0; i < myCount; i++) {
                    mine.add(new ShopRow(buf.readVarInt(), buf.readString(), buf.readLong(), buf.readVarInt(),
                        buf.readBlockPos(), buf.readString(), ItemStack.OPTIONAL_PACKET_CODEC.decode(buf)));
                }
                int catalogCount = buf.readVarInt();
                List<ServerShopRow> shopCatalog = new ArrayList<>(catalogCount);
                for (int i = 0; i < catalogCount; i++) {
                    shopCatalog.add(new ServerShopRow(buf.readString(), buf.readString(),
                        buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt()));
                }
                boolean admin = buf.readBoolean();
                long treasury = buf.readLong();
                int libraryCount = buf.readVarInt();
                List<LibraryRow> library = new ArrayList<>(libraryCount);
                for (int i = 0; i < libraryCount; i++) {
                    library.add(new LibraryRow(buf.readString(), buf.readString(),
                        buf.readLong(), buf.readLong()));
                }
                String focusOwner = buf.readString();
                BlockPos focusDepot = buf.readBlockPos();
                int offeringCount = buf.readVarInt();
                List<BondOfferingRow> offerings = new ArrayList<>(offeringCount);
                for (int i = 0; i < offeringCount; i++) {
                    offerings.add(new BondOfferingRow(buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                int vaultCount = buf.readVarInt();
                List<VaultBondRow> vault = new ArrayList<>(vaultCount);
                for (int i = 0; i < vaultCount; i++) {
                    vault.add(new VaultBondRow(buf.readVarInt(), buf.readString()));
                }
                boolean wholesale = buf.readBoolean();
                int wholesaleOfferCount = buf.readVarInt();
                List<WholesaleOfferRow> wholesaleOffers = new ArrayList<>(wholesaleOfferCount);
                for (int i = 0; i < wholesaleOfferCount; i++) {
                    wholesaleOffers.add(new WholesaleOfferRow(buf.readVarInt(),
                        ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readString(), buf.readVarInt(),
                        buf.readLong(), buf.readString(), buf.readBoolean()));
                }
                int wholesaleOrderCount = buf.readVarInt();
                List<WholesaleOrderRow> wholesaleOrders = new ArrayList<>(wholesaleOrderCount);
                for (int i = 0; i < wholesaleOrderCount; i++) {
                    wholesaleOrders.add(new WholesaleOrderRow(buf.readVarInt(),
                        ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readString(), buf.readVarInt(),
                        buf.readLong(), buf.readString(), buf.readBoolean(), buf.readBoolean(),
                        buf.readVarInt()));
                }
                return new OpenHub(balance, pocket, coltRatio, interestPercent, interestDays,
                    hasBank, occupied, nearDepot, mode, listings, mine, shopCatalog, admin, treasury, library,
                    focusOwner, focusDepot, offerings, vault, wholesale, wholesaleOffers, wholesaleOrders);
            });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record OpenHistory(List<HistoryEntry> purchases, List<HistoryEntry> payments,
                              List<HistoryEntry> deals, List<HistoryEntry> bills) implements CustomPayload {
        public static final Id<OpenHistory> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "open_history"));
        public static final PacketCodec<RegistryByteBuf, OpenHistory> CODEC = PacketCodec.of(
            (value, buf) -> {
                HistoryEntry.LIST_CODEC.encode(buf, value.purchases);
                HistoryEntry.LIST_CODEC.encode(buf, value.payments);
                HistoryEntry.LIST_CODEC.encode(buf, value.deals);
                HistoryEntry.LIST_CODEC.encode(buf, value.bills);
            },
            buf -> new OpenHistory(
                HistoryEntry.LIST_CODEC.decode(buf),
                HistoryEntry.LIST_CODEC.decode(buf),
                HistoryEntry.LIST_CODEC.decode(buf),
                HistoryEntry.LIST_CODEC.decode(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BankAction(boolean deposit, boolean chips, long amount) implements CustomPayload {
        public static final Id<BankAction> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "bank_action"));
        public static final PacketCodec<RegistryByteBuf, BankAction> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.deposit);
                buf.writeBoolean(value.chips);
                buf.writeLong(value.amount);
            },
            buf -> new BankAction(buf.readBoolean(), buf.readBoolean(), buf.readLong()));

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

    // A listing request; a null depot lists at the looked-at barrel wall instead of a chosen shop.
    public record ListHeld(long price, BlockPos depot) implements CustomPayload {
        public static final Id<ListHeld> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "list_held"));
        public static final PacketCodec<RegistryByteBuf, ListHeld> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeLong(value.price);
                buf.writeBoolean(value.depot != null);
                if (value.depot != null) {
                    buf.writeBlockPos(value.depot);
                }
            },
            buf -> new ListHeld(buf.readLong(), buf.readBoolean() ? buf.readBlockPos() : null));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RenameShop(BlockPos depot, String name) implements CustomPayload {
        public static final Id<RenameShop> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "rename_shop"));
        public static final PacketCodec<RegistryByteBuf, RenameShop> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBlockPos(value.depot);
                buf.writeString(value.name, Marketplace.MAX_SHOP_NAME);
            },
            buf -> new RenameShop(buf.readBlockPos(), buf.readString(Marketplace.MAX_SHOP_NAME)));

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

    public record ServerSell(String itemId, long count) implements CustomPayload {
        public static final Id<ServerSell> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "server_sell"));
        public static final PacketCodec<RegistryByteBuf, ServerSell> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.itemId);
                buf.writeLong(value.count);
            },
            buf -> new ServerSell(buf.readString(), buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BondBuy(int offeringId) implements CustomPayload {
        public static final Id<BondBuy> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "bond_buy"));
        public static final PacketCodec<RegistryByteBuf, BondBuy> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.offeringId),
            buf -> new BondBuy(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BondWithdraw(int index) implements CustomPayload {
        public static final Id<BondWithdraw> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "bond_withdraw"));
        public static final PacketCodec<RegistryByteBuf, BondWithdraw> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new BondWithdraw(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ServerBuy(String itemId, long count) implements CustomPayload {
        public static final Id<ServerBuy> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "server_buy"));
        public static final PacketCodec<RegistryByteBuf, ServerBuy> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.itemId);
                buf.writeLong(value.count);
            },
            buf -> new ServerBuy(buf.readString(), buf.readLong()));

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

    public record RequestHub() implements CustomPayload {
        public static final Id<RequestHub> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "request_hub"));
        public static final PacketCodec<RegistryByteBuf, RequestHub> CODEC = PacketCodec.unit(new RequestHub());

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

    public record RequestShopBook(String owner, BlockPos depot) implements CustomPayload {
        public static final Id<RequestShopBook> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "request_shop_book"));
        public static final PacketCodec<RegistryByteBuf, RequestShopBook> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.owner);
                buf.writeBlockPos(value.depot);
            },
            buf -> new RequestShopBook(buf.readString(), buf.readBlockPos()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record WholesaleAdd(int lotSize, long lotPrice) implements CustomPayload {
        public static final Id<WholesaleAdd> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "wholesale_add"));
        public static final PacketCodec<RegistryByteBuf, WholesaleAdd> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.lotSize);
                buf.writeLong(value.lotPrice);
            },
            buf -> new WholesaleAdd(buf.readVarInt(), buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record WholesaleRemove(int offerId) implements CustomPayload {
        public static final Id<WholesaleRemove> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "wholesale_remove"));
        public static final PacketCodec<RegistryByteBuf, WholesaleRemove> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.offerId),
            buf -> new WholesaleRemove(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record WholesaleOrderOffer(int offerId, int lots) implements CustomPayload {
        public static final Id<WholesaleOrderOffer> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "wholesale_order"));
        public static final PacketCodec<RegistryByteBuf, WholesaleOrderOffer> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.offerId);
                buf.writeVarInt(value.lots);
            },
            buf -> new WholesaleOrderOffer(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // A request against a shop listing, naming the listing by seller, depot, dimension and item.
    public record WholesaleRequest(UUID seller, BlockPos depot, String dimension, ItemStack stack,
                                   int quantity, long total) implements CustomPayload {
        public static final Id<WholesaleRequest> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "wholesale_request"));
        public static final PacketCodec<RegistryByteBuf, WholesaleRequest> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeUuid(value.seller);
                buf.writeBlockPos(value.depot);
                buf.writeString(value.dimension);
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, value.stack);
                buf.writeVarInt(value.quantity);
                buf.writeLong(value.total);
            },
            buf -> new WholesaleRequest(buf.readUuid(), buf.readBlockPos(), buf.readString(),
                ItemStack.OPTIONAL_PACKET_CODEC.decode(buf), buf.readVarInt(), buf.readLong()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Answers a wholesale order: the seller accepts or declines it, the buyer cancels it.
    public record WholesaleRespond(int orderId, int action) implements CustomPayload {
        public static final int ACCEPT = 0;

        public static final int DECLINE = 1;

        public static final int CANCEL = 2;

        public static final Id<WholesaleRespond> ID = new Id<>(Identifier.of(MmsEconomy.MOD_ID, "wholesale_respond"));
        public static final PacketCodec<RegistryByteBuf, WholesaleRespond> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.orderId);
                buf.writeVarInt(value.action);
            },
            buf -> new WholesaleRespond(buf.readVarInt(), buf.readVarInt()));

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
        PayloadTypeRegistry.playC2S().register(RenameShop.ID, RenameShop.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerSell.ID, ServerSell.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerBuy.ID, ServerBuy.CODEC);
        PayloadTypeRegistry.playC2S().register(BondBuy.ID, BondBuy.CODEC);
        PayloadTypeRegistry.playC2S().register(BondWithdraw.ID, BondWithdraw.CODEC);
        PayloadTypeRegistry.playC2S().register(WholesaleAdd.ID, WholesaleAdd.CODEC);
        PayloadTypeRegistry.playC2S().register(WholesaleRemove.ID, WholesaleRemove.CODEC);
        PayloadTypeRegistry.playC2S().register(WholesaleOrderOffer.ID, WholesaleOrderOffer.CODEC);
        PayloadTypeRegistry.playC2S().register(WholesaleRequest.ID, WholesaleRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(WholesaleRespond.ID, WholesaleRespond.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestHistory.ID, RequestHistory.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestHub.ID, RequestHub.CODEC);
        PayloadTypeRegistry.playC2S().register(CloseHub.ID, CloseHub.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestShopBook.ID, RequestShopBook.CODEC);

        registerAction(BankAction.ID, EconomyNetworking::handleBankAction);
        registerAction(BuyListing.ID, (player, payload) -> {
            Marketplace.buy(player, payload.index());
            refresh(player);
        });
        registerAction(ListHeld.ID, (player, payload) -> {
            Marketplace.list(player, payload.price(), payload.depot());
            refresh(player);
        });
        registerAction(RenameShop.ID, (player, payload) -> {
            Marketplace.rename(player, payload.depot(), payload.name());
            refresh(player);
        });
        registerAction(Unlist.ID, (player, payload) -> {
            Marketplace.unlist(player, payload.index());
            refresh(player);
        });
        registerAction(ServerSell.ID, (player, payload) -> {
            int count = (int) Math.min(Integer.MAX_VALUE, payload.count());
            if (payload.itemId().isEmpty()) {
                Marketplace.serverSell(player, count);
            } else {
                Marketplace.serverSellItem(player, payload.itemId(), count);
            }
            refresh(player);
        });
        registerAction(ServerBuy.ID, (player, payload) -> {
            Marketplace.serverBuy(player, payload.itemId(),
                (int) Math.min(Integer.MAX_VALUE, payload.count()));
            refresh(player);
        });
        registerAction(BondBuy.ID, (player, payload) -> {
            if (atBank(player)) {
                TreasuryBonds.buy(player, payload.offeringId());
            }
            refresh(player);
        });
        registerAction(BondWithdraw.ID, (player, payload) -> {
            if (atBank(player)) {
                Bonds.withdraw(player, payload.index());
            }
            refresh(player);
        });
        registerAction(WholesaleAdd.ID, (player, payload) -> {
            WholesaleManager.addOffer(player, payload.lotSize(), payload.lotPrice());
            refresh(player);
        });
        registerAction(WholesaleRemove.ID, (player, payload) -> {
            WholesaleManager.removeOffer(player, payload.offerId());
            refresh(player);
        });
        registerAction(WholesaleOrderOffer.ID, (player, payload) -> {
            WholesaleManager.orderOffer(player, payload.offerId(), payload.lots());
            refresh(player);
        });
        registerAction(WholesaleRequest.ID, (player, payload) -> {
            WholesaleManager.requestListing(player, payload.seller(),
                new Spot(payload.depot(), payload.dimension()), payload.stack(), payload.quantity(),
                payload.total());
            refresh(player);
        });
        registerAction(WholesaleRespond.ID, (player, payload) -> {
            switch (payload.action()) {
                case WholesaleRespond.ACCEPT -> WholesaleManager.accept(player, payload.orderId());
                case WholesaleRespond.DECLINE -> WholesaleManager.decline(player, payload.orderId());
                case WholesaleRespond.CANCEL -> WholesaleManager.cancel(player, payload.orderId());
                default -> {
                }
            }
            refresh(player);
        });
        registerAction(RequestHistory.ID, (player, payload) -> openHistory(player));
        registerAction(RequestHub.ID, (player, payload) ->
            openHub(player, BankAccess.nearestBank(player, MmsEconomy.config().bankRange)));
        registerAction(CloseHub.ID, (player, payload) -> handleClose(player));
        registerAction(RequestShopBook.ID, EconomyNetworking::giveShopBook);
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
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther, "", BlockPos.ORIGIN));
    }

    // Opens the market on the shop a shop book names, or tells the player it has closed.
    public static void openShop(ServerPlayerEntity player, ShopBook.Target target) {
        for (ShopListing listing : Market.get(player.getEntityWorld().getServer()).all()) {
            if (listing.ownerName().equals(target.owner())
                && listing.depot().equals(target.depot())
                && listing.dimension().equals(target.dimension())) {
                ServerPlayNetworking.send(player, snapshot(player, false, false,
                    target.owner(), target.depot()));
                return;
            }
        }
        player.sendMessage(Text.literal("This shop has closed."), true);
    }

    // Gives the player a shop book for the first listing at the named owner's depot.
    private static void giveShopBook(ServerPlayerEntity player, RequestShopBook request) {
        for (ShopListing listing : Market.get(player.getEntityWorld().getServer()).all()) {
            if (listing.ownerName().equals(request.owner()) && listing.depot().equals(request.depot())) {
                String shopName = Market.get(player.getEntityWorld().getServer())
                    .shopName(listing.dimension(), listing.depot());
                player.getInventory().offerOrDrop(ShopBook.create(listing, shopName));
                return;
            }
        }
        player.sendMessage(Text.literal("That shop no longer exists."), false);
    }

    private static boolean atBank(ServerPlayerEntity player) {
        return DebugAccess.has(player)
            || BankAccess.nearestBank(player, MmsEconomy.config().bankRange) != null;
    }

    private static void refresh(ServerPlayerEntity player) {
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        boolean hasBank = bank != null;
        boolean occupiedByOther = hasBank && BankOccupancy.isOccupiedByOther(bank, player);
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther, "", BlockPos.ORIGIN));
    }

    private static OpenHub snapshot(ServerPlayerEntity player, boolean hasBank, boolean occupiedByOther,
                                    String focusOwner, BlockPos focusDepot) {
        ServerWorld world = player.getEntityWorld();
        MinecraftServer server = world.getServer();
        Market market = Market.get(server);
        List<ShopListing> all = market.all();
        String dimension = world.getRegistryKey().getValue().toString();
        int depotRange = MmsEconomy.config().depotRange;

        List<MarketRow> listings = new ArrayList<>(all.size());
        List<ShopRow> mine = new ArrayList<>();
        Map<BlockPos, List<Inventory>> depots = new HashMap<>();
        boolean anyBuyable = false;
        for (int i = 0; i < all.size(); i++) {
            ShopListing listing = all.get(i);
            String name = Marketplace.displayName(listing);
            String shopName = market.shopName(listing.dimension(), listing.depot());
            ItemStack stack = listing.template();
            boolean own = listing.owner().equals(player.getUuid());
            boolean sameDim = listing.dimension().equals(dimension);
            boolean buyable = !own && Marketplace.canReach(player, listing, server)
                && stock(world, depots, listing) > 0;
            anyBuyable = anyBuyable || buyable;
            listings.add(new MarketRow(i, name, listing.price(), listing.ownerName(),
                listing.depot().getX(), listing.depot().getY(), listing.depot().getZ(), buyable,
                shopName, stack, listing.owner(), listing.dimension()));
            if (own) {
                mine.add(new ShopRow(i, name, listing.price(),
                    sameDim ? stock(world, depots, listing) : 0,
                    listing.depot(), shopName, stack));
            }
        }

        BlockPos depot = Depot.detectNear(player, depotRange);
        boolean nearDepot = (depot != null && !market.depotClaimedByOther(depot, player.getUuid()))
            || Marketplace.ownsReachableDepot(player);

        info.mudbourn.mmseconomy.economy.ServerLibrary shelf =
            info.mudbourn.mmseconomy.economy.ServerLibrary.get(server);
        List<ServerShopRow> shopCatalog = new ArrayList<>();
        for (Map.Entry<String, ServerBuyList.Entry> entry : ServerBuyList.all().entrySet()) {
            String id = entry.getKey();
            if (Marketplace.itemOf(id) == null) {
                continue;
            }
            long sell = entry.getValue().sell();
            long stock = shelf.count(id);
            long buyPrice = stock > 0
                ? ServerBuyList.stockBuyPrice(id, sell)
                : ServerBuyList.mintBuyPrice(id, sell);
            shopCatalog.add(new ServerShopRow(id, Marketplace.displayName(id),
                ServerBuyList.sellPriceAt(id, stock), buyPrice,
                (int) Math.min(Integer.MAX_VALUE, stock), entry.getValue().category().ordinal()));
        }

        boolean debug = info.mudbourn.mmseconomy.economy.DebugAccess.has(player);
        boolean effectiveBank = hasBank || debug;
        boolean effectiveDepot = nearDepot || debug;
        int mode = hasBank ? 2 : ((anyBuyable || nearDepot) ? 1 : 0);

        boolean admin = net.minecraft.server.command.CommandManager.GAMEMASTERS_CHECK
            .allows(player.getPermissions()) || debug;
        long treasury = admin ? info.mudbourn.mmseconomy.economy.Treasury.get(server).balance() : 0L;
        List<LibraryRow> library = new ArrayList<>();
        if (admin) {
            Map<String, Long> available = shelf.view();
            java.util.TreeSet<String> ids = new java.util.TreeSet<>(available.keySet());
            ids.addAll(shelf.boughtView().keySet());
            for (String id : ids) {
                library.add(new LibraryRow(id, Marketplace.displayName(id),
                    available.getOrDefault(id, 0L), shelf.boughtCount(id)));
            }
        }

        Obligations obligations = Obligations.get(server);
        List<BondOfferingRow> offerings = new ArrayList<>();
        for (BondOffering offering : obligations.offerings) {
            offerings.add(new BondOfferingRow(offering.id, offering.principal, offering.couponBps,
                offering.couponDays, offering.termDays, offering.remaining));
        }
        List<VaultBondRow> vault = new ArrayList<>();
        List<Bond> vaulted = Bonds.vaulted(obligations, player);
        for (int i = 0; i < vaulted.size(); i++) {
            vault.add(new VaultBondRow(i + 1, Bonds.describe(server, obligations, vaulted.get(i))));
        }

        WholesaleManager.retarget(player);
        Wholesale wholesaleState = Wholesale.get(server);
        List<WholesaleOfferRow> wholesaleOffers = new ArrayList<>();
        for (WholesaleOffer offer : wholesaleState.offers) {
            boolean own = offer.seller().equals(player.getUuid());
            if (own || WholesaleManager.live(server, offer.seller(), offer.depot())) {
                wholesaleOffers.add(new WholesaleOfferRow(offer.id(), offer.stack(),
                    offer.stack().getName().getString(), offer.lotSize(), offer.lotPrice(),
                    offer.sellerName(), own));
            }
        }
        List<WholesaleOrderRow> wholesaleOrders = new ArrayList<>();
        for (WholesaleOrder order : wholesaleState.orders) {
            boolean incoming = order.seller.equals(player.getUuid());
            if (incoming || order.buyer.equals(player.getUuid())) {
                wholesaleOrders.add(new WholesaleOrderRow(order.id, order.stack,
                    order.stack.getName().getString(), order.items(), order.escrow,
                    incoming ? order.buyerName : order.sellerName, incoming, order.inTransit,
                    order.remaining));
            }
        }

        return new OpenHub(Wallet.balance(player),
            info.mudbourn.mmseconomy.economy.Gems.inventoryValue(player),
            MmsEconomy.config().piceToColtRatio,
            MmsEconomy.config().bankInterestPercent,
            MmsEconomy.config().bankInterestDays,
            effectiveBank,
            occupiedByOther && !debug,
            effectiveDepot,
            mode,
            listings, mine, shopCatalog, admin, treasury, library,
            focusOwner, focusDepot, offerings, vault,
            WholesaleManager.canTrade(player), wholesaleOffers, wholesaleOrders);
    }

    // The listing's live stock, reading each depot's barrels at most once per snapshot.
    private static int stock(ServerWorld world, Map<BlockPos, List<Inventory>> depots, ShopListing listing) {
        List<Inventory> barrels = depots.computeIfAbsent(listing.depot(), depot -> Depot.barrels(world, depot));
        return Marketplace.stock(barrels, listing);
    }

    public static void openHistory(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new OpenHistory(
            History.get(player, HistoryCategory.PURCHASE),
            History.get(player, HistoryCategory.PAYMENT),
            History.get(player, HistoryCategory.DEAL),
            History.get(player, HistoryCategory.BILL)));
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

        if (action.chips() && (!Chips.casinoLoaded() || amount % Chips.step() != 0)) {
            player.sendMessage(
                Text.literal("Chips come in multiples of " + Currency.format(Chips.step()) + "."),
                false);
            return;
        }

        if (action.chips() && action.deposit()) {
            if (!Chips.removeExact(player, amount)) {
                player.sendMessage(Text.literal("You lack chips worth exactly that amount."), false);
                return;
            }
            Wallet.deposit(player, amount);
        } else if (action.chips()) {
            if (!Wallet.withdraw(player, amount)) {
                player.sendMessage(Text.literal("Your balance is too low."), false);
                return;
            }
            Chips.give(player, amount);
        } else if (action.deposit()) {
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
                bankNote(action)));

        refresh(player);
    }

    private static String bankNote(BankAction action) {
        if (action.chips()) {
            return action.deposit() ? "Chips cashed" : "Chips bought";
        }
        return action.deposit() ? "Bank deposit" : "Bank withdrawal";
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
