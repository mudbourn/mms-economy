package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Chips;
import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.economy.BondPaper;
import info.mudbourn.mmseconomy.network.EconomyNetworking.BondOfferingRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.LoanRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.LibraryRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.MarketRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.OpenHub;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ServerShopRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ShopRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.VaultBondRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.WholesaleOfferRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.WholesaleOrderRow;
import info.mudbourn.mmseconomy.registry.ModItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import info.mudbourn.mmseconomy.client.MmsEconomyClient;

// The economy hub: a tab row over a balance header, opened by /eco or a bank block.
public final class HubScreen extends Screen {

    private enum Tab {
        MARKET,
        BANK,
        SHOPS,
        DEALS,
        BONDS,
        LOANS,
        HISTORY,
        ADMIN
    }

    // The three server-decided hub variants, mirrored from OpenHub.mode.
    private static final int MODE_PORTABLE = 0;

    private static final int MODE_MERCHANT = 1;

    private static final int MODE_BANKING = 2;

    private static final int LIST_TOP = 78;

    private static final int MY_SHOPS_TOP = 110;

    private static final int SHOP_LIST_TOP = 164;

    private static final int SERVER_LIST_TOP = 134;

    private static final int DETAIL_LIST_TOP = 96;

    // The three server-shop category labels, indexed by ServerShopCategory ordinal.
    private static final String[] CATEGORY_LABELS = {"Unobtainable", "Treasure", "Valuable"};

    private static final int ROW_HEIGHT = 22;

    private static final int ROWS_VISIBLE = 12;

    private OpenHub data;

    private Tab tab;

    private int scroll;

    // The market owner whose shops are open; null shows the owner list.
    private String selectedShop;

    // The depot within the selected owner whose items are open; null shows that owner's shop list.
    private BlockPos selectedDepot;

    // Whether the selected owner's shops are sorted farthest-first instead of closest-first.
    private boolean shopSortInverted;

    // The Shops-tab sub-view: managing existing listings, opening a new shop, or the server buy/sell menu.
    private enum ShopView {
        MANAGE,
        CREATE,
        SERVER
    }

    private ShopView shopView = ShopView.MANAGE;

    // The player's own shop open in the Shops tab; null shows the list of their shops.
    private BlockPos selectedMyDepot;

    // Horizontal space a row's item icon takes before its text.
    private static final int ICON_WIDTH = 20;

    // One of the player's own shops: its depot, custom name, and the listings sold there.
    private record MyShop(BlockPos depot, String name, List<ShopRow> rows) {
    }

    // Whether the server shop is in buy mode; false is sell mode.
    private boolean serverShopBuy = true;

    // The server-shop category being browsed, as a ServerShopCategory ordinal.
    private int serverShopCategory;

    // One shop location of an owner: its depot anchor and the listings sold there.
    private record ShopEntry(BlockPos depot, String name, List<MarketRow> rows) {
    }

    private List<HistoryEntry> historyPurchases = List.of();

    private List<HistoryEntry> historyPayments = List.of();

    private List<HistoryEntry> historyDeals = List.of();

    private List<HistoryEntry> historyBills = List.of();

    private HistoryCategory historyTab = HistoryCategory.PURCHASE;

    private boolean historyLoaded;

    private EditBox bankField;

    private EditBox priceField;

    private EditBox nameField;

    // The Deals-tab field contents, kept across redraws.
    private String dealLotSize = "";

    private String dealLotPrice = "";

    private String dealQuantity = "";

    private String dealTotal = "";

    private static final int DEALS_TOP = 126;

    // What a Deals-tab row shows.
    private enum DealKind {
        HEADER,
        MINE,
        OFFER,
        LISTING,
        ORDER
    }

    // One Deals-tab row: a section header, a wholesale offer, a shop listing, or an order.
    private record DealLine(DealKind kind, String text, WholesaleOfferRow offer, MarketRow listing,
                            WholesaleOrderRow order) {
    }

    // The owner grouping of the current snapshot's listings, rebuilt only when the snapshot changes.
    private List<Map.Entry<String, List<MarketRow>>> shopGroupsCache;

    private OpenHub shopGroupsSource;

    public HubScreen(OpenHub data) {
        super(Component.literal("Economy"));
        this.data = data;
        this.tab = defaultTab();
        focus();
    }

    public void apply(OpenHub data) {
        this.data = data;
        focus();
        if (selectedMyDepot != null && myDepotRows().isEmpty()) {
            this.selectedMyDepot = null;
            this.scroll = 0;
        }
        if (!tabAllowed(this.tab)) {
            this.tab = defaultTab();
            this.scroll = 0;
        }
        rebuildWidgets();
    }

    // Jumps the market to the owner and depot a shop lectern named, when the snapshot carries one.
    private void focus() {
        if (data.focusOwner().isEmpty() || !tabAllowed(Tab.MARKET)) {
            return;
        }
        this.tab = Tab.MARKET;
        this.scroll = 0;
        this.selectedShop = data.focusOwner();
        this.selectedDepot = data.focusDepot();
    }

    private Tab defaultTab() {
        return data.mode() == MODE_BANKING ? Tab.BANK : Tab.MARKET;
    }

    // The tabs each variant exposes: portable browses the market, merchant adds buying and shop tools, banking is the teller.
    private boolean tabAllowed(Tab target) {
        return switch (target) {
            case MARKET -> data.mode() != MODE_BANKING;
            case BANK -> data.mode() == MODE_BANKING;
            case SHOPS -> data.mode() != MODE_BANKING;
            case DEALS -> data.mode() == MODE_MERCHANT;
            case BONDS -> data.mode() == MODE_BANKING;
            case LOANS -> data.mode() == MODE_BANKING;
            case HISTORY -> true;
            case ADMIN -> data.admin();
        };
    }

