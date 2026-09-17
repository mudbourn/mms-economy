package info.mudbourn.mmseconomy.client.screen;

import info.mudbourn.mmseconomy.economy.Currency;
import info.mudbourn.mmseconomy.history.HistoryCategory;
import info.mudbourn.mmseconomy.history.HistoryEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

// The three-tab transaction ledger, opened by /eco history or the hub history tab.
public final class HistoryScreen extends Screen {

    private static final int ROW_HEIGHT = 12;

    private final List<HistoryEntry> purchases;

    private final List<HistoryEntry> payments;

    private final List<HistoryEntry> deals;

    private HistoryCategory tab = HistoryCategory.PURCHASE;

    private int scroll;

    public HistoryScreen(List<HistoryEntry> purchases, List<HistoryEntry> payments, List<HistoryEntry> deals) {
        super(Text.literal("Transaction History"));
        this.purchases = purchases;
        this.payments = payments;
        this.deals = deals;
    }

    @Override
    protected void init() {
        int tabWidth = 90;
        int startX = this.width / 2 - tabWidth * 3 / 2 - tabWidth / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Purchases"), b -> select(HistoryCategory.PURCHASE))
            .dimensions(startX, 28, tabWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Payments"), b -> select(HistoryCategory.PAYMENT))
            .dimensions(startX + tabWidth + 4, 28, tabWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Deals"), b -> select(HistoryCategory.DEAL))
            .dimensions(startX + (tabWidth + 4) * 2, 28, tabWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
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
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        List<HistoryEntry> entries = current();
        int top = 56;
        int bottom = this.height - 36;
        int visible = (bottom - top) / ROW_HEIGHT;
        int x = this.width / 2 - 160;

        if (entries.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "No entries.", x, top, 0xAAAAAA);
            return;
        }

        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            HistoryEntry entry = entries.get(i + scroll);
            context.drawTextWithShadow(this.textRenderer, rowText(entry), x, top + i * ROW_HEIGHT, 0xE0E0E0);
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
    public boolean shouldPause() {
        return false;
    }
}
