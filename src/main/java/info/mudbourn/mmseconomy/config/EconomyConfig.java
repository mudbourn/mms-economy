package info.mudbourn.mmseconomy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.mudbourn.mmseconomy.MmsEconomy;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EconomyConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "mms_economy.json";

    // Hard cap on a wallet balance in Pice; 0 means the long range is the only limit.
    public long maxBalance = 0L;

    // Pice a fresh player wallet starts with.
    public long startingBalance = 0L;

    // Whether /pay is available to players.
    public boolean payEnabled = true;

    // Whether /deal is available to players.
    public boolean dealEnabled = true;

    // Ticks a pending deal survives before it times out (1200 = 60 seconds).
    public int dealTimeoutTicks = 1200;

    // Whether the marketplace tabs and listings are active.
    public boolean marketEnabled = true;

    // Whether the read-only wallet readout draws on the inventory screen.
    public boolean walletHudEnabled = true;

    // Pice that make one Colt.
    public int piceToColtRatio = 100;

    // Blocks a player may be from a bank block to use it.
    public int bankRange = 8;

    // Blocks within which a player opening the hub claims a bank block as occupant.
    public int bankOccupancyRange = 2;

    // Blocks a buyer may be from a listing's depot to buy from it.
    public int depotRange = 8;

    // Blocks a buyer may be from a shop's online owner to buy from it.
    public int shopOwnerRange = 5;

    // Blocks a buyer may be from a shop's market depot to buy from it.
    public int shopMarketRange = 15;

    // Flat Pice sink charged once when a player first claims a shop depot.
    public long shopSetupFeePice = 1500L;

    // Whether transaction taxes are collected at all.
    public boolean taxEnabled = true;

    // Tax on /pay, in basis points (500 = 5%).
    public int taxPayBps = 500;

    // Tax on each currency leg of a /deal, in basis points.
    public int taxDealBps = 500;

    // Tax on a player-to-player market purchase, in basis points.
    public int taxPurchaseBps = 500;

    // Entries kept per history category before the oldest is dropped.
    public int historyPerCategory = 250;

    // Percent interest paid on a stored bank balance each interest tick.
    public int bankInterestPercent = 2;

    // In-game days between bank interest ticks.
    public int bankInterestDays = 30;

    // Every settable key, for command autocomplete and validation.
    public static final java.util.List<String> KEYS = java.util.List.of(
        "maxBalance",
        "startingBalance",
        "payEnabled",
        "dealEnabled",
        "dealTimeoutTicks",
        "marketEnabled",
        "walletHudEnabled",
        "piceToColtRatio",
        "bankRange",
        "bankOccupancyRange",
        "depotRange",
        "shopOwnerRange",
        "shopMarketRange",
        "shopSetupFeePice",
        "taxEnabled",
        "taxPayBps",
        "taxDealBps",
        "taxPurchaseBps",
        "historyPerCategory",
        "bankInterestPercent",
        "bankInterestDays");

    // Applies a value to a named field, returning false for an unknown key or unparseable value, and saves on success.
    public boolean set(String key, String value) {
        try {
            switch (key) {
                case "maxBalance" -> this.maxBalance = Long.parseLong(value);
                case "startingBalance" -> this.startingBalance = Long.parseLong(value);
                case "payEnabled" -> this.payEnabled = Boolean.parseBoolean(value);
                case "dealEnabled" -> this.dealEnabled = Boolean.parseBoolean(value);
                case "dealTimeoutTicks" -> this.dealTimeoutTicks = Integer.parseInt(value);
                case "marketEnabled" -> this.marketEnabled = Boolean.parseBoolean(value);
                case "walletHudEnabled" -> this.walletHudEnabled = Boolean.parseBoolean(value);
                case "piceToColtRatio" -> this.piceToColtRatio = Integer.parseInt(value);
                case "bankRange" -> this.bankRange = Integer.parseInt(value);
                case "bankOccupancyRange" -> this.bankOccupancyRange = Integer.parseInt(value);
                case "depotRange" -> this.depotRange = Integer.parseInt(value);
                case "shopOwnerRange" -> this.shopOwnerRange = Integer.parseInt(value);
                case "shopMarketRange" -> this.shopMarketRange = Integer.parseInt(value);
                case "shopSetupFeePice" -> this.shopSetupFeePice = Long.parseLong(value);
                case "taxEnabled" -> this.taxEnabled = Boolean.parseBoolean(value);
                case "taxPayBps" -> this.taxPayBps = Integer.parseInt(value);
                case "taxDealBps" -> this.taxDealBps = Integer.parseInt(value);
                case "taxPurchaseBps" -> this.taxPurchaseBps = Integer.parseInt(value);
                case "historyPerCategory" -> this.historyPerCategory = Integer.parseInt(value);
                case "bankInterestPercent" -> this.bankInterestPercent = Integer.parseInt(value);
                case "bankInterestDays" -> this.bankInterestDays = Integer.parseInt(value);
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        save();
        return true;
    }

    // A single-line dump of every field, for the config command with no value.
    public String describe() {
        return "maxBalance=" + maxBalance
            + ", startingBalance=" + startingBalance
            + ", payEnabled=" + payEnabled
            + ", dealEnabled=" + dealEnabled
            + ", dealTimeoutTicks=" + dealTimeoutTicks
            + ", marketEnabled=" + marketEnabled
            + ", walletHudEnabled=" + walletHudEnabled
            + ", piceToColtRatio=" + piceToColtRatio
            + ", bankRange=" + bankRange
            + ", bankOccupancyRange=" + bankOccupancyRange
            + ", depotRange=" + depotRange
            + ", shopOwnerRange=" + shopOwnerRange
            + ", shopMarketRange=" + shopMarketRange
            + ", shopSetupFeePice=" + shopSetupFeePice
            + ", taxEnabled=" + taxEnabled
            + ", taxPayBps=" + taxPayBps
            + ", taxDealBps=" + taxDealBps
            + ", taxPurchaseBps=" + taxPurchaseBps
            + ", historyPerCategory=" + historyPerCategory
            + ", bankInterestPercent=" + bankInterestPercent
            + ", bankInterestDays=" + bankInterestDays;
    }

    public static EconomyConfig load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            EconomyConfig fresh = new EconomyConfig();
            fresh.save();
            return fresh;
        }

        try {
            EconomyConfig loaded = GSON.fromJson(Files.readString(path), EconomyConfig.class);
            return loaded != null ? loaded : new EconomyConfig();
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to read {}, using defaults", FILE_NAME, e);
            return new EconomyConfig();
        }
    }

    public void save() {
        try {
            Files.writeString(configPath(), GSON.toJson(this));
        } catch (IOException e) {
            MmsEconomy.LOGGER.error("Failed to write {}", FILE_NAME, e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
