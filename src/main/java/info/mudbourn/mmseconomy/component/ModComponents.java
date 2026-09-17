package info.mudbourn.mmseconomy.component;

import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.history.HistoryComponent;
import info.mudbourn.mmseconomy.history.HistoryComponentImpl;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistryV3;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;

// Declares the player-attached economy components and their respawn policy.
public final class ModComponents implements EntityComponentInitializer {

    public static final ComponentKey<WalletComponent> WALLET = ComponentRegistryV3.INSTANCE.getOrCreate(
        Identifier.of(MmsEconomy.MOD_ID, "wallet"),
        WalletComponent.class);

    public static final ComponentKey<HistoryComponent> HISTORY = ComponentRegistryV3.INSTANCE.getOrCreate(
        Identifier.of(MmsEconomy.MOD_ID, "history"),
        HistoryComponent.class);

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(
            WALLET,
            WalletComponentImpl::new,
            RespawnCopyStrategy.ALWAYS_COPY);
        registry.registerForPlayers(
            HISTORY,
            player -> new HistoryComponentImpl(),
            RespawnCopyStrategy.ALWAYS_COPY);
    }
}
