package info.mudbourn.mmseconomy.economy;

import info.mudbourn.mmseconomy.MmsEconomy;

// Converts between wallet Pice counts and their human-facing Colt.Pice form.
public final class Currency {

    private Currency() {
    }

    public static int ratio() {
        int ratio = MmsEconomy.config().piceToColtRatio;
        return ratio > 0 ? ratio : 100;
    }

    // A compact readout such as "C12.34".
    public static String format(long pice) {
        int ratio = ratio();
        long colt = pice / ratio;
        long sub = pice % ratio;
        int width = Integer.toString(ratio - 1).length();
        return "C" + colt + "." + padLeft(sub, width);
    }

    // A spelled-out readout such as "12 Colt 34 Pice".
    public static String formatLong(long pice) {
        int ratio = ratio();
        long colt = pice / ratio;
        long sub = pice % ratio;
        return colt + " Colt " + sub + " Pice";
    }

    // Parses "<colt>.<pice>" or a bare Colt integer into a Pice count, or -1 when malformed or over-precise.
    public static long parse(String text) {
        if (text == null || text.isBlank()) {
            return -1L;
        }
        String trimmed = text.trim();
        int dot = trimmed.indexOf('.');
        try {
            if (dot < 0) {
                long colt = Long.parseLong(trimmed);
                return colt >= 0 ? colt * ratio() : -1L;
            }

            int ratio = ratio();
            String coltPart = trimmed.substring(0, dot);
            String picePart = trimmed.substring(dot + 1);
            if (picePart.isEmpty() || picePart.length() > Integer.toString(ratio - 1).length()) {
                return -1L;
            }

            long colt = coltPart.isEmpty() ? 0L : Long.parseLong(coltPart);
            long sub = Long.parseLong(picePart);
            if (colt < 0 || sub < 0 || sub >= ratio) {
                return -1L;
            }
            return colt * ratio + sub;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static String padLeft(long value, int width) {
        String digits = Long.toString(value);
        if (digits.length() >= width) {
            return digits;
        }
        return "0".repeat(width - digits.length()) + digits;
    }
}
