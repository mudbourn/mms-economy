package info.mudbourn.mmseconomy.market;

// The three rarity buckets the server shop groups its items into, from rarest to most farmable.
public enum ServerShopCategory {

    UNOBTAINABLE("Unobtainable"),
    TREASURE("Treasure"),
    VALUABLE("Valuable");

    private final String label;

    ServerShopCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    // The category named by an id, case-insensitive, falling back to VALUABLE for anything unknown.
    public static ServerShopCategory of(String name) {
        if (name != null) {
            for (ServerShopCategory category : values()) {
                if (category.name().equalsIgnoreCase(name)) {
                    return category;
                }
            }
        }
        return VALUABLE;
    }

    public static ServerShopCategory byOrdinal(int ordinal) {
        ServerShopCategory[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : VALUABLE;
    }
}
