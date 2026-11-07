package info.mudbourn.mmseconomy.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import info.mudbourn.mmseconomy.MmsEconomy;
import info.mudbourn.mmseconomy.economy.Chips;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// The admin server-shop catalog: item id to a sell price and rarity category, reloadable and saved in object form.
public final class ServerBuyList {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "mms_economy_buylist.json";

    // One catalog entry: the price the server pays a seller and the rarity bucket it lists under.
    public record Entry(long sell, ServerShopCategory category) {
    }

    private static Map<String, Entry> entries = new LinkedHashMap<>();

    private ServerBuyList() {
    }

    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) {
            entries = new LinkedHashMap<>();
            save();
            return;
        }
        try {
            entries = parse(Files.readString(path));
            dropChips();
            save();
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to read {}", FILE_NAME, e);
            entries = new LinkedHashMap<>();
        }
    }

    // Keeps casino chips out of the server shop; they are exchanged at the bank instead.
    private static void dropChips() {
        entries.keySet().removeIf(Chips::isChip);
    }

    // Reads either the legacy id-to-number map or the id-to-object map, defaulting a missing category to VALUABLE.
    private static Map<String, Entry> parse(String json) {
        Map<String, Entry> parsed = new LinkedHashMap<>();
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) {
            return parsed;
        }
        for (Map.Entry<String, JsonElement> field : root.getAsJsonObject().entrySet()) {
            JsonElement value = field.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                parsed.put(field.getKey(), new Entry(value.getAsLong(), ServerShopCategory.VALUABLE));
            } else if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                long sell = object.has("sell") ? object.get("sell").getAsLong() : 0L;
                ServerShopCategory category = object.has("category")
                    ? ServerShopCategory.of(object.get("category").getAsString())
                    : ServerShopCategory.VALUABLE;
                parsed.put(field.getKey(), new Entry(sell, category));
            }
        }
        return parsed;
    }

    public static long priceOf(String itemId) {
        Entry entry = entries.get(itemId);
        return entry != null ? entry.sell() : 0L;
    }

    // The price the server pays for one unit when the library already holds stock of it, or face value for a chip.
    public static long sellPriceAt(String itemId, long stock) {
        long base = priceOf(itemId);
        long half = MmsEconomy.config().serverSellHalfStock;
        if (half <= 0 || Chips.isChip(itemId)) {
            return base;
        }
        return Math.max(1L, base * half / (half + Math.max(0L, stock)));
    }

    // The total the server pays for count units sold into a library holding stock, pricing each unit in turn.
    public static long sellTotal(String itemId, long stock, long count) {
        long total = 0L;
        for (long i = 0; i < count; i++) {
            total += sellPriceAt(itemId, stock + i);
        }
        return total;
    }

    public static ServerShopCategory categoryOf(String itemId) {
        Entry entry = entries.get(itemId);
        return entry != null ? entry.category() : ServerShopCategory.VALUABLE;
    }

    public static boolean sells(String itemId) {
        return entries.containsKey(itemId);
    }

    // The price a buyer pays for an in-stock unit: 1.5x the sell price rounded up, or face value for a chip.
    public static long stockBuyPrice(String itemId, long sell) {
        return Chips.isChip(itemId) ? sell : (sell * 3 + 1) / 2;
    }

    // The price a buyer pays for a minted unit when out of stock: 3x the sell price, or face value for a chip.
    public static long mintBuyPrice(String itemId, long sell) {
        return Chips.isChip(itemId) ? sell : sell * 3;
    }

    public static Map<String, Entry> all() {
        return Collections.unmodifiableMap(entries);
    }

    private static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Entry> field : entries.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("sell", field.getValue().sell());
            object.addProperty("category", field.getValue().category().name().toLowerCase());
            root.add(field.getKey(), object);
        }
        try {
            Files.writeString(filePath(), GSON.toJson(root));
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to write {}", FILE_NAME, e);
        }
    }

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
