package dev.prisoncore.prestige;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.util.Numbers;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A prestige prize.
 *
 * @param description short text shown in /prestiges and in the reward message (auto-generated when blank)
 * @param message     private message to the player (blank = default milestone message)
 * @param broadcast   server-wide announcement (blank = none)
 * @param money       money added to the player's balance
 * @param multiplier  permanent sell multiplier bonus, e.g. 0.25 for +0.25x
 * @param commands    console commands, %player% and %prestige% are replaced
 * @param permissions permission nodes the player keeps from this prestige onwards
 */
public record Reward(String description, String message, String broadcast, double money, double multiplier,
                     List<String> commands, List<String> permissions) {

    static Reward read(ConfigurationSection section) {
        return new Reward(
                section.getString("description", ""),
                section.getString("message", ""),
                section.getString("broadcast", ""),
                Math.max(0, section.getDouble("money", 0)),
                Math.max(0, section.getDouble("multiplier", 0)),
                List.copyOf(section.getStringList("commands")),
                List.copyOf(section.getStringList("permissions")));
    }

    /** A one-line summary such as "$50K, +0.1x sell multiplier". */
    public String describe(PrisonCore plugin) {
        if (!description.isBlank()) {
            return description;
        }
        List<String> parts = new ArrayList<>();
        if (money > 0) {
            parts.add(plugin.money(money));
        }
        if (multiplier > 0) {
            parts.add("+" + Numbers.format(multiplier) + "x sell multiplier");
        }
        if (!commands.isEmpty() || !permissions.isEmpty()) {
            parts.add(parts.isEmpty() ? "special reward" : "bonus rewards");
        }
        return parts.isEmpty() ? "reward" : String.join(", ", parts);
    }
}
