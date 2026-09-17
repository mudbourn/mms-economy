package info.mudbourn.mmseconomy.history;

import org.ladysnake.cca.api.v3.component.Component;

import java.util.List;

// A player's capped transaction ledger, one ring buffer per category.
public interface HistoryComponent extends Component {

    void add(HistoryEntry entry);

    List<HistoryEntry> get(HistoryCategory category);
}
