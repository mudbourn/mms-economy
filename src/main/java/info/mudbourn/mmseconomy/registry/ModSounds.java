package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

// Registers the mod's bank interaction sound events.
public final class ModSounds {

    // Plays when the hub is opened at a bank block.
    public static SoundEvent BANK_OPEN;

    // Plays when a hub opened at a bank block is closed.
    public static SoundEvent BANK_CLOSE;

    public static void register() {
        BANK_OPEN = register("bank_open");
        BANK_CLOSE = register("bank_close");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(MmsEconomy.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
