package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.network.EconomyNetworking.LibraryRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.MarketRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.OpenHub;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ServerShopRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ShopRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

// The economy hub: a tab row over a balance header, opened by /eco or a bank block.
public final class HubScreen extends Screen {

    private enum Tab {
        MARKET,
        BANK,
        SHOPS,
        HISTORY,
        ADMIN
    }

    // The three server-decided hub variants, mirrored from OpenHub.mode.
    private static final int MODE_PORTABLE = 0;

    private static final int MODE_MERCHANT = 1;

    private static final int MODE_BANKING = 2;

    private static final int LIST_TOP = 78;

    private static final int MY_SHOPS_TOP = 110;

    private static final int SHOP_LIST_TOP = 152;

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

    private HistoryCategory historyTab = HistoryCategory.PURCHASE;

    private boolean historyLoaded;

    private TextFieldWidget bankField;

    private TextFieldWidget priceField;

    private TextFieldWidget nameField;

    // The owner grouping of the current snapshot's listings, rebuilt only when the snapshot changes.
    private List<Map.Entry<String, List<MarketRow>>> shopGroupsCache;

    private OpenHub shopGroupsSource;

    public HubScreen(OpenHub data) {
        super(Text.literal("Economy"));
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
        clearAndInit();
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
            case SHOPS -> data.mode() == MODE_MERCHANT && data.nearDepot();
            case HISTORY -> true;
            case ADMIN -> data.admin();
        };
    }

    private static String tabLabel(Tab target) {
        return switch (target) {
            case MARKET -> "Market";
            case BANK -> "Bank";
            case SHOPS -> "Shops";
            case HISTORY -> "History";
            case ADMIN -> "Admin";
        };
    }

    public void applyHistory(List<HistoryEntry> purchases, List<HistoryEntry> payments,
                             List<HistoryEntry> deals) {
        this.historyPurchases = purchases;
        this.historyPayments = payments;
        this.historyDeals = deals;
        this.historyLoaded = true;
        if (tab == Tab.HISTORY) {
            clearAndInit();
        }
    }

    private List<HistoryEntry> historyRows() {
        return switch (historyTab) {
            case PURCHASE -> historyPurchases;
            case PAYMENT -> historyPayments;
            case DEAL -> historyDeals;
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());

        switch (tab) {
            case BANK -> initBank();
            case MARKET -> initMarket();
            case SHOPS -> initShops();
            case HISTORY -> initHistory();
            case ADMIN -> initAdmin();
        }
    }

    private void initAdmin() {
        addScrollButtons(data.library().size());
    }

    private void initHistory() {
        if (!historyLoaded) {
            ClientPlayNetworking.send(new EconomyNetworking.RequestHistory());
        }
        int width = 90;
        int startX = this.width / 2 - width * 3 / 2 - 4;
        addHistoryTab("Purchases", HistoryCategory.PURCHASE, startX, width);
        addHistoryTab("Payments", HistoryCategory.PAYMENT, startX + width + 4, width);
        addHistoryTab("Deals", HistoryCategory.DEAL, startX + (width + 4) * 2, width);
        addScrollButtons(historyRows().size());
    }

    private void addHistoryTab(String label, HistoryCategory target, int x, int width) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> {
            this.historyTab = target;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(x, 74, width, 20).build();
        button.active = target != historyTab;
        addDrawableChild(button);
    }

    private void addTab(String label, Tab target, int x, int width) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(label), b -> {
            this.tab = target;
            this.scroll = 0;
            this.selectedShop = null;
            this.selectedDepot = null;
            this.selectedMyDepot = null;
            this.shopView = ShopView.MANAGE;
            clearAndInit();
        }).dimensions(x, 28, width, 20).build();
        button.active = target != tab;
        addDrawableChild(button);
    }

    private void initBank() {
        int centerX = this.width / 2;
        bankField = new TextFieldWidget(this.textRenderer, centerX - 80, 96, 160, 20, Text.literal("Amount"));
        bankField.setPlaceholder(Text.literal("12.34"));
        addDrawableChild(bankField);
        setInitialFocus(bankField);

        boolean usable = data.hasBank() && !data.bankOccupiedByOther();
        ButtonWidget deposit = ButtonWidget.builder(Text.literal("Deposit"), b -> sendBank(true))
            .dimensions(centerX - 80, 122, 78, 20).build();
        ButtonWidget withdraw = ButtonWidget.builder(Text.literal("Withdraw"), b -> sendBank(false))
            .dimensions(centerX + 2, 122, 78, 20).build();
        deposit.active = usable;
        withdraw.active = usable;
        addDrawableChild(deposit);
        addDrawableChild(withdraw);
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
        if (this.client == null || this.client.player == null) {
            return 0.0;
        }
        double dx = this.client.player.getX() - (depot.getX() + 0.5);
        double dy = this.client.player.getY() - (depot.getY() + 0.5);
        double dz = this.client.player.getZ() - (depot.getZ() + 0.5);
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
        addScrollButtons(groups.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < groups.size(); i++) {
            String owner = groups.get(i + scroll).getKey();
            addDrawableChild(ButtonWidget.builder(Text.literal("View"), b -> {
                this.selectedShop = owner;
                this.scroll = 0;
                clearAndInit();
            }).dimensions(left + 280, LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initOwnerShops() {
        int left = this.width / 2 - 170;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.selectedShop = null;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal(shopSortInverted ? "Farthest" : "Closest"), b -> {
                this.shopSortInverted = !this.shopSortInverted;
                this.scroll = 0;
                clearAndInit();
            }).dimensions(left + 240, 70, 100, 20).build());

        List<ShopEntry> shops = selectedOwnerShops();
        addScrollButtons(shops.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            ShopEntry shop = shops.get(i + scroll);
            addDrawableChild(ButtonWidget.builder(Text.literal("View"), b -> {
                this.selectedDepot = shop.depot();
                this.scroll = 0;
                clearAndInit();
            }).dimensions(left + 280, DETAIL_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initDepotItems() {
        int left = this.width / 2 - 170;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.selectedDepot = null;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Get shop book"), b ->
                ClientPlayNetworking.send(new EconomyNetworking.RequestShopBook(selectedShop, selectedDepot)))
            .dimensions(left + 66, 70, 100, 20).build());

        List<MarketRow> rows = selectedDepotRows();
        addScrollButtons(rows.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            MarketRow row = rows.get(i + scroll);
            ButtonWidget buy = ButtonWidget.builder(Text.literal("Buy"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.BuyListing(row.index())))
                .dimensions(left + 280, DETAIL_LIST_TOP + i * ROW_HEIGHT - 4, 50, 20).build();
            buy.active = row.buyable();
            addDrawableChild(buy);
        }
    }

    private void initShops() {
        switch (shopView) {
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
        addDrawableChild(ButtonWidget.builder(Text.literal("New shop"), b -> {
            this.shopView = ShopView.CREATE;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Server shop"), b -> {
            this.shopView = ShopView.SERVER;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left + 106, 70, 100, 20).build());

        List<MyShop> shops = myShops();
        addScrollButtons(shops.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            MyShop shop = shops.get(i + scroll);
            addDrawableChild(ButtonWidget.builder(Text.literal("Manage"), b -> {
                this.selectedMyDepot = shop.depot();
                this.scroll = 0;
                clearAndInit();
            }).dimensions(left + 280, MY_SHOPS_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initMyShop() {
        int left = this.width / 2 - 170;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.selectedMyDepot = null;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());

        List<ShopRow> rows = myDepotRows();
        nameField = new TextFieldWidget(this.textRenderer, left, 100, 150, 20, Text.literal("Shop name"));
        nameField.setMaxLength(info.mudbourn.mmseconomy.market.Marketplace.MAX_SHOP_NAME);
        nameField.setPlaceholder(Text.literal("Shop name"));
        nameField.setText(rows.isEmpty() ? "" : rows.get(0).shopName());
        addDrawableChild(nameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), b ->
                ClientPlayNetworking.send(new EconomyNetworking.RenameShop(selectedMyDepot, nameField.getText())))
            .dimensions(left + 156, 100, 80, 20).build());

        priceField = new TextFieldWidget(this.textRenderer, left, 124, 90, 20, Text.literal("Price"));
        priceField.setPlaceholder(Text.literal("12.34"));
        addDrawableChild(priceField);
        addDrawableChild(ButtonWidget.builder(Text.literal("List held item here"), b -> {
            long price = Currency.parse(priceField.getText());
            if (price > 0) {
                ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price, selectedMyDepot));
            }
        }).dimensions(left + 96, 124, 140, 20).build());

        addScrollButtons(rows.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ShopRow row = rows.get(i + scroll);
            addDrawableChild(ButtonWidget.builder(Text.literal("Unlist"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.Unlist(row.index())))
                .dimensions(left + 280, SHOP_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void initShopCreate() {
        int left = this.width / 2 - 170;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.shopView = ShopView.MANAGE;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());

        priceField = new TextFieldWidget(this.textRenderer, left, 132, 90, 20, Text.literal("Price"));
        priceField.setPlaceholder(Text.literal("12.34"));
        addDrawableChild(priceField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Open shop with held item"), b -> {
            long price = Currency.parse(priceField.getText());
            if (price > 0) {
                ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price, null));
                this.shopView = ShopView.MANAGE;
                this.scroll = 0;
                clearAndInit();
            }
        }).dimensions(left + 96, 132, 180, 20).build());
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
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.shopView = ShopView.MANAGE;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());
        ButtonWidget buy = ButtonWidget.builder(Text.literal("Buy"), b -> {
            this.serverShopBuy = true;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left + 216, 70, 60, 20).build();
        buy.active = !serverShopBuy;
        addDrawableChild(buy);
        ButtonWidget sell = ButtonWidget.builder(Text.literal("Sell"), b -> {
            this.serverShopBuy = false;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left + 280, 70, 60, 20).build();
        sell.active = serverShopBuy;
        addDrawableChild(sell);

        int catWidth = 110;
        for (int c = 0; c < CATEGORY_LABELS.length; c++) {
            int category = c;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(CATEGORY_LABELS[c]), b -> {
                this.serverShopCategory = category;
                this.scroll = 0;
                clearAndInit();
            }).dimensions(left + c * (catWidth + 4), 94, catWidth, 20).build();
            tab.active = category != serverShopCategory;
            addDrawableChild(tab);
        }

        List<ServerShopRow> rows = catalogRows();
        addScrollButtons(rows.size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ServerShopRow row = rows.get(i + scroll);
            String label = serverShopBuy ? "Buy" : "Sell";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                if (serverShopBuy) {
                    ClientPlayNetworking.send(new EconomyNetworking.ServerBuy(row.itemId(), 1));
                } else {
                    ClientPlayNetworking.send(new EconomyNetworking.ServerSell(row.itemId(), 1));
                }
            }).dimensions(left + 280, SERVER_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
        }
    }

    private void addScrollButtons(int total) {
        if (total <= ROWS_VISIBLE) {
            return;
        }
        int x = this.width / 2 + 150;
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> {
            scroll = Math.max(0, scroll - 1);
            clearAndInit();
        }).dimensions(x, 52, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dn"), b -> {
            scroll = Math.min(total - ROWS_VISIBLE, scroll + 1);
            clearAndInit();
        }).dimensions(x + 32, 52, 30, 20).build());
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
            case SHOPS -> switch (shopView) {
                case SERVER -> catalogRows().size();
                case MANAGE -> selectedMyDepot == null ? myShops().size() : myDepotRows().size();
                case CREATE -> 0;
            };
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
            clearAndInit();
        }
        return true;
    }

    private void sendBank(boolean deposit) {
        long amount = Currency.parse(bankField.getText());
        if (amount <= 0) {
            return;
        }
        ClientPlayNetworking.send(new EconomyNetworking.BankAction(deposit, amount));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Balance: " + Currency.format(data.balance()), this.width / 2, 56, 0xFFFFE066);

        switch (tab) {
            case BANK -> renderBank(context);
            case MARKET -> renderMarket(context, mouseX, mouseY);
            case SHOPS -> renderShops(context, mouseX, mouseY);
            case HISTORY -> renderHistory(context);
            case ADMIN -> renderAdmin(context);
        }
    }

    private void renderAdmin(DrawContext context) {
        int left = this.width / 2 - 170;
        context.drawTextWithShadow(this.textRenderer,
            "Taxes collected (Treasury): " + Currency.format(data.treasury()), left, 74, 0xFFFFE066);
        context.drawTextWithShadow(this.textRenderer,
            "Items bought by players (history / available now)", left, 90, 0xFFFFFFFF);
        List<LibraryRow> library = data.library();
        if (library.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                "Nothing bought yet.", left, DETAIL_LIST_TOP + 10, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < library.size(); i++) {
            LibraryRow row = library.get(i + scroll);
            int y = DETAIL_LIST_TOP + 10 + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer, row.name(), left, y, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer,
                "bought " + row.bought() + "  available " + row.count(), left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderHistory(DrawContext context) {
        int left = this.width / 2 - 170;
        List<HistoryEntry> rows = historyRows();
        if (!historyLoaded) {
            context.drawTextWithShadow(this.textRenderer, "Loading...", left, 100, 0xFFAAAAAA);
            return;
        }
        if (rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No entries.", left, 100, 0xFFAAAAAA);
            return;
        }
        int top = 100;
        int visible = Math.min(ROWS_VISIBLE + 1, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            HistoryEntry entry = rows.get(i + scroll);
            context.drawTextWithShadow(this.textRenderer, historyRow(entry), left, top + i * 14, 0xFFE0E0E0);
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

    private void renderBank(DrawContext context) {
        int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Pocket: " + Currency.format(data.pocket()), centerX, 70, 0xFFFFFFFF);
        if (data.bankOccupiedByOther()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "This bank is occupied. Try another.", centerX, 84, 0xFFFF8080);
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
            "1 Colt = " + data.coltRatio() + " Pice    Interest: "
                + data.interestPercent() + "% every " + data.interestDays() + " days",
            centerX, 188, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Reach 2 blocks at the face.  A Colt/Pice block = "
                + info.mudbourn.mmseconomy.economy.Gems.blockUnits() + " gems.",
            centerX, 200, 0xFFAAAAAA);
    }

    private void renderMarket(DrawContext context, int mouseX, int mouseY) {
        if (selectedShop == null) {
            renderShopList(context);
        } else if (selectedDepot == null) {
            renderOwnerShops(context);
        } else {
            renderDepotItems(context, mouseX, mouseY);
        }
        renderMarketHint(context);
    }

    private void renderShopList(DrawContext context) {
        int left = this.width / 2 - 170;
        List<Map.Entry<String, List<MarketRow>>> groups = shopGroups();
        if (groups.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No shops yet.", left, LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < groups.size(); i++) {
            Map.Entry<String, List<MarketRow>> group = groups.get(i + scroll);
            int y = LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer, group.getKey(), left, y, 0xFFFFFFFF);
            int count = group.getValue().size();
            context.drawTextWithShadow(this.textRenderer,
                count + (count == 1 ? " listing" : " listings"), left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderOwnerShops(DrawContext context) {
        int left = this.width / 2 - 170;
        context.drawTextWithShadow(this.textRenderer,
            selectedShop + "'s shops", left + 68, 76, 0xFFFFFFFF);
        List<ShopEntry> shops = selectedOwnerShops();
        if (shops.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No shops.", left, DETAIL_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            ShopEntry shop = shops.get(i + scroll);
            BlockPos depot = shop.depot();
            int y = DETAIL_LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer, shopLabel(shop.name(), depot), left, y, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, shopDetail(depot, shop.name(), shop.rows().size()),
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
    private void drawItemRow(DrawContext context, ItemStack stack, int left, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            return;
        }
        context.drawItem(stack, left, y - 4);
        if (mouseX >= left && mouseX < left + 270 && mouseY >= y - 4 && mouseY < y - 4 + ROW_HEIGHT) {
            context.drawItemTooltip(this.textRenderer, stack, mouseX, mouseY);
        }
    }

    private void renderDepotItems(DrawContext context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        List<MarketRow> rows = selectedDepotRows();
        String name = rows.isEmpty() ? "" : rows.get(0).shopName();
        context.drawTextWithShadow(this.textRenderer,
            name.isEmpty()
                ? selectedShop + " @ " + selectedDepot.getX() + ","
                    + selectedDepot.getY() + "," + selectedDepot.getZ()
                : name + " (" + selectedShop + ")",
            left + 68, 76, 0xFFFFFFFF);
        if (rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No listings.", left, DETAIL_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            MarketRow row = rows.get(i + scroll);
            int y = DETAIL_LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()), left + ICON_WIDTH, y, 0xFFFFFFFF);
            String detail = row.buyable()
                ? "in reach"
                : "@ " + row.x() + "," + row.y() + "," + row.z();
            context.drawTextWithShadow(this.textRenderer, detail, left + ICON_WIDTH, y + 10,
                row.buyable() ? 0xFF88CC88 : 0xFFAAAAAA);
            drawItemRow(context, row.stack(), left, y, mouseX, mouseY);
        }
    }

    private void renderMarketHint(DrawContext context) {
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
        context.drawTextWithShadow(this.textRenderer, hint, left, this.height - 60, 0xFFAAAAAA);
    }

    private void renderShops(DrawContext context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        if (!data.nearDepot()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "Stand near your 2x2 barrel depot to manage a shop.", this.width / 2, 96, 0xFFAAAAAA);
            return;
        }
        switch (shopView) {
            case CREATE -> renderShopCreate(context);
            case SERVER -> renderServerShop(context);
            case MANAGE -> {
                if (selectedMyDepot == null) {
                    renderMyShopList(context);
                } else {
                    renderMyShop(context, mouseX, mouseY);
                }
            }
        }
    }

    private void renderMyShopList(DrawContext context) {
        int left = this.width / 2 - 170;
        List<MyShop> shops = myShops();
        if (shops.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                "You have no shop yet. Use New shop to open one.", left, 100, 0xFFAAAAAA);
            return;
        }
        context.drawTextWithShadow(this.textRenderer, "Your shops", left, MY_SHOPS_TOP - 14, 0xFFFFFFFF);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < shops.size(); i++) {
            MyShop shop = shops.get(i + scroll);
            int y = MY_SHOPS_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer, shopLabel(shop.name(), shop.depot()), left, y, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, shopDetail(shop.depot(), shop.name(), shop.rows().size()),
                left, y + 10, 0xFFAAAAAA);
        }
    }

    private void renderMyShop(DrawContext context, int mouseX, int mouseY) {
        int left = this.width / 2 - 170;
        List<ShopRow> rows = myDepotRows();
        String name = rows.isEmpty() ? "" : rows.get(0).shopName();
        context.drawTextWithShadow(this.textRenderer, shopLabel(name, selectedMyDepot), left + 68, 76, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "Listings", left, SHOP_LIST_TOP - 14, 0xFFFFFFFF);
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ShopRow row = rows.get(i + scroll);
            int y = SHOP_LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()) + "  stock " + row.stock(),
                left + ICON_WIDTH, y + 2, 0xFFFFFFFF);
            drawItemRow(context, row.stack(), left, y + 2, mouseX, mouseY);
        }
    }

    private void renderShopCreate(DrawContext context) {
        int left = this.width / 2 - 170;
        context.drawTextWithShadow(this.textRenderer, "Open a new shop", left + 68, 76, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
            "1. Stand in front of and look directly at the 2x2 barrel wall.", left, 100, 0xFFE0E0E0);
        context.drawTextWithShadow(this.textRenderer,
            "2. Hold the item to sell and set its price.", left, 112, 0xFFE0E0E0);
        context.drawTextWithShadow(this.textRenderer,
            "Opening a new wall costs a one-time setup fee.", left, 158, 0xFFAAAAAA);
    }

    private void renderServerShop(DrawContext context) {
        int left = this.width / 2 - 170;
        context.drawTextWithShadow(this.textRenderer,
            serverShopBuy ? "Buy from server (1.5x in stock, 3x minted)" : "Sell to server",
            left, 118, 0xFFFFE066);
        List<ServerShopRow> rows = catalogRows();
        if (rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                "No items in this category.", left, SERVER_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            ServerShopRow row = rows.get(i + scroll);
            int y = SERVER_LIST_TOP + i * ROW_HEIGHT;
            long price = serverShopBuy ? row.buyPrice() : row.sell();
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(price), left, y, 0xFFFFFFFF);
            String detail = serverShopBuy
                ? (row.stock() > 0 ? "in stock " + row.stock() : "minted to order")
                : "server pays " + Currency.format(row.sell());
            context.drawTextWithShadow(this.textRenderer, detail, left, y + 10, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        ClientPlayNetworking.send(new EconomyNetworking.CloseHub());
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
