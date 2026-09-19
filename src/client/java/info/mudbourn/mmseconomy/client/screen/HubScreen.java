package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.network.EconomyNetworking.MarketRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.OpenHub;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ShopRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

// The economy hub: a tab row over a balance header, opened by /eco or a bank block.
public final class HubScreen extends Screen {

    private enum Tab {
        MARKET,
        BANK,
        SHOPS,
        HISTORY
    }

    // The three server-decided hub variants, mirrored from OpenHub.mode.
    private static final int MODE_PORTABLE = 0;

    private static final int MODE_MERCHANT = 1;

    private static final int MODE_BANKING = 2;

    private static final int LIST_TOP = 78;

    private static final int SHOP_LIST_TOP = 140;

    private static final int DETAIL_LIST_TOP = 96;

    private static final int ROW_HEIGHT = 22;

    private static final int ROWS_VISIBLE = 9;

    private OpenHub data;

    private Tab tab;

    private int scroll;

    // The market owner whose listings are open; null shows the shop list.
    private String selectedShop;

    private List<HistoryEntry> historyPurchases = List.of();

    private List<HistoryEntry> historyPayments = List.of();

    private List<HistoryEntry> historyDeals = List.of();

    private HistoryCategory historyTab = HistoryCategory.PURCHASE;

    private boolean historyLoaded;

    private TextFieldWidget bankField;

    private TextFieldWidget priceField;

    private TextFieldWidget sellField;

    public HubScreen(OpenHub data) {
        super(Text.literal("Economy"));
        this.data = data;
        this.tab = defaultTab();
    }

    public void apply(OpenHub data) {
        this.data = data;
        if (!tabAllowed(this.tab)) {
            this.tab = defaultTab();
            this.scroll = 0;
        }
        clearAndInit();
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
        };
    }

    private static String tabLabel(Tab target) {
        return switch (target) {
            case MARKET -> "Market";
            case BANK -> "Bank";
            case SHOPS -> "Shops";
            case HISTORY -> "History";
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
        }
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
        Map<String, List<MarketRow>> byOwner = new TreeMap<>();
        for (MarketRow row : data.listings()) {
            byOwner.computeIfAbsent(row.owner(), k -> new ArrayList<>()).add(row);
        }
        return new ArrayList<>(byOwner.entrySet());
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

    private void initMarket() {
        if (selectedShop == null) {
            initShopList();
        } else {
            initShopListings();
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

    private void initShopListings() {
        int left = this.width / 2 - 170;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            this.selectedShop = null;
            this.scroll = 0;
            clearAndInit();
        }).dimensions(left, 70, 60, 20).build());

        List<MarketRow> rows = selectedShopRows();
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
        int left = this.width / 2 - 170;
        if (data.nearDepot()) {
            priceField = new TextFieldWidget(this.textRenderer, left, 70, 90, 20, Text.literal("Price"));
            priceField.setPlaceholder(Text.literal("12.34"));
            addDrawableChild(priceField);
            addDrawableChild(ButtonWidget.builder(Text.literal("List held item"), b -> {
                long price = Currency.parse(priceField.getText());
                if (price > 0) {
                    ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price));
                }
            }).dimensions(left + 96, 70, 110, 20).build());

            sellField = new TextFieldWidget(this.textRenderer, left, 94, 40, 20, Text.literal("Count"));
            sellField.setPlaceholder(Text.literal("1"));
            addDrawableChild(sellField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Sell held to server"), b -> {
                long count = parseCount(sellField.getText());
                ClientPlayNetworking.send(new EconomyNetworking.ServerSell(count));
            }).dimensions(left + 46, 94, 160, 20).build());
        }

        addScrollButtons(data.myListings().size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.myListings().size(); i++) {
            ShopRow row = data.myListings().get(i + scroll);
            addDrawableChild(ButtonWidget.builder(Text.literal("Unlist"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.Unlist(row.index())))
                .dimensions(left + 280, SHOP_LIST_TOP + i * ROW_HEIGHT - 4, 60, 20).build());
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
            case MARKET -> selectedShop == null ? shopGroups().size() : selectedShopRows().size();
            case SHOPS -> data.myListings().size();
            case HISTORY -> historyRows().size();
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

    private long parseCount(String text) {
        try {
            return Math.max(1L, Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Balance: " + Currency.format(data.balance()), this.width / 2, 56, 0xFFFFE066);

        switch (tab) {
            case BANK -> renderBank(context);
            case MARKET -> renderMarket(context);
            case SHOPS -> renderShops(context);
            case HISTORY -> renderHistory(context);
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

    private void renderMarket(DrawContext context) {
        if (selectedShop == null) {
            renderShopList(context);
        } else {
            renderShopListings(context);
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

    private void renderShopListings(DrawContext context) {
        int left = this.width / 2 - 170;
        context.drawTextWithShadow(this.textRenderer,
            selectedShop + "'s shop", left + 68, 76, 0xFFFFFFFF);
        List<MarketRow> rows = selectedShopRows();
        if (rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No listings.", left, DETAIL_LIST_TOP, 0xFFAAAAAA);
            return;
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < rows.size(); i++) {
            MarketRow row = rows.get(i + scroll);
            int y = DETAIL_LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()), left, y, 0xFFFFFFFF);
            String detail = row.buyable()
                ? "in reach"
                : "@ " + row.x() + "," + row.y() + "," + row.z();
            context.drawTextWithShadow(this.textRenderer, detail, left, y + 10,
                row.buyable() ? 0xFF88CC88 : 0xFFAAAAAA);
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

    private void renderShops(DrawContext context) {
        int left = this.width / 2 - 170;
        if (!data.nearDepot()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "Stand near your 2x2 barrel depot to manage a shop.", this.width / 2, 96, 0xFFAAAAAA);
            return;
        }

        StringBuilder accepted = new StringBuilder();
        for (EconomyNetworking.BuyRow row : data.serverBuy()) {
            if (accepted.length() > 0) {
                accepted.append(", ");
            }
            accepted.append(row.name()).append(" ").append(Currency.format(row.price()));
        }
        context.drawTextWithShadow(this.textRenderer,
            "Server buys: " + (accepted.length() == 0 ? "nothing" : accepted.toString()),
            left, 118, 0xFFFFE066);

        context.drawTextWithShadow(this.textRenderer, "Your listings", left, SHOP_LIST_TOP - 14, 0xFFFFFFFF);
        if (data.myListings().isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                "None yet. Hold an item, set a price, and List.", left, SHOP_LIST_TOP, 0xFFAAAAAA);
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.myListings().size(); i++) {
            ShopRow row = data.myListings().get(i + scroll);
            int y = SHOP_LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()) + "  stock " + row.stock(),
                left, y + 2, 0xFFFFFFFF);
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
