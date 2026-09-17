package info.mudbourn.mmseconomy.history;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HistoryComponentImpl implements HistoryComponent {

    private final Map<HistoryCategory, Deque<HistoryEntry>> buffers = new EnumMap<>(HistoryCategory.class);

    public HistoryComponentImpl() {
        for (HistoryCategory category : HistoryCategory.values()) {
            buffers.put(category, new ArrayDeque<>());
        }
    }

    @Override
    public void add(HistoryEntry entry) {
        Deque<HistoryEntry> buffer = buffers.get(entry.category());
        buffer.addFirst(entry);
        int cap = Math.max(1, MmsEconomy.config().historyPerCategory);
        while (buffer.size() > cap) {
            buffer.removeLast();
        }
    }

    @Override
    public List<HistoryEntry> get(HistoryCategory category) {
        return new ArrayList<>(buffers.get(category));
    }

    @Override
    public void readData(ReadView view) {
        for (HistoryCategory category : HistoryCategory.values()) {
            Deque<HistoryEntry> buffer = buffers.get(category);
            buffer.clear();
            view.read(key(category), HistoryEntry.CODEC.listOf())
                .ifPresent(buffer::addAll);
        }
    }

    @Override
    public void writeData(WriteView view) {
        for (HistoryCategory category : HistoryCategory.values()) {
            view.put(key(category), HistoryEntry.CODEC.listOf(), new ArrayList<>(buffers.get(category)));
        }
    }

    private static String key(HistoryCategory category) {
        return category.name().toLowerCase(java.util.Locale.ROOT);
    }
}
