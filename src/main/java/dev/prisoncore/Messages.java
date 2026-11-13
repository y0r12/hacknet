package dev.prisoncore;

import dev.prisoncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Player-facing text from messages.yml. Missing entries fall back to the defaults shipped in the jar. */
public final class Messages {

    private final PrisonCore plugin;
    private YamlConfiguration config = new YamlConfiguration();
    private YamlConfiguration defaults = new YamlConfiguration();
    private String prefix = "";

    public Messages(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not read the default messages: " + ex.getMessage());
        }
        prefix = raw("prefix");
    }

    private String raw(String key) {
        YamlConfiguration source = config.contains(key) ? config : defaults;
        if (source.isList(key)) {
            List<String> lines = source.getStringList(key);
            return String.join("\n", lines);
        }
        String value = source.getString(key);
        return value == null ? key : value;
    }

    /** Fills in {@code %name%} placeholders (given as name, value pairs) and applies colours. */
    public String format(String text, Object... replacements) {
        String result = text.replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace("%" + replacements[i] + "%", String.valueOf(replacements[i + 1]));
        }
        return Text.color(result);
    }

    public String get(String key, Object... replacements) {
        return format(raw(key), replacements);
    }

    /** Sends a message from messages.yml. An empty message is treated as "disabled" and not sent. */
    public void send(CommandSender to, String key, Object... replacements) {
        sendRaw(to, raw(key), replacements);
    }

    public void sendRaw(CommandSender to, String text, Object... replacements) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : format(text, replacements).split("\n")) {
            to.sendMessage(line);
        }
    }

    public String prefix() {
        return Text.color(prefix);
    }
}
