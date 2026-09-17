package info.mudbourn.mmseconomy.registry;

import info.mudbourn.mmseconomy.MmsEconomy;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

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
        Identifier id = Identifier.of(MmsEconomy.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
