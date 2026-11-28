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
import info.mudbourn.mmseconomy.economy.BondMarket;
import info.mudbourn.mmseconomy.economy.BondOffering;
import info.mudbourn.mmseconomy.economy.Bonds;
import info.mudbourn.mmseconomy.economy.Chips;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.economy.DebugAccess;
import info.mudbourn.mmseconomy.economy.Gems;
import info.mudbourn.mmseconomy.economy.Loan;
import info.mudbourn.mmseconomy.economy.Loans;
import info.mudbourn.mmseconomy.economy.Obligations;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
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
                                  int remaining, String issuer, boolean own) {
    }

    public record LoanRow(int id, String text, boolean owed, long payoff) {
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
                          List<WholesaleOrderRow> wholesaleOrders, List<LoanRow> loans, String bankLoanTerms)
        implements CustomPacketPayload {
        public static final Type<OpenHub> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "open_hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenHub> CODEC = StreamCodec.ofMember(
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
                    buf.writeUtf(row.name());
                    buf.writeLong(row.price());
                    buf.writeUtf(row.owner());
                    buf.writeVarInt(row.x());
                    buf.writeVarInt(row.y());
                    buf.writeVarInt(row.z());
                    buf.writeBoolean(row.buyable());
                    buf.writeUtf(row.shopName());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack());
                    buf.writeUUID(row.ownerId());
                    buf.writeUtf(row.dimension());
                }
                buf.writeVarInt(value.myListings.size());
                for (ShopRow row : value.myListings) {
                    buf.writeVarInt(row.index());
                    buf.writeUtf(row.name());
                    buf.writeLong(row.price());
                    buf.writeVarInt(row.stock());
                    buf.writeBlockPos(row.depot());
                    buf.writeUtf(row.shopName());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack());
                }
                buf.writeVarInt(value.shopCatalog.size());
                for (ServerShopRow row : value.shopCatalog) {
                    buf.writeUtf(row.itemId());
                    buf.writeUtf(row.name());
                    buf.writeLong(row.sell());
                    buf.writeLong(row.buyPrice());
                    buf.writeVarInt(row.stock());
                    buf.writeVarInt(row.category());
                }
                buf.writeBoolean(value.admin);
                buf.writeLong(value.treasury);
                buf.writeVarInt(value.library.size());
                for (LibraryRow row : value.library) {
                    buf.writeUtf(row.itemId());
                    buf.writeUtf(row.name());
                    buf.writeLong(row.count());
                    buf.writeLong(row.bought());
                }
                buf.writeUtf(value.focusOwner);
                buf.writeBlockPos(value.focusDepot);
                buf.writeVarInt(value.offerings.size());
                for (BondOfferingRow row : value.offerings) {
                    buf.writeVarInt(row.id());
                    buf.writeLong(row.principal());
                    buf.writeVarInt(row.couponBps());
                    buf.writeVarInt(row.couponDays());
                    buf.writeVarInt(row.termDays());
                    buf.writeVarInt(row.remaining());
                    buf.writeUtf(row.issuer());
                    buf.writeBoolean(row.own());
                }
                buf.writeVarInt(value.vault.size());
                for (VaultBondRow row : value.vault) {
                    buf.writeVarInt(row.index());
                    buf.writeUtf(row.text());
                }
                buf.writeBoolean(value.wholesale);
                buf.writeVarInt(value.wholesaleOffers.size());
                for (WholesaleOfferRow row : value.wholesaleOffers) {
                    buf.writeVarInt(row.id());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack());
                    buf.writeUtf(row.name());
                    buf.writeVarInt(row.lotSize());
                    buf.writeLong(row.lotPrice());
                    buf.writeUtf(row.seller());
                    buf.writeBoolean(row.own());
                }
                buf.writeVarInt(value.wholesaleOrders.size());
                for (WholesaleOrderRow row : value.wholesaleOrders) {
                    buf.writeVarInt(row.id());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.stack());
                    buf.writeUtf(row.name());
                    buf.writeVarInt(row.items());
                    buf.writeLong(row.escrow());
                    buf.writeUtf(row.other());
                    buf.writeBoolean(row.incoming());
                    buf.writeBoolean(row.inTransit());
                    buf.writeVarInt(row.remaining());
                }
                buf.writeVarInt(value.loans.size());
                for (LoanRow row : value.loans) {
                    buf.writeVarInt(row.id());
                    buf.writeUtf(row.text());
                    buf.writeBoolean(row.owed());
                    buf.writeLong(row.payoff());
                }
                buf.writeUtf(value.bankLoanTerms);
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
                        buf.readVarInt(), buf.readUtf(), buf.readLong(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                        buf.readUUID(), buf.readUtf()));
                }
                int myCount = buf.readVarInt();
                List<ShopRow> mine = new ArrayList<>(myCount);
                for (int i = 0; i < myCount; i++) {
                    mine.add(new ShopRow(buf.readVarInt(), buf.readUtf(), buf.readLong(), buf.readVarInt(),
                        buf.readBlockPos(), buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
                }
                int catalogCount = buf.readVarInt();
                List<ServerShopRow> shopCatalog = new ArrayList<>(catalogCount);
                for (int i = 0; i < catalogCount; i++) {
                    shopCatalog.add(new ServerShopRow(buf.readUtf(), buf.readUtf(),
                        buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt()));
                }
                boolean admin = buf.readBoolean();
                long treasury = buf.readLong();
                int libraryCount = buf.readVarInt();
                List<LibraryRow> library = new ArrayList<>(libraryCount);
                for (int i = 0; i < libraryCount; i++) {
                    library.add(new LibraryRow(buf.readUtf(), buf.readUtf(),
                        buf.readLong(), buf.readLong()));
                }
                String focusOwner = buf.readUtf();
                BlockPos focusDepot = buf.readBlockPos();
                int offeringCount = buf.readVarInt();
                List<BondOfferingRow> offerings = new ArrayList<>(offeringCount);
                for (int i = 0; i < offeringCount; i++) {
                    offerings.add(new BondOfferingRow(buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                        buf.readBoolean()));
                }
                int vaultCount = buf.readVarInt();
                List<VaultBondRow> vault = new ArrayList<>(vaultCount);
                for (int i = 0; i < vaultCount; i++) {
                    vault.add(new VaultBondRow(buf.readVarInt(), buf.readUtf()));
                }
                boolean wholesale = buf.readBoolean();
                int wholesaleOfferCount = buf.readVarInt();
                List<WholesaleOfferRow> wholesaleOffers = new ArrayList<>(wholesaleOfferCount);
                for (int i = 0; i < wholesaleOfferCount; i++) {
                    wholesaleOffers.add(new WholesaleOfferRow(buf.readVarInt(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readVarInt(),
                        buf.readLong(), buf.readUtf(), buf.readBoolean()));
                }
                int wholesaleOrderCount = buf.readVarInt();
                List<WholesaleOrderRow> wholesaleOrders = new ArrayList<>(wholesaleOrderCount);
                for (int i = 0; i < wholesaleOrderCount; i++) {
                    wholesaleOrders.add(new WholesaleOrderRow(buf.readVarInt(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readVarInt(),
                        buf.readLong(), buf.readUtf(), buf.readBoolean(), buf.readBoolean(),
                        buf.readVarInt()));
                }
                int loanCount = buf.readVarInt();
                List<LoanRow> loans = new ArrayList<>(loanCount);
                for (int i = 0; i < loanCount; i++) {
                    loans.add(new LoanRow(buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readLong()));
                }
                String bankLoanTerms = buf.readUtf();
                return new OpenHub(balance, pocket, coltRatio, interestPercent, interestDays,
                    hasBank, occupied, nearDepot, mode, listings, mine, shopCatalog, admin, treasury, library,
                    focusOwner, focusDepot, offerings, vault, wholesale, wholesaleOffers, wholesaleOrders,
                    loans, bankLoanTerms);
            });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record OpenHistory(List<HistoryEntry> purchases, List<HistoryEntry> payments,
                              List<HistoryEntry> deals, List<HistoryEntry> bills) implements CustomPacketPayload {
        public static final Type<OpenHistory> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "open_history"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenHistory> CODEC = StreamCodec.ofMember(
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
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BankAction(boolean deposit, boolean chips, long amount) implements CustomPacketPayload {
        public static final Type<BankAction> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "bank_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BankAction> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeBoolean(value.deposit);
                buf.writeBoolean(value.chips);
                buf.writeLong(value.amount);
            },
            buf -> new BankAction(buf.readBoolean(), buf.readBoolean(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BuyListing(int index) implements CustomPacketPayload {
        public static final Type<BuyListing> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "buy_listing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BuyListing> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new BuyListing(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // A listing request; a null depot lists at the looked-at barrel wall instead of a chosen shop.
    public record ListHeld(long price, BlockPos depot) implements CustomPacketPayload {
        public static final Type<ListHeld> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "list_held"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ListHeld> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeLong(value.price);
                buf.writeBoolean(value.depot != null);
                if (value.depot != null) {
                    buf.writeBlockPos(value.depot);
                }
            },
            buf -> new ListHeld(buf.readLong(), buf.readBoolean() ? buf.readBlockPos() : null));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RenameShop(BlockPos depot, String name) implements CustomPacketPayload {
        public static final Type<RenameShop> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "rename_shop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameShop> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeBlockPos(value.depot);
                buf.writeUtf(value.name, Marketplace.MAX_SHOP_NAME);
            },
            buf -> new RenameShop(buf.readBlockPos(), buf.readUtf(Marketplace.MAX_SHOP_NAME)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record Unlist(int index) implements CustomPacketPayload {
        public static final Type<Unlist> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "unlist"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Unlist> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new Unlist(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ServerSell(String itemId, long count) implements CustomPacketPayload {
        public static final Type<ServerSell> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "server_sell"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerSell> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.itemId);
                buf.writeLong(value.count);
            },
            buf -> new ServerSell(buf.readUtf(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BondBuy(int offeringId) implements CustomPacketPayload {
        public static final Type<BondBuy> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "bond_buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BondBuy> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.offeringId),
            buf -> new BondBuy(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BondWithdraw(int index) implements CustomPacketPayload {
        public static final Type<BondWithdraw> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "bond_withdraw"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BondWithdraw> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new BondWithdraw(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BondCancel(int offeringId) implements CustomPacketPayload {
        public static final Type<BondCancel> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "bond_cancel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BondCancel> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.offeringId),
            buf -> new BondCancel(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record LoanRepay(int id) implements CustomPacketPayload {
        public static final Type<LoanRepay> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "loan_repay"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LoanRepay> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.id),
            buf -> new LoanRepay(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record BondVault() implements CustomPacketPayload {
        public static final Type<BondVault> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "bond_vault"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BondVault> CODEC = StreamCodec.unit(new BondVault());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ServerBuy(String itemId, long count) implements CustomPacketPayload {
        public static final Type<ServerBuy> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "server_buy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerBuy> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.itemId);
                buf.writeLong(value.count);
            },
            buf -> new ServerBuy(buf.readUtf(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RequestHistory() implements CustomPacketPayload {
        public static final Type<RequestHistory> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "request_history"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestHistory> CODEC = StreamCodec.unit(new RequestHistory());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RequestHub() implements CustomPacketPayload {
        public static final Type<RequestHub> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "request_hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestHub> CODEC = StreamCodec.unit(new RequestHub());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record CloseHub() implements CustomPacketPayload {
        public static final Type<CloseHub> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "close_hub"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CloseHub> CODEC = StreamCodec.unit(new CloseHub());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RequestShopBook(String owner, BlockPos depot) implements CustomPacketPayload {
        public static final Type<RequestShopBook> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "request_shop_book"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestShopBook> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.owner);
                buf.writeBlockPos(value.depot);
            },
            buf -> new RequestShopBook(buf.readUtf(), buf.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record WholesaleAdd(int lotSize, long lotPrice) implements CustomPacketPayload {
        public static final Type<WholesaleAdd> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "wholesale_add"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WholesaleAdd> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeVarInt(value.lotSize);
                buf.writeLong(value.lotPrice);
            },
            buf -> new WholesaleAdd(buf.readVarInt(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record WholesaleRemove(int offerId) implements CustomPacketPayload {
        public static final Type<WholesaleRemove> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "wholesale_remove"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WholesaleRemove> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeVarInt(value.offerId),
            buf -> new WholesaleRemove(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record WholesaleOrderOffer(int offerId, int lots) implements CustomPacketPayload {
        public static final Type<WholesaleOrderOffer> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "wholesale_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WholesaleOrderOffer> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeVarInt(value.offerId);
                buf.writeVarInt(value.lots);
            },
            buf -> new WholesaleOrderOffer(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // A request against a shop listing, naming the listing by seller, depot, dimension and item.
    public record WholesaleRequest(UUID seller, BlockPos depot, String dimension, ItemStack stack,
                                   int quantity, long total) implements CustomPacketPayload {
        public static final Type<WholesaleRequest> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "wholesale_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WholesaleRequest> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUUID(value.seller);
                buf.writeBlockPos(value.depot);
                buf.writeUtf(value.dimension);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, value.stack);
                buf.writeVarInt(value.quantity);
                buf.writeLong(value.total);
            },
            buf -> new WholesaleRequest(buf.readUUID(), buf.readBlockPos(), buf.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // Answers a wholesale order: the seller accepts or declines it, the buyer cancels it.
    public record WholesaleRespond(int orderId, int action) implements CustomPacketPayload {
        public static final int ACCEPT = 0;

        public static final int DECLINE = 1;

        public static final int CANCEL = 2;

        public static final Type<WholesaleRespond> ID = new Type<>(Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, "wholesale_respond"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WholesaleRespond> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeVarInt(value.orderId);
                buf.writeVarInt(value.action);
            },
            buf -> new WholesaleRespond(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    // Sends a message to one player, as a toast while a screen is open and as an overlay otherwise.
    public static void notice(ServerPlayer player, String message, boolean failed) {
        ServerPlayNetworking.send(player, new NoticePayload(message, failed));
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(NoticePayload.TYPE, NoticePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenHub.ID, OpenHub.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenHistory.ID, OpenHistory.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BankAction.ID, BankAction.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BuyListing.ID, BuyListing.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ListHeld.ID, ListHeld.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Unlist.ID, Unlist.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenameShop.ID, RenameShop.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerSell.ID, ServerSell.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerBuy.ID, ServerBuy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BondBuy.ID, BondBuy.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BondWithdraw.ID, BondWithdraw.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BondVault.ID, BondVault.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BondCancel.ID, BondCancel.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LoanRepay.ID, LoanRepay.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WholesaleAdd.ID, WholesaleAdd.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WholesaleRemove.ID, WholesaleRemove.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WholesaleOrderOffer.ID, WholesaleOrderOffer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WholesaleRequest.ID, WholesaleRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WholesaleRespond.ID, WholesaleRespond.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestHistory.ID, RequestHistory.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestHub.ID, RequestHub.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CloseHub.ID, CloseHub.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestShopBook.ID, RequestShopBook.CODEC);

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
                BondMarket.buy(player, payload.offeringId());
            }
            refresh(player);
        });
        registerAction(BondWithdraw.ID, (player, payload) -> {
            if (atBank(player)) {
                Bonds.withdraw(player, payload.index());
            }
            refresh(player);
        });
        registerAction(BondCancel.ID, (player, payload) -> {
            BondMarket.cancel(player.level().getServer(), payload.offeringId(),
                player.getUUID().toString());
            refresh(player);
        });
        registerAction(LoanRepay.ID, (player, payload) -> {
            Loans.repay(player, payload.id());
            refresh(player);
        });
        registerAction(BondVault.ID, (player, payload) -> {
            Bonds.vaultCarried(player);
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

    private static <T extends CustomPacketPayload> void registerAction(CustomPacketPayload.Type<T> id,
                                                                 java.util.function.BiConsumer<ServerPlayer, T> action) {
        ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> action.accept(player, payload));
        });
    }

    public static void openHub(ServerPlayer player, BlockPos bankPos) {
        ServerLevel world = player.level();
        boolean hasBank = bankPos != null;
        boolean occupiedByOther = false;
        if (hasBank) {
            occupiedByOther = BankOccupancy.isOccupiedByOther(bankPos, player);
            if (!occupiedByOther) {
                BankOccupancy.claim(world, bankPos, player);
                playAt(world, bankPos, ModSounds.BANK_OPEN);
            }
        }
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther, "", BlockPos.ZERO));
    }

    // Opens the market on the shop a shop book names, or tells the player it has closed.
    public static void openShop(ServerPlayer player, ShopBook.Target target) {
        for (ShopListing listing : Market.get(player.level().getServer()).all()) {
            if (listing.ownerName().equals(target.owner())
                && listing.depot().equals(target.depot())
                && listing.dimension().equals(target.dimension())) {
                ServerPlayNetworking.send(player, snapshot(player, false, false,
                    target.owner(), target.depot()));
                return;
            }
        }
        EconomyNetworking.notice(player, "This shop has closed.", true);
    }

    // Gives the player a shop book for the first listing at the named owner's depot.
    private static void giveShopBook(ServerPlayer player, RequestShopBook request) {
        for (ShopListing listing : Market.get(player.level().getServer()).all()) {
            if (listing.ownerName().equals(request.owner()) && listing.depot().equals(request.depot())) {
                String shopName = Market.get(player.level().getServer())
                    .shopName(listing.dimension(), listing.depot());
                player.getInventory().placeItemBackInInventory(ShopBook.create(listing, shopName), Prediction.SERVER_ONLY);
                return;
            }
        }
        EconomyNetworking.notice(player, "That shop no longer exists.", true);
    }

    private static boolean atBank(ServerPlayer player) {
        return DebugAccess.has(player)
            || BankAccess.nearestBank(player, MmsEconomy.config().bankRange) != null;
    }

    private static void refresh(ServerPlayer player) {
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        boolean hasBank = bank != null;
        boolean occupiedByOther = hasBank && BankOccupancy.isOccupiedByOther(bank, player);
        ServerPlayNetworking.send(player, snapshot(player, hasBank, occupiedByOther, "", BlockPos.ZERO));
    }

    private static OpenHub snapshot(ServerPlayer player, boolean hasBank, boolean occupiedByOther,
                                    String focusOwner, BlockPos focusDepot) {
        ServerLevel world = player.level();
        MinecraftServer server = world.getServer();
        Market market = Market.get(server);
        List<ShopListing> all = market.all();
        String dimension = world.dimension().identifier().toString();
        int depotRange = MmsEconomy.config().depotRange;

        List<MarketRow> listings = new ArrayList<>(all.size());
        List<ShopRow> mine = new ArrayList<>();
        Map<BlockPos, List<Container>> depots = new HashMap<>();
        boolean anyBuyable = false;
        for (int i = 0; i < all.size(); i++) {
            ShopListing listing = all.get(i);
            String name = Marketplace.displayName(listing);
            String shopName = market.shopName(listing.dimension(), listing.depot());
            ItemStack stack = listing.template();
            boolean own = listing.owner().equals(player.getUUID());
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
        boolean nearDepot = (depot != null
            && !market.depotClaimedByOther(depot, player.getUUID())
            && !Depot.ownedByOther(world, depot, player.getUUID()))
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

        boolean admin = net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
            .check(player.permissions()) || debug;
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
                offering.couponDays, offering.termDays, offering.remaining, offering.issuerName,
                offering.issuer.equals(player.getUUID().toString())));
        }
        List<VaultBondRow> vault = new ArrayList<>();
        List<Bond> vaulted = Bonds.vaulted(obligations, player);
        for (int i = 0; i < vaulted.size(); i++) {
            vault.add(new VaultBondRow(i + 1, Bonds.describe(server, obligations, vaulted.get(i))));
        }

        String playerId = player.getUUID().toString();
        List<LoanRow> loans = new ArrayList<>();
        for (Loan loan : Loans.involving(obligations, playerId)) {
            loans.add(new LoanRow(loan.id, Loans.describe(server, loan, playerId), loan.borrower.equals(playerId),
                loan.payoff()));
        }

        WholesaleManager.retarget(player);
        Wholesale wholesaleState = Wholesale.get(server);
        List<WholesaleOfferRow> wholesaleOffers = new ArrayList<>();
        for (WholesaleOffer offer : wholesaleState.offers) {
            boolean own = offer.seller().equals(player.getUUID());
            if (own || WholesaleManager.live(server, offer.seller(), offer.depot())) {
                wholesaleOffers.add(new WholesaleOfferRow(offer.id(), offer.stack(),
                    offer.stack().getHoverName().getString(), offer.lotSize(), offer.lotPrice(),
                    offer.sellerName(), own));
            }
        }
        List<WholesaleOrderRow> wholesaleOrders = new ArrayList<>();
        for (WholesaleOrder order : wholesaleState.orders) {
            boolean incoming = order.seller.equals(player.getUUID());
            if (incoming || order.buyer.equals(player.getUUID())) {
                wholesaleOrders.add(new WholesaleOrderRow(order.id, order.stack,
                    order.stack.getHoverName().getString(), order.items(), order.escrow,
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
            WholesaleManager.canTrade(player), wholesaleOffers, wholesaleOrders,
            loans, Loans.bankTerms(player));
    }

    // The listing's live stock, reading each depot's barrels at most once per snapshot.
    private static int stock(ServerLevel world, Map<BlockPos, List<Container>> depots, ShopListing listing) {
        List<Container> barrels = depots.computeIfAbsent(listing.depot(), depot -> Depot.barrels(world, depot));
        return Marketplace.stock(barrels, listing);
    }

    public static void openHistory(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenHistory(
            History.get(player, HistoryCategory.PURCHASE),
            History.get(player, HistoryCategory.PAYMENT),
            History.get(player, HistoryCategory.DEAL),
            History.get(player, HistoryCategory.BILL)));
    }

    private static void handleBankAction(ServerPlayer player, BankAction action) {
        boolean debug = info.mudbourn.mmseconomy.economy.DebugAccess.has(player);
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        if (!debug && (bank == null || BankOccupancy.isOccupiedByOther(bank, player))) {
            EconomyNetworking.notice(player, "This bank is not available.", true);
            return;
        }
        long amount = action.amount();
        if (amount <= 0) {
            return;
        }

        boolean gamemaster = net.minecraft.commands.Commands.LEVEL_GAMEMASTERS
            .check(player.permissions()) || debug;
        if (action.chips() && (!action.deposit() || !gamemaster)) {
            EconomyNetworking.notice(player, "Only admins can cash chips at the bank.", true);
            return;
        }

        if (action.chips() && (!Chips.casinoLoaded() || amount % Chips.step() != 0)) {
            EconomyNetworking.notice(
                player,
                "Chips come in multiples of " + Currency.format(Chips.step()) + ".",
                true);
            return;
        }

        if (action.chips() && action.deposit()) {
            if (!Chips.removeExact(player, amount)) {
                EconomyNetworking.notice(player, "You lack chips worth exactly that amount.", true);
                return;
            }
            Wallet.deposit(player, amount);
        } else if (action.deposit()) {
            if (!Gems.removeExact(player, amount)) {
                EconomyNetworking.notice(player, "You lack gems worth exactly that amount.", true);
                return;
            }
            Wallet.deposit(player, amount);
        } else {
            if (!Wallet.withdraw(player, amount)) {
                EconomyNetworking.notice(player, "Your balance is too low.", true);
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
            return "Chips cashed";
        }
        return action.deposit() ? "Bank deposit" : "Bank withdrawal";
    }

    private static void handleClose(ServerPlayer player) {
        ServerLevel world = player.level();
        BlockPos bank = BankAccess.nearestBank(player, MmsEconomy.config().bankRange);
        if (bank != null) {
            playAt(world, bank, ModSounds.BANK_CLOSE);
        }
        BankOccupancy.release(world, player);
    }

    private static void playAt(ServerLevel world, BlockPos pos, SoundEvent sound) {
        world.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}
