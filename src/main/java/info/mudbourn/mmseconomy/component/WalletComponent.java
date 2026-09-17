package info.mudbourn.mmseconomy.component;

import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

// A player's virtual balance in Pice, the currency of record.
public interface WalletComponent extends AutoSyncedComponent {

    long getBalance();

    void setBalance(long pice);
}
