package info.mudbourn.mmseconomy.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import info.mudbourn.mmseconomy.MmsEconomy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

// The admin buy-list of item id to server-sell unit price in Pice, reloadable and empty until an admin populates it.
public final class ServerBuyList {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "mms_economy_buylist.json";

    private static Map<String, Long> prices = new LinkedHashMap<>();

    private ServerBuyList() {
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) {
            prices = new LinkedHashMap<>();
            save();
            return;
        }
        try {
            java.lang.reflect.Type type = new TypeToken<LinkedHashMap<String, Long>>() {
            }.getType();
            Map<String, Long> loaded = GSON.fromJson(Files.readString(path), type);
            prices = loaded != null ? loaded : new LinkedHashMap<>();
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to read {}", FILE_NAME, e);
            prices = new LinkedHashMap<>();
        }
    }

    public static long priceOf(String itemId) {
        Long price = prices.get(itemId);
        return price != null ? price : 0L;
    }

    public static Map<String, Long> entries() {
        return Map.copyOf(prices);
    }

    private static void save() {
        try {
            Files.writeString(filePath(), GSON.toJson(prices));
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to write {}", FILE_NAME, e);
        }
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