    private static String tabLabel(Tab target) {
        return switch (target) {
            case MARKET -> "Market";
            case BANK -> "Bank";
            case SHOPS -> "Shops";
            case DEALS -> "Deals";
            case BONDS -> "Bonds";
            case LOANS -> "Loans";
            case HISTORY -> "History";
            case ADMIN -> "Admin";
        };
    }

    public void applyHistory(List<HistoryEntry> purchases, List<HistoryEntry> payments,
                             List<HistoryEntry> deals, List<HistoryEntry> bills) {
        this.historyBills = bills;
        this.historyPurchases = purchases;
        this.historyPayments = payments;
        this.historyDeals = deals;
        this.historyLoaded = true;
        if (tab == Tab.HISTORY) {
            rebuildWidgets();
        }
    }

    private List<HistoryEntry> historyRows() {
        return switch (historyTab) {
            case PURCHASE -> historyPurchases;
            case PAYMENT -> historyPayments;
            case DEAL -> historyDeals;
            case BILL -> historyBills;
        };
    }

    @Override
    protected void init() {
        List<Tab> tabs = new ArrayList<>();
        for (Tab candidate : Tab.values()) {
            if (tabAllowed(candidate)) {
                tabs.add(candidate);
            }
        }
        int tabWidth = 80;
        int gap = 4;
        int total = tabWidth * tabs.size() + gap * (tabs.size() - 1);
        int startX = this.width / 2 - total / 2;
        for (int i = 0; i < tabs.size(); i++) {
            Tab candidate = tabs.get(i);
            addTab(tabLabel(candidate), candidate, startX + i * (tabWidth + gap), tabWidth);
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());

        switch (tab) {
            case BANK -> initBank();
            case MARKET -> initMarket();
            case SHOPS -> initShops();
            case DEALS -> initDeals();
            case BONDS -> initBonds();
            case LOANS -> initLoans();
            case HISTORY -> initHistory();
            case ADMIN -> initAdmin();
        }
    }

    private void initAdmin() {
    }

    private void initHistory() {
        if (!historyLoaded) {
            ClientPlayNetworking.send(new EconomyNetworking.RequestHistory());
        }
        int width = 90;
        int startX = this.width / 2 - (width * 4 + 12) / 2;
        addHistoryTab("Purchases", HistoryCategory.PURCHASE, startX, width);
        addHistoryTab("Payments", HistoryCategory.PAYMENT, startX + width + 4, width);
        addHistoryTab("Deals", HistoryCategory.DEAL, startX + (width + 4) * 2, width);
        addHistoryTab("Bills", HistoryCategory.BILL, startX + (width + 4) * 3, width);
    }

