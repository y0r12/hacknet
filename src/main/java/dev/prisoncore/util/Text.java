package dev.prisoncore.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat colour helpers. Supports {@code &a} style codes and {@code &#RRGGBB} hex colours. */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String text = input;
        if (text.contains("&#")) {
            Matcher matcher = HEX.matcher(text);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String replacement;
                try {
                    replacement = net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString();
                } catch (Throwable ex) {
                    replacement = "";
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(out);
            text = out.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }

    /** Shows text above the hotbar. Purely cosmetic, so any failure is ignored. */
    @SuppressWarnings("deprecation")
    public static void actionBar(Player player, String message) {
        try {
            player.sendActionBar(message); // Paper
            return;
        } catch (Throwable notPaper) {
            // Fall through to the Spigot way.
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable ignored) {
            // No action bar support on this server.
        }
    }

    /** A text progress bar, e.g. {@code ||||||||||} with the first part coloured as complete. */
    public static String progressBar(double fraction, int length, String symbol, String doneColor, String todoColor) {
        double clamped = Math.max(0, Math.min(1, fraction));
        int done = (int) Math.floor(clamped * length);
        StringBuilder bar = new StringBuilder(doneColor);
        for (int i = 0; i < length; i++) {
            if (i == done) {
                bar.append(todoColor);
            }
            bar.append(symbol);
        }
        return color(bar.toString());
    }

    /** Turns DIAMOND_BLOCK into "Diamond Block". */
    public static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
