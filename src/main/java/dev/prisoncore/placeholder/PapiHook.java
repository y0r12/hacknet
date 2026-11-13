package dev.prisoncore.placeholder;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.util.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI integration. Everything that touches PlaceholderAPI classes lives here so the plugin
 * loads without it. Registers {@code %prisoncore_<placeholder>%}, e.g. {@code %prisoncore_rank%}.
 */
public final class PapiHook {

    private PapiHook() {
    }

    public static boolean register(PrisonCore plugin) {
        return new Expansion(plugin).register();
    }

    /** Lets other plugins' placeholders be used in our scoreboard and chat format. */
    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    private static final class Expansion extends PlaceholderExpansion {

        private final PrisonCore plugin;

        Expansion(PrisonCore plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "prisoncore";
        }

        @Override
        public String getAuthor() {
            return "PrisonCore";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null || params == null) {
                return "";
            }
            PlayerData data = plugin.store().get(player.getUniqueId());
            if (data == null) {
                return "";
            }
            String value = plugin.placeholders().resolve(data, player.getPlayer(), params.toLowerCase(Locale.ROOT));
            return value == null ? null : Text.color(value);
        }
    }
}
