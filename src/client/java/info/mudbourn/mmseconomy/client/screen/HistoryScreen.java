package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import info.mudbourn.mmseconomy.client.MmsEconomyClient;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// The three-tab transaction ledger, opened by /eco history or the hub history tab.
public final class HistoryScreen extends Screen {

    private static final int ROW_HEIGHT = 12;

    private final List<HistoryEntry> purchases;

    private final List<HistoryEntry> payments;

    private final List<HistoryEntry> deals;

    private final List<HistoryEntry> bills;

    private HistoryCategory tab = HistoryCategory.PURCHASE;

    private int scroll;

    public HistoryScreen(List<HistoryEntry> purchases,
                         List<HistoryEntry> payments,
                         List<HistoryEntry> deals,
                         List<HistoryEntry> bills) {
        super(Component.literal("Transaction History"));
        this.purchases = purchases;
        this.payments = payments;
        this.deals = deals;
        this.bills = bills;
    }

    @Override
    protected void init() {
        int tabWidth = 90;
        int startX = this.width / 2 - (tabWidth * 4 + 12) / 2;
        addRenderableWidget(Button.builder(Component.literal("Purchases"), b -> select(HistoryCategory.PURCHASE))
            .bounds(startX, 28, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Payments"), b -> select(HistoryCategory.PAYMENT))
            .bounds(startX + tabWidth + 4, 28, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Deals"), b -> select(HistoryCategory.DEAL))
            .bounds(startX + (tabWidth + 4) * 2, 28, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Bills"), b -> select(HistoryCategory.BILL))
            .bounds(startX + (tabWidth + 4) * 3, 28, tabWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void select(HistoryCategory category) {
        this.tab = category;
        this.scroll = 0;
    }

    private List<HistoryEntry> current() {
        return switch (tab) {
            case PURCHASE -> purchases;
            case PAYMENT -> payments;
            case DEAL -> deals;
            case BILL -> bills;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        List<HistoryEntry> entries = current();
        int top = 56;
        int bottom = this.height - 36;
        int visible = (bottom - top) / ROW_HEIGHT;
        int x = this.width / 2 - 160;

        if (entries.isEmpty()) {
            context.text(this.font, "No entries.", x, top, 0xAAAAAA);
            return;
        }

        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            HistoryEntry entry = entries.get(i + scroll);
            context.text(this.font, rowText(entry), x, top + i * ROW_HEIGHT, 0xE0E0E0);
        }
    }

    private String rowText(HistoryEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("D").append(entry.day()).append("  ");
        if (!entry.item().isEmpty()) {
            builder.append(entry.item());
            if (entry.count() > 0) {
                builder.append(" x").append(entry.count());
            }
            builder.append("  ");
        }
        builder.append("net ").append(Currency.format(entry.net()));
        builder.append(" (tax ").append(Currency.format(entry.tax())).append(")");
        if (!entry.counterparty().isEmpty()) {
            builder.append("  ").append(entry.counterparty());
        }
        return builder.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, current().size() - 1);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
        return true;
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