    private void addHistoryTab(String label, HistoryCategory target, int x, int width) {
        Button button = Button.builder(Component.literal(label), b -> {
            this.historyTab = target;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(x, 74, width, 20).build();
        button.active = target != historyTab;
        addRenderableWidget(button);
    }

    private void addTab(String label, Tab target, int x, int width) {
        Button button = Button.builder(Component.literal(label), b -> {
            this.tab = target;
            this.scroll = 0;
            this.selectedShop = null;
            this.selectedDepot = null;
            this.selectedMyDepot = null;
            this.shopView = ShopView.MANAGE;
            rebuildWidgets();
        }).bounds(x, 28, width, 20).build();
        button.active = target != tab;
        addRenderableWidget(button);
    }

    private void initBank() {
        int centerX = this.width / 2;
        bankField = new EditBox(this.font, centerX - 80, 96, 160, 20, Component.literal("Amount"));
        bankField.setHint(Component.literal("12.34"));
        addRenderableWidget(bankField);
        setInitialFocus(bankField);

        boolean usable = data.hasBank() && !data.bankOccupiedByOther();
        Button deposit = Button.builder(Component.literal("Deposit"), b -> sendBank(true, false))
            .bounds(centerX - 80, 122, 78, 20).build();
        Button withdraw = Button.builder(Component.literal("Withdraw"), b -> sendBank(false, false))
            .bounds(centerX + 2, 122, 78, 20).build();
        deposit.active = usable;
        withdraw.active = usable;
        addRenderableWidget(deposit);
        addRenderableWidget(withdraw);

        if (Chips.casinoLoaded() && data.admin()) {
            Button cashChips = Button.builder(Component.literal("Cash chips"), b -> sendBank(true, true))
                .bounds(centerX - 80, 146, 160, 20).build();
            cashChips.active = usable;
            addRenderableWidget(cashChips);
        }
    }

    // The market listings grouped by owner, ordered by owner name.
    private List<Map.Entry<String, List<MarketRow>>> shopGroups() {
        if (shopGroupsSource == data) {
            return shopGroupsCache;
        }
        Map<String, List<MarketRow>> byOwner = new TreeMap<>();
        for (MarketRow row : data.listings()) {
            byOwner.computeIfAbsent(row.owner(), k -> new ArrayList<>()).add(row);
        }
        shopGroupsCache = new ArrayList<>(byOwner.entrySet());
        shopGroupsSource = data;
        return shopGroupsCache;
    }

    private List<MarketRow> selectedShopRows() {
        List<MarketRow> rows = new ArrayList<>();
        for (MarketRow row : data.listings()) {
            if (row.owner().equals(selectedShop)) {
                rows.add(row);
            }
        }
        return rows;
    }

    // The selected owner's distinct shop locations, sorted by distance to the player and reversible.
    private List<ShopEntry> selectedOwnerShops() {
        Map<BlockPos, List<MarketRow>> byDepot = new LinkedHashMap<>();
        for (MarketRow row : selectedShopRows()) {
            byDepot.computeIfAbsent(new BlockPos(row.x(), row.y(), row.z()), k -> new ArrayList<>()).add(row);
        }
        List<ShopEntry> shops = new ArrayList<>();
        for (Map.Entry<BlockPos, List<MarketRow>> entry : byDepot.entrySet()) {
            shops.add(new ShopEntry(entry.getKey(), entry.getValue().get(0).shopName(), entry.getValue()));
        }
        shops.sort(Comparator.comparingDouble(shop -> distanceTo(shop.depot())));
        if (shopSortInverted) {
            java.util.Collections.reverse(shops);
        }
        return shops;
    }

    private List<MarketRow> selectedDepotRows() {
        List<MarketRow> rows = new ArrayList<>();
        for (MarketRow row : selectedShopRows()) {
            if (row.x() == selectedDepot.getX()
                && row.y() == selectedDepot.getY()
                && row.z() == selectedDepot.getZ()) {
                rows.add(row);
            }
        }
        return rows;
    }

    // The player's own listings grouped by depot, closest shop first.
    private List<MyShop> myShops() {
        Map<BlockPos, List<ShopRow>> byDepot = new LinkedHashMap<>();
        for (ShopRow row : data.myListings()) {
            byDepot.computeIfAbsent(row.depot(), k -> new ArrayList<>()).add(row);
        }
        List<MyShop> shops = new ArrayList<>();
        for (Map.Entry<BlockPos, List<ShopRow>> entry : byDepot.entrySet()) {
            shops.add(new MyShop(entry.getKey(), entry.getValue().get(0).shopName(), entry.getValue()));
        }
        shops.sort(Comparator.comparingDouble(shop -> distanceTo(shop.depot())));
        return shops;
    }

    private List<ShopRow> myDepotRows() {
        List<ShopRow> rows = new ArrayList<>();
        for (ShopRow row : data.myListings()) {
            if (row.depot().equals(selectedMyDepot)) {
                rows.add(row);
            }
        }
        return rows;
    }

    // A shop's custom name, or its depot coordinates when it has none.
    private static String shopLabel(String name, BlockPos depot) {
        return name.isEmpty() ? "@ " + depot.getX() + "," + depot.getY() + "," + depot.getZ() : name;
    }

    // Squared distance from the player to a depot centre, or 0 when the player is unavailable.
    private double distanceTo(BlockPos depot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return 0.0;
        }
        double dx = this.minecraft.player.getX() - (depot.getX() + 0.5);
        double dy = this.minecraft.player.getY() - (depot.getY() + 0.5);
        double dz = this.minecraft.player.getZ() - (depot.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private void initMarket() {
        if (selectedShop == null) {
            initShopList();
        } else if (selectedDepot == null) {
            initOwnerShops();
        } else {
            initDepotItems();
        }
    }

    private void initShopList() {
        int left = this.width / 2 - 170;
        List<Map.Entry<String, List<MarketRow>>> groups = shopGroups();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < groups.size(); i++) {
            String owner = groups.get(i + scroll).getKey();
            addRenderableWidget(Button.builder(Component.literal("View"), b -> {
                this.selectedShop = owner;
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left + 280, LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initOwnerShops() {
        int left = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.selectedShop = null;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left, 70, 60, 20).build());
        addRenderableWidget(Button.builder(
            Component.literal(shopSortInverted ? "Farthest" : "Closest"), b -> {
                this.shopSortInverted = !this.shopSortInverted;
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left + 240, 70, 100, 20).build());

        List<ShopEntry> shops = selectedOwnerShops();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            ShopEntry shop = shops.get(i + scroll);
            addRenderableWidget(Button.builder(Component.literal("View"), b -> {
                this.selectedDepot = shop.depot();
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left + 280, DETAIL_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initDepotItems() {
        int left = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.selectedDepot = null;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left, 70, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Get shop book"), b ->
                ClientPlayNetworking.send(new EconomyNetworking.RequestShopBook(selectedShop, selectedDepot)))
            .bounds(left + 66, 70, 100, 20).build());

        List<MarketRow> rows = selectedDepotRows();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            MarketRow row = rows.get(i + scroll);
            Button buy = Button.builder(Component.literal("Buy"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.BuyListing(row.index())))
                .bounds(left + 280, DETAIL_LIST_TOP + i * ROW_HEIGHT - 4, 50, 20).build();
            buy.active = row.buyable();
            addRenderableWidget(buy);
        }
    }

    // The Shops sub-view to show; away from a depot only the server shop is browsable.
    private ShopView activeShopView() {
        return data.nearDepot() ? shopView : ShopView.SERVER;
    }

    private void initShops() {
        switch (activeShopView()) {
            case CREATE -> initShopCreate();
            case SERVER -> initServerShop();
            case MANAGE -> initShopManage();
        }
    }

    private void initShopManage() {
        if (selectedMyDepot == null) {
            initMyShopList();
        } else {
            initMyShop();
        }
    }

    private void initMyShopList() {
        int left = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("New shop"), b -> {
            this.shopView = ShopView.CREATE;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left, 70, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Server shop"), b -> {
            this.shopView = ShopView.SERVER;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left + 106, 70, 100, 20).build());

        List<MyShop> shops = myShops();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            MyShop shop = shops.get(i + scroll);
            addRenderableWidget(Button.builder(Component.literal("Manage"), b -> {
                this.selectedMyDepot = shop.depot();
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left + 280, MY_SHOPS_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initMyShop() {
        int left = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.selectedMyDepot = null;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left, 70, 60, 20).build());

        List<ShopRow> rows = myDepotRows();
        nameField = new EditBox(this.font, left, 100, 150, 20, Component.literal("Shop name"));
        nameField.setMaxLength(info.mudbourn.mmseconomy.market.Marketplace.MAX_SHOP_NAME);
        nameField.setHint(Component.literal("Shop name"));
        nameField.setValue(rows.isEmpty() ? "" : rows.get(0).shopName());
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Rename"), b ->
                ClientPlayNetworking.send(new EconomyNetworking.RenameShop(selectedMyDepot, nameField.getValue())))
            .bounds(left + 156, 100, 80, 20).build());

        priceField = new EditBox(this.font, left, 124, 90, 20, Component.literal("Price"));
        priceField.setHint(Component.literal("12.34"));
        addRenderableWidget(priceField);
        addRenderableWidget(Button.builder(Component.literal("List held item here"), b -> {
            long price = Currency.parse(priceField.getValue());
            if (price > 0) {
                ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price, selectedMyDepot));
            }
        }).bounds(left + 96, 124, 140, 20).build());

        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ShopRow row = rows.get(i + scroll);
            addRenderableWidget(Button.builder(Component.literal("Unlist"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.Unlist(row.index())))
                .bounds(left + 280, SHOP_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initShopCreate() {
        int left = this.width / 2 - 170;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            this.shopView = ShopView.MANAGE;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left, 70, 60, 20).build());

        priceField = new EditBox(this.font, left, 132, 90, 20, Component.literal("Price"));
        priceField.setHint(Component.literal("12.34"));
        addRenderableWidget(priceField);
        addRenderableWidget(Button.builder(Component.literal("Open shop with held item"), b -> {
            long price = Currency.parse(priceField.getValue());
            if (price > 0) {
                ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price, null));
                this.shopView = ShopView.MANAGE;
                this.scroll = 0;
                rebuildWidgets();
            }
        }).bounds(left + 96, 132, 180, 20).build());
    }

    // The catalog rows in the category currently being browsed.
    private List<ServerShopRow> catalogRows() {
        List<ServerShopRow> rows = new ArrayList<>();
        for (ServerShopRow row : data.shopCatalog()) {
            if (row.category() == serverShopCategory) {
                rows.add(row);
            }
        }
        return rows;
    }

    private void initServerShop() {
        int left = this.width / 2 - 170;
        if (data.nearDepot()) {
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
                this.shopView = ShopView.MANAGE;
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left, 70, 60, 20).build());
        }
        Button buy = Button.builder(Component.literal("Buy"), b -> {
            this.serverShopBuy = true;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left + 216, 70, 60, 20).build();
        buy.active = !serverShopBuy;
        addRenderableWidget(buy);
        Button sell = Button.builder(Component.literal("Sell"), b -> {
            this.serverShopBuy = false;
            this.scroll = 0;
            rebuildWidgets();
        }).bounds(left + 280, 70, 60, 20).build();
        sell.active = serverShopBuy;
        addRenderableWidget(sell);

        int catWidth = 110;
        for (int c = 0; c < CATEGORY_LABELS.length; c++) {
            int category = c;
            Button tab = Button.builder(Component.literal(CATEGORY_LABELS[c]), b -> {
                this.serverShopCategory = category;
                this.scroll = 0;
                rebuildWidgets();
            }).bounds(left + c * (catWidth + 4), 94, catWidth, 20).build();
            tab.active = category != serverShopCategory;
            addRenderableWidget(tab);
        }

        List<ServerShopRow> rows = catalogRows();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ServerShopRow row = rows.get(i + scroll);
            String label = serverShopBuy ? "Buy" : "Sell";
            Button trade = Button.builder(Component.literal(label), b -> {
                if (serverShopBuy) {
                    ClientPlayNetworking.send(new EconomyNetworking.ServerBuy(row.itemId(), 1));
                } else {
                    ClientPlayNetworking.send(new EconomyNetworking.ServerSell(row.itemId(), 1));
                }
            }).bounds(left + 280, SERVER_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build();
            trade.active = data.nearDepot();
            addRenderableWidget(trade);
        }
    }

    // The Deals rows: own offers, then everything browsable, then orders, each under a header.
    private List<DealLine> dealLines() {
        List<DealLine> lines = new ArrayList<>();
        lines.add(new DealLine(DealKind.HEADER, "My wholesale offers", null, null, null));
        for (WholesaleOfferRow offer : data.wholesaleOffers()) {
            if (offer.own()) {
                lines.add(new DealLine(DealKind.MINE, "", offer, null, null));
            }
        }
        lines.add(new DealLine(DealKind.HEADER, "Browse", null, null, null));
        for (WholesaleOfferRow offer : data.wholesaleOffers()) {
            if (!offer.own()) {
                lines.add(new DealLine(DealKind.OFFER, "", offer, null, null));
            }
        }
        java.util.Set<Integer> mine = new java.util.HashSet<>();
        for (ShopRow row : data.myListings()) {
            mine.add(row.index());
        }
        for (MarketRow row : data.listings()) {
            if (!mine.contains(row.index())) {
                lines.add(new DealLine(DealKind.LISTING, "", null, row, null));
            }
        }
        lines.add(new DealLine(DealKind.HEADER, "Orders", null, null, null));
        for (WholesaleOrderRow order : data.wholesaleOrders()) {
            lines.add(new DealLine(DealKind.ORDER, "", null, null, order));
        }
        return lines;
    }

    private static int parseCount(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private EditBox dealField(int x, int y, int width, String hint, String value,
                                      java.util.function.Consumer<String> sink) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setValue(value);
        field.setResponder(sink);
        addRenderableWidget(field);
        return field;
    }

    private void initDeals() {
        int left = this.width / 2 - 170;
        dealField(left, 70, 56, "Lot size", dealLotSize, text -> dealLotSize = text);
        dealField(left + 60, 70, 80, "Price/lot", dealLotPrice, text -> dealLotPrice = text);
        Button add = Button.builder(Component.literal("Add offer (held)"), b -> {
            int lotSize = parseCount(dealLotSize);
            long price = Currency.parse(dealLotPrice);
            if (lotSize > 0 && price > 0) {
                ClientPlayNetworking.send(new EconomyNetworking.WholesaleAdd(lotSize, price));
            }
        }).bounds(left + 144, 70, 110, 20).build();
        add.active = data.wholesale();
        addRenderableWidget(add);
        dealField(left, 94, 56, "Lots/qty", dealQuantity, text -> dealQuantity = text);
        dealField(left + 60, 94, 80, "Total", dealTotal, text -> dealTotal = text);

        List<DealLine> lines = dealLines();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < lines.size(); i++) {
            DealLine line = lines.get(i + scroll);
            int y = DEALS_TOP + i * ROW_HEIGHT - 4;
            switch (line.kind()) {
                case MINE -> addDealButton("Remove", left + 280, y, 60, () ->
                    ClientPlayNetworking.send(new EconomyNetworking.WholesaleRemove(line.offer().id())));
                case OFFER -> addDealButton("Order", left + 280, y, 60, () -> {
                    int lots = parseCount(dealQuantity);
                    if (lots > 0) {
                        ClientPlayNetworking.send(new EconomyNetworking.WholesaleOrderOffer(line.offer().id(), lots));
                    }
                });
                case LISTING -> addDealButton("Request", left + 270, y, 70, () -> {
                    int quantity = parseCount(dealQuantity);
                    long total = dealTotal.isBlank()
                        ? line.listing().price() * quantity
                        : Currency.parse(dealTotal);
                    if (quantity > 0 && total > 0) {
                        MarketRow row = line.listing();
                        ClientPlayNetworking.send(new EconomyNetworking.WholesaleRequest(
                            row.ownerId(),
                            new BlockPos(row.x(), row.y(), row.z()),
                            row.dimension(),
                            row.stack(),
                            quantity,
                            total));
                    }
                });
                case ORDER -> initOrderButtons(line.order(), left, y);
                case HEADER -> {
                }
            }
        }
    }

    private void initOrderButtons(WholesaleOrderRow order, int left, int y) {
        if (order.inTransit()) {
            return;
        }
        if (order.incoming()) {
            addDealButton("Accept", left + 218, y, 58, () -> ClientPlayNetworking.send(
                new EconomyNetworking.WholesaleRespond(order.id(), EconomyNetworking.WholesaleRespond.ACCEPT)));
            addDealButton("Decline", left + 280, y, 60, () -> ClientPlayNetworking.send(
                new EconomyNetworking.WholesaleRespond(order.id(), EconomyNetworking.WholesaleRespond.DECLINE)));
        } else {
            addDealButton("Cancel", left + 280, y, 60, () -> ClientPlayNetworking.send(
                new EconomyNetworking.WholesaleRespond(order.id(), EconomyNetworking.WholesaleRespond.CANCEL)));
        }
    }

    private void addDealButton(String label, int x, int y, int width, Runnable action) {
        Button button = Button.builder(Component.literal(label), b -> action.run())
            .bounds(x, y, width, 20).build();
        button.active = data.wholesale();
        addRenderableWidget(button);
    }

    private void renderDeals(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        context.text(this.font,
            "Set lots (or quantity) and an optional total, then Order or Request.",
            left + 148, 100, 0xFFAAAAAA);
        List<DealLine> lines = dealLines();
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < lines.size(); i++) {
            DealLine line = lines.get(i + scroll);
            int y = DEALS_TOP + i * ROW_HEIGHT;
            String first = "";
            String second = "";
            ItemStack icon = ItemStack.EMPTY;
            switch (line.kind()) {
                case HEADER -> context.text(this.font, line.text(), left, y + 4, 0xFFFFE066);
                case MINE, OFFER -> {
                    WholesaleOfferRow offer = line.offer();
                    icon = offer.stack();
                    first = offer.name() + "  x" + offer.lotSize() + " per lot  " + Currency.format(offer.lotPrice());
                    second = line.kind() == DealKind.MINE ? "your offer" : "by " + offer.seller();
                }
                case LISTING -> {
                    MarketRow row = line.listing();
                    icon = row.stack();
                    first = row.name() + "  " + Currency.format(row.price()) + " each";
                    second = "shop of " + row.owner() + " (request a quantity)";
                }
                case ORDER -> {
                    WholesaleOrderRow order = line.order();
                    icon = order.stack();
                    first = (order.incoming() ? "From " : "To ") + order.other() + ": "
                        + order.items() + " x " + order.name();
                    second = Currency.format(order.escrow()) + "  "
                        + (order.inTransit()
                            ? "in transit, " + order.remaining() + " left"
                            : order.incoming() ? "awaiting your answer" : "awaiting seller");
                }
            }
            if (line.kind() != DealKind.HEADER) {
                context.text(this.font,
                    this.font.plainSubstrByWidth(first, 190), left + ICON_WIDTH, y, 0xFFFFFFFF);
                context.text(this.font,
                    this.font.plainSubstrByWidth(second, 190), left + ICON_WIDTH, y + 10, 0xFFAAAAAA);
                drawItemRow(context, icon, left, y, mouseX, mouseY);
            }
        }
        if (!data.wholesale()) {
            context.text(this.font,
                "Stand at a depot you own to trade wholesale.", left, this.height - 60, 0xFFAAAAAA);
        }
    }

