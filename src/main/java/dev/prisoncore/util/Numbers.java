package dev.prisoncore.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting and parsing for money and other large values. */
public final class Numbers {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    private Numbers() {
    }

    /** Short form: 950, 1.5K, 2.35M, 1B ... falls back to scientific notation past decillions. */
    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "0";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "∞" : "-∞";
        }
        if (value < 0) {
            return "-" + format(-value);
        }
        if (value < 1000) {
            return trim(value);
        }
        int group = (int) Math.floor(Math.log10(value) / 3);
        double scaled = value / Math.pow(1000, group);
        // Rounding can push 999.999 up to 1000; move to the next suffix when that happens.
        if (Math.round(scaled * 100) / 100.0 >= 1000) {
            group++;
            scaled = value / Math.pow(1000, group);
        }
        if (group >= SUFFIXES.length) {
            return new DecimalFormat("0.##E0", SYMBOLS).format(value);
        }
        return trim(scaled) + SUFFIXES[group];
    }

    /** Long form with thousands separators: 1,234,567.89 */
    public static String full(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return format(value);
        }
        if (Math.abs(value) >= 1e15) {
            return format(value);
        }
        return new DecimalFormat("#,##0.##", SYMBOLS).format(value);
    }

    /** Whole number with thousands separators: 12,345 */
    public static String whole(long value) {
        return new DecimalFormat("#,##0", SYMBOLS).format(value);
    }

    private static String trim(double value) {
        return new DecimalFormat("0.##", SYMBOLS).format(value);
    }

    /**
     * Parses player input such as {@code 1500}, {@code 1,500}, {@code 1.5k}, {@code 2M} or {@code 1e6}.
     *
     * @return the parsed value, or {@code -1} if the input is not a valid non-negative number
     */
    public static double parse(String input) {
        if (input == null) {
            return -1;
        }
        String text = input.trim().replace(",", "").replace("$", "");
        if (text.isEmpty()) {
            return -1;
        }
        double factor = 1;
        String lower = text.toLowerCase(Locale.ROOT);
        // Longest suffixes first so "qa" is not read as a plain "a".
        for (int i = SUFFIXES.length - 1; i >= 1; i--) {
            String suffix = SUFFIXES[i].toLowerCase(Locale.ROOT);
            if (lower.endsWith(suffix)) {
                String head = lower.substring(0, lower.length() - suffix.length());
                if (!head.isEmpty() && (Character.isDigit(head.charAt(head.length() - 1)) || head.endsWith("."))) {
                    factor = Math.pow(1000, i);
                    text = head;
                    break;
                }
            }
        }
        try {
            double value = Double.parseDouble(text) * factor;
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                return -1;
            }
            return value;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Parses a whole non-negative number, returning {@code -1} when invalid. */
    public static long parseLong(String input) {
        double value = parse(input);
        if (value < 0 || value > Long.MAX_VALUE / 2.0 || value != Math.floor(value)) {
            return -1;
        }
        return (long) value;
    }

    /** Seconds as m:ss, or h:mm:ss for long durations. */
    public static String time(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%d:%02d", m, s);
    }
}
