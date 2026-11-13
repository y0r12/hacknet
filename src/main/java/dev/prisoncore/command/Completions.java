package dev.prisoncore.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Tab-completion helpers. */
final class Completions {

    private static final int LIMIT = 60;

    private Completions() {
    }

    static List<String> filter(String typed, Collection<String> options) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
                if (matches.size() >= LIMIT) {
                    break;
                }
            }
        }
        return matches;
    }

    static List<String> players(String typed) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return filter(typed, names);
    }
}