    private void initBonds() {
        int left = this.width / 2 - 170;
        int offerings = data.offerings().size();
        int total = offerings + data.vault().size();
        addRenderableWidget(Button.builder(Component.literal("Vault bonds"), b ->
                ClientPlayNetworking.send(new EconomyNetworking.BondVault()))
            .bounds(left, this.height - 28, 66, 20).build());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < total; i++) {
            int row = i + scroll;
            int y = LIST_TOP + i * ROW_HEIGHT - 4;
            if (row < offerings) {
                BondOfferingRow offering = data.offerings().get(row);
                if (offering.own()) {
                    addRenderableWidget(Button.builder(Component.literal("Withdraw"), b ->
                            ClientPlayNetworking.send(new EconomyNetworking.BondCancel(offering.id())))
                        .bounds(left + 280, y, 60, 20).build());
                } else {
                    Button buy = Button.builder(Component.literal("Invest"), b ->
                            ClientPlayNetworking.send(new EconomyNetworking.BondBuy(offering.id())))
                        .bounds(left + 280, y, 60, 20).build();
                    buy.active = data.balance() >= offering.principal();
                    addRenderableWidget(buy);
                }
            } else {
                VaultBondRow held = data.vault().get(row - offerings);
                addRenderableWidget(Button.builder(Component.literal("Take out"), b ->
                        ClientPlayNetworking.send(new EconomyNetworking.BondWithdraw(held.index())))
                    .bounds(left + 280, y, 60, 20).build());
            }
        }
    }

    private void renderBonds(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        int offerings = data.offerings().size();
        int total = offerings + data.vault().size();
        if (total == 0) {
            context.text(this.font,
                "No bonds on offer and none in your vault.", left, LIST_TOP, 0xFFAAAAAA);
            return;
        }
        ItemStack icon = new ItemStack(ModItems.BOND);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < total; i++) {
            int row = i + scroll;
            int y = LIST_TOP + i * ROW_HEIGHT;
            if (row < offerings) {
                BondOfferingRow offering = data.offerings().get(row);
                context.text(this.font,
                    offering.issuer() + "  " + Currency.format(offering.principal()) + "  "
                        + BondPaper.percent(offering.couponBps()) + " every " + offering.couponDays() + " days",
                    left + ICON_WIDTH, y, 0xFFFFFFFF);
                context.text(this.font,
                    "term " + offering.termDays() + " days, " + offering.remaining() + " left",
                    left + ICON_WIDTH, y + 10, 0xFFAAAAAA);
            } else {
                VaultBondRow held = data.vault().get(row - offerings);
                context.text(this.font, "Vaulted", left + ICON_WIDTH, y, 0xFFFFE066);
                context.text(this.font,
                    this.font.plainSubstrByWidth(held.text(), 210), left + ICON_WIDTH, y + 10, 0xFFAAAAAA);
            }
            drawItemRow(context, icon, left, y, mouseX, mouseY);
        }
    }

    private void initLoans() {
        int left = this.width / 2 - 170;
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.loans().size(); i++) {
            LoanRow loan = data.loans().get(i + scroll);
            if (!loan.owed()) {
                continue;
            }
            Button payOff = Button.builder(Component.literal("Pay off"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.LoanRepay(loan.id())))
                .bounds(left + 280, LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build();
            payOff.active = data.balance() >= loan.payoff();
            addRenderableWidget(payOff);
        }
    }

    private void renderLoans(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        String bank = data.bankLoanTerms().isEmpty()
            ? "The bank is not lending. Ask a player with /loan request."
            : "Bank lends " + data.bankLoanTerms() + ": /loan bank <amount> <payments>";
        context.text(this.font,
            this.font.plainSubstrByWidth(bank, 340), left, this.height - 44, 0xFFAAAAAA);
        if (data.loans().isEmpty()) {
            context.text(this.font, "You have no loans.", left, LIST_TOP, 0xFFAAAAAA);
            return;
        }
        ItemStack icon = new ItemStack(ModItems.LOAN_DEED);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.loans().size(); i++) {
            LoanRow loan = data.loans().get(i + scroll);
            int y = LIST_TOP + i * ROW_HEIGHT;
            int split = loan.text().indexOf(", ");
            String first = split < 0 ? loan.text() : loan.text().substring(0, split);
            String second = split < 0
                ? ""
                : loan.text().substring(split + 2) + (loan.owed() ? "  pay off " + Currency.format(loan.payoff()) : "");
            context.text(this.font,
                this.font.plainSubstrByWidth(first, 250), left + ICON_WIDTH, y, loan.owed() ? 0xFFFFE066 : 0xFFFFFFFF);
            context.text(this.font,
                this.font.plainSubstrByWidth(second, 250), left + ICON_WIDTH, y + 10, 0xFFAAAAAA);
            drawItemRow(context, icon, left, y, mouseX, mouseY);
        }
    }

    // The number of rows the active view can scroll through.
    private int scrollableTotal() {
        return switch (tab) {
            case MARKET -> {
                if (selectedShop == null) {
                    yield shopGroups().size();
                }
                yield selectedDepot == null ? selectedOwnerShops().size() : selectedDepotRows().size();
            }
            case SHOPS -> switch (activeShopView()) {
                case SERVER -> catalogRows().size();
                case MANAGE -> selectedMyDepot == null ? myShops().size() : myDepotRows().size();
                case CREATE -> 0;
            };
            case DEALS -> dealLines().size();
            case BONDS -> data.offerings().size() + data.vault().size();
            case LOANS -> data.loans().size();
            case HISTORY -> historyRows().size();
            case ADMIN -> data.library().size();
            case BANK -> 0;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        int total = scrollableTotal();
        int max = Math.max(0, total - ROWS_VISIBLE);
        if (max == 0 || verticalAmount == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int next = scroll + (verticalAmount > 0 ? -1 : 1);
        int clamped = Math.max(0, Math.min(max, next));
        if (clamped != scroll) {
            scroll = clamped;
            rebuildWidgets();
        }
        return true;
    }

    private void sendBank(boolean deposit, boolean chips) {
        long amount = Currency.parse(bankField.getValue());
        if (amount <= 0) {
            return;
        }
        ClientPlayNetworking.send(new EconomyNetworking.BankAction(deposit, chips, amount));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.centeredText(this.font,
            "Balance: " + Currency.format(data.balance()), this.width / 2, 56, 0xFFFFE066);

        switch (tab) {
            case BANK -> renderBank(context);
            case MARKET -> renderMarket(context, mouseX, mouseY);
            case SHOPS -> renderShops(context, mouseX, mouseY);
            case DEALS -> renderDeals(context, mouseX, mouseY);
            case BONDS -> renderBonds(context, mouseX, mouseY);
            case LOANS -> renderLoans(context, mouseX, mouseY);
            case HISTORY -> renderHistory(context);
            case ADMIN -> renderAdmin(context);
        }
    }

    private void renderAdmin(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        context.text(this.font,
            "Taxes collected (Treasury): " + Currency.format(data.treasury()), left, 74, 0xFFFFE066);
        context.text(this.font,
            "Items bought by players (history / available now)", left, 90, 0xFFFFFFFF);
        List<LibraryRow> library = data.library();
        if (library.isEmpty()) {
            context.text(this.font,
                "Nothing bought yet.", left, DETAIL_LIST_TOP + 10, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < library.size(); i++) {
            LibraryRow row = library.get(i + scroll);
            int y = DETAIL_LIST_TOP + 10 + i * ROW_HEIGHT;
            context.text(this.font, row.name(), left, y, 0xFFFFFFFF);
            context.text(this.font,
                "bought " + row.bought() + "  available " + row.count(), left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderHistory(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        List<HistoryEntry> rows = historyRows();
        if (!historyLoaded) {
            context.text(this.font, "Loading...", left, 100, 0xFFAAAAAA);
            return;
        }
        if (rows.isEmpty()) {
            context.text(this.font, "No entries.", left, 100, 0xFFAAAAAA);
            return;
        }
        int top = 100;
        int visible = Math.min(ROWS_VISIBLE + 1, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            HistoryEntry entry = rows.get(i + scroll);
            context.text(this.font, historyRow(entry), left, top + i * 14, 0xFFE0E0E0);
        }
    }

    private String historyRow(HistoryEntry entry) {
        StringBuilder builder = new StringBuilder("D").append(entry.day()).append("  ");
        if (!entry.item().isEmpty()) {
            builder.append(entry.item());
            if (entry.count() > 0) {
                builder.append(" x").append(entry.count());
            }
            builder.append("  ");
        }
        builder.append("net ").append(Currency.format(entry.net()))
            .append(" (tax ").append(Currency.format(entry.tax())).append(")");
        if (!entry.counterparty().isEmpty()) {
            builder.append("  ").append(entry.counterparty());
        }
        return builder.toString();
    }

    private void renderBank(GuiGraphicsExtractor context) {
        int centerX = this.width / 2;
        context.centeredText(this.font,
            "Pocket: " + Currency.format(data.pocket()), centerX, 70, 0xFFFFFFFF);
        if (data.bankOccupiedByOther()) {
            context.centeredText(this.font,
                "This bank is occupied. Try another.", centerX, 84, 0xFFFF8080);
        }

        context.centeredText(this.font,
            "1 Colt = " + data.coltRatio() + " Pice    Interest: "
                + data.interestPercent() + "% every " + data.interestDays() + " days",
            centerX, 188, 0xFFAAAAAA);
        context.centeredText(this.font,
            "Reach 2 blocks at the face.  A Colt/Pice block = "
                + info.mudbourn.mmseconomy.economy.Gems.blockUnits() + " gems.",
            centerX, 200, 0xFFAAAAAA);
    }

    private void renderMarket(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (selectedShop == null) {
            renderShopList(context);
        } else if (selectedDepot == null) {
            renderOwnerShops(context);
        } else {
            renderDepotItems(context, mouseX, mouseY);
        }
        renderMarketHint(context);
    }

    private void renderShopList(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        List<Map.Entry<String, List<MarketRow>>> groups = shopGroups();
        if (groups.isEmpty()) {
            context.text(this.font, "No shops yet.", left, LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < groups.size(); i++) {
            Map.Entry<String, List<MarketRow>> group = groups.get(i + scroll);
            int y = LIST_TOP + i * ROW_HEIGHT;
            context.text(this.font, group.getKey(), left, y, 0xFFFFFFFF);
            int count = group.getValue().size();
            context.text(this.font,
                count + (count == 1 ? " listing" : " listings"), left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderOwnerShops(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        context.text(this.font,
            selectedShop + "'s shops", left + 68, 76, 0xFFFFFFFF);
        List<ShopEntry> shops = selectedOwnerShops();
        if (shops.isEmpty()) {
            context.text(this.font, "No shops.", left, DETAIL_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            ShopEntry shop = shops.get(i + scroll);
            BlockPos depot = shop.depot();
            int y = DETAIL_LIST_TOP + i * ROW_HEIGHT;
            context.text(this.font, shopLabel(shop.name(), depot), left, y, 0xFFFFFFFF);
            context.text(this.font, shopDetail(depot, shop.name(), shop.rows().size()),
                left, y + 10, 0xFFAAAAAA);
        }
    }

    // A shop row's second line: coordinates when the name hides them, distance, and item count.
    private String shopDetail(BlockPos depot, String name, int count) {
        String coords = name.isEmpty() ? "" : "@ " + depot.getX() + "," + depot.getY() + "," + depot.getZ() + "  ";
        return coords + Math.round(Math.sqrt(distanceTo(depot))) + " blocks away  "
            + count + (count == 1 ? " item" : " items");
    }

    // Draws a listing's item icon and, when the mouse is over its row, the full item tooltip.
    private void drawItemRow(GuiGraphicsExtractor context, ItemStack stack, int left, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        context.item(stack, left, y - 4);
        if (mouseX >= left && mouseX < left + 270 && mouseY >= y - 4 && mouseY < y - 4 + ROW_HEIGHT) {
            context.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
        }
    }

    private void renderDepotItems(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        List<MarketRow> rows = selectedDepotRows();
        String name = rows.isEmpty() ? "" : rows.get(0).shopName();
        context.text(this.font,
            name.isEmpty()
                ? selectedShop + " @ " + selectedDepot.getX() + ","
                    + selectedDepot.getY() + "," + selectedDepot.getZ()
                : name + " (" + selectedShop + ")",
            left + 172, 76, 0xFFFFFFFF);
        if (rows.isEmpty()) {
            context.text(this.font, "No listings.", left, DETAIL_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            MarketRow row = rows.get(i + scroll);
            int y = DETAIL_LIST_TOP + i * ROW_HEIGHT;
            context.text(this.font,
                row.name() + "  " + Currency.format(row.price()), left + ICON_WIDTH, y, 0xFFFFFFFF);
            String detail = row.buyable()
                ? "in reach"
                : "@ " + row.x() + "," + row.y() + "," + row.z();
            context.text(this.font, detail, left + ICON_WIDTH, y + 10,
                row.buyable() ? 0xFF88CC88 : 0xFFAAAAAA);
            drawItemRow(context, row.stack(), left, y, mouseX, mouseY);
        }
    }

    private void renderMarketHint(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        boolean canBuyAny = false;
        for (MarketRow row : data.listings()) {
            if (row.buyable()) {
                canBuyAny = true;
                break;
            }
        }
        String hint;
        if (data.nearDepot()) {
            hint = "Buy listings in reach and manage your shop under Shops.";
        } else if (canBuyAny) {
            hint = "Green listings are in reach. Buy them here.";
        } else {
            hint = "Browse only. Get near a shop's depot or owner to buy.";
        }
        context.text(this.font, hint, left, this.height - 60, 0xFFAAAAAA);
    }

    private void renderShops(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        switch (activeShopView()) {
            case CREATE -> renderShopCreate(context);
            case SERVER -> renderServerShop(context, mouseX, mouseY);
            case MANAGE -> {
                if (selectedMyDepot == null) {
                    renderMyShopList(context);
                } else {
                    renderMyShop(context, mouseX, mouseY);
                }
            }
        }
    }

    private void renderMyShopList(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        List<MyShop> shops = myShops();
        if (shops.isEmpty()) {
            context.text(this.font,
                "You have no shop yet. Use New shop to open one.", left, 100, 0xFFAAAAAA);
            return;
        }
        context.text(this.font, "Your shops", left, MY_SHOPS_TOP - 14, 0xFFFFFFFF);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            MyShop shop = shops.get(i + scroll);
            int y = MY_SHOPS_TOP + i * ROW_HEIGHT;
            context.text(this.font, shopLabel(shop.name(), shop.depot()), left, y, 0xFFFFFFFF);
            context.text(this.font, shopDetail(shop.depot(), shop.name(), shop.rows().size()),
                left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderMyShop(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        List<ShopRow> rows = myDepotRows();
        String name = rows.isEmpty() ? "" : rows.get(0).shopName();
        context.text(this.font, shopLabel(name, selectedMyDepot), left + 68, 76, 0xFFFFFFFF);
        context.text(this.font, "Listings", left, SHOP_LIST_TOP - 14, 0xFFFFFFFF);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ShopRow row = rows.get(i + scroll);
            int y = SHOP_LIST_TOP + i * ROW_HEIGHT;
            context.text(this.font,
                row.name() + "  " + Currency.format(row.price()) + "  stock " + row.stock(),
                left + ICON_WIDTH, y + 2, 0xFFFFFFFF);
            drawItemRow(context, row.stack(), left, y + 2, mouseX, mouseY);
        }
    }

    private void renderShopCreate(GuiGraphicsExtractor context) {
        int left = this.width / 2 - 170;
        context.text(this.font, "Open a new shop", left + 68, 76, 0xFFFFFFFF);
        context.text(this.font,
            "1. Stand in front of and look directly at the 2x2 barrel wall.", left, 100, 0xFFE0E0E0);
        context.text(this.font,
            "2. Hold the item to sell and set its price.", left, 112, 0xFFE0E0E0);
        context.text(this.font,
            "Opening a new wall costs a one-time setup fee.", left, 158, 0xFFAAAAAA);
    }

    // Builds a display stack for a server shop item id, empty when the id is unknown.
    private static ItemStack catalogStack(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
    }

    private void renderServerShop(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        context.text(this.font,
            serverShopBuy ? "Buy from server (1.5x in stock, 3x minted)" : "Sell to server",
            left, 118, 0xFFFFE066);
        if (!data.nearDepot()) {
            context.text(this.font,
                "Browse only. Stand at your own depot to trade.", left, this.height - 60, 0xFFAAAAAA);
        }
        List<ServerShopRow> rows = catalogRows();
        if (rows.isEmpty()) {
            context.text(this.font,
                "No items in this category.", left, SERVER_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ServerShopRow row = rows.get(i + scroll);
            int y = SERVER_LIST_TOP + i * ROW_HEIGHT;
            long price = serverShopBuy ? row.buyPrice() : row.sell();
            context.text(this.font,
                row.name() + "  " + Currency.format(price), left + ICON_WIDTH, y, 0xFFFFFFFF);
            String detail = serverShopBuy
                ? (row.stock() > 0 ? "in stock " + row.stock() : "minted to order")
                : "server pays " + Currency.format(row.sell());
            context.text(this.font, detail, left + ICON_WIDTH, y + 10, 0xFFAAAAAA);
            drawItemRow(context, catalogStack(row.itemId()), left, y, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        ClientPlayNetworking.send(new EconomyNetworking.CloseHub());
        super.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (!(this.getFocused() instanceof EditBox) && MmsEconomyClient.openHub.matches(input)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
