package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.network.EconomyNetworking;
import info.mudbourn.mmseconomy.network.EconomyNetworking.MarketRow;
import info.mudbourn.mmseconomy.network.EconomyNetworking.OpenHub;
import info.mudbourn.mmseconomy.network.EconomyNetworking.ShopRow;

import java.util.List;
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

    private static final int LIST_TOP = 78;

    private static final int ROW_HEIGHT = 22;

    private static final int ROWS_VISIBLE = 6;

    private OpenHub data;

    private Tab tab = Tab.BANK;

    private int scroll;

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
    }

    public void apply(OpenHub data) {
        this.data = data;
        clearAndInit();
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
        int tabWidth = 80;
        int startX = this.width / 2 - tabWidth * 2 - 6;
        addTab("Market", Tab.MARKET, startX, tabWidth);
        addTab("Bank", Tab.BANK, startX + tabWidth + 4, tabWidth);
        addTab("Shops", Tab.SHOPS, startX + (tabWidth + 4) * 2, tabWidth);
        addTab("History", Tab.HISTORY, startX + (tabWidth + 4) * 3, tabWidth);

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

    private void initMarket() {
        int left = this.width / 2 - 170;
        addScrollButtons(data.listings().size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.listings().size(); i++) {
            MarketRow row = data.listings().get(i + scroll);
            ButtonWidget buy = ButtonWidget.builder(Text.literal("Buy"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.BuyListing(row.index())))
                .dimensions(left + 280, LIST_TOP + i * ROW_HEIGHT - 4, 50, 20).build();
            buy.active = row.buyable();
            addDrawableChild(buy);
        }

        int y = this.height - 56;
        sellField = new TextFieldWidget(this.textRenderer, this.width / 2 - 170, y, 60, 20, Text.literal("Count"));
        sellField.setPlaceholder(Text.literal("1"));
        addDrawableChild(sellField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Sell held to server"), b -> {
            long count = parseCount(sellField.getText());
            ClientPlayNetworking.send(new EconomyNetworking.ServerSell(count));
        }).dimensions(this.width / 2 - 104, y, 160, 20).build());
    }

    private void initShops() {
        int centerX = this.width / 2;
        if (data.nearDepot()) {
            priceField = new TextFieldWidget(this.textRenderer, centerX - 170, 96, 100, 20, Text.literal("Price"));
            priceField.setPlaceholder(Text.literal("12.34"));
            addDrawableChild(priceField);
            addDrawableChild(ButtonWidget.builder(Text.literal("List held item"), b -> {
                long price = Currency.parse(priceField.getText());
                if (price > 0) {
                    ClientPlayNetworking.send(new EconomyNetworking.ListHeld(price));
                }
            }).dimensions(centerX - 64, 96, 120, 20).build());
        }

        int left = centerX - 170;
        int top = 128;
        addScrollButtons(data.myListings().size());
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.myListings().size(); i++) {
            ShopRow row = data.myListings().get(i + scroll);
            addDrawableChild(ButtonWidget.builder(Text.literal("Unlist"), b ->
                    ClientPlayNetworking.send(new EconomyNetworking.Unlist(row.index())))
                .dimensions(left + 280, top + i * ROW_HEIGHT - 4, 60, 20).build());
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Balance: " + Currency.format(data.balance()), this.width / 2, 56, 0xFFE066);

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
            context.drawTextWithShadow(this.textRenderer, "Loading...", left, 100, 0xAAAAAA);
            return;
        }
        if (rows.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No entries.", left, 100, 0xAAAAAA);
            return;
        }
        int top = 100;
        int visible = Math.min(ROWS_VISIBLE + 1, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            HistoryEntry entry = rows.get(i + scroll);
            context.drawTextWithShadow(this.textRenderer, historyRow(entry), left, top + i * 14, 0xE0E0E0);
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
            "Balance: " + Currency.format(data.balance()), centerX, 60, 0xFFD700);
        if (!data.hasBank()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "Stand near a bank block to deposit or withdraw.", centerX, 78, 0xAAAAAA);
        } else if (data.bankOccupiedByOther()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "This bank is occupied. Try another.", centerX, 78, 0xFF8080);
        }
    }

    private void renderMarket(DrawContext context) {
        int left = this.width / 2 - 170;
        if (data.listings().isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No listings yet.", left, LIST_TOP, 0xAAAAAA);
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.listings().size(); i++) {
            MarketRow row = data.listings().get(i + scroll);
            int y = LIST_TOP + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()), left, y, 0xFFFFFF);
            String detail = row.buyable()
                ? "by " + row.owner()
                : "by " + row.owner() + " @ " + row.x() + "," + row.y() + "," + row.z();
            context.drawTextWithShadow(this.textRenderer, detail, left, y + 10,
                row.buyable() ? 0x88CC88 : 0xAAAAAA);
        }

        int listY = this.height - 68;
        context.drawTextWithShadow(this.textRenderer, "Sell to server:", this.width / 2 - 170, listY, 0xFFE066);
        StringBuilder buyables = new StringBuilder();
        for (EconomyNetworking.BuyRow row : data.serverBuy()) {
            if (buyables.length() > 0) {
                buyables.append(", ");
            }
            buyables.append(row.name()).append(" ").append(Currency.format(row.price()));
        }
        String summary = buyables.length() == 0 ? "nothing accepted" : buyables.toString();
        context.drawTextWithShadow(this.textRenderer, summary, this.width / 2 - 60, listY, 0xAAAAAA);
    }

    private void renderShops(DrawContext context) {
        int left = this.width / 2 - 170;
        if (!data.nearDepot()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                "Stand near your 2x2 barrel depot to manage a shop.", this.width / 2, 96, 0xAAAAAA);
        } else {
            context.drawTextWithShadow(this.textRenderer,
                "Hold an item and set a price to list it.", left, 118, 0xAAAAAA);
        }

        int top = 128;
        if (data.myListings().isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "You have no listings.", left, top, 0xAAAAAA);
        }
        for (int i = 0; i < ROWS_VISIBLE && i + scroll < data.myListings().size(); i++) {
            ShopRow row = data.myListings().get(i + scroll);
            int y = top + i * ROW_HEIGHT;
            context.drawTextWithShadow(this.textRenderer,
                row.name() + "  " + Currency.format(row.price()) + "  stock " + row.stock(),
                left, y + 2, 0xFFFFFF);
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
