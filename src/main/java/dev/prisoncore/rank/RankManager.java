package dev.prisoncore.rank;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Loads ranks.yml and handles ranking up. */
public final class RankManager {

    public enum Result { SUCCESS, MAX_RANK, NOT_ENOUGH }

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,16}");

    private final PrisonCore plugin;
    private final List<Rank> ranks = new ArrayList<>();
    private final Map<String, Rank> byName = new HashMap<>();
    private File file;
    private YamlConfiguration config;
    private boolean broadcastRankup;
    private boolean broadcastFinalRank;

    public RankManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) {
            plugin.saveResource("ranks.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        broadcastRankup = config.getBoolean("settings.broadcast-rankup", false);
        broadcastFinalRank = config.getBoolean("settings.broadcast-final-rank", true);

        ranks.clear();
        byName.clear();
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(name);
                if (entry == null) {
                    continue;
                }
                if (!VALID_NAME.matcher(name).matches()) {
                    plugin.getLogger().warning("Skipping rank '" + name
                            + "': names may only use letters, numbers, _ and - (max 16 characters).");
                    continue;
                }
                if (byName.containsKey(name.toLowerCase(Locale.ROOT))) {
                    plugin.getLogger().warning("Skipping duplicate rank '" + name + "'.");
                    continue;
                }
                Rank rank = new Rank(name, ranks.size(), Math.max(0, entry.getDouble("cost", 0)),
                        entry.getString("prefix", "&7[&f" + name + "&7]"),
                        List.copyOf(entry.getStringList("permissions")),
                        List.copyOf(entry.getStringList("commands")));
                ranks.add(rank);
                byName.put(name.toLowerCase(Locale.ROOT), rank);
            }
        }
        if (ranks.isEmpty()) {
            plugin.getLogger().severe("ranks.yml has no ranks! Using a single placeholder rank 'A'. "
                    + "Delete ranks.yml and run /prison reload to restore the default A-Z ladder.");
            Rank rank = new Rank("A", 0, 0, "&7[&fA&7]", List.of(), List.of());
            ranks.add(rank);
            byName.put("a", rank);
        }
    }

    public List<Rank> all() {
        return Collections.unmodifiableList(ranks);
    }

    public Rank get(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public Rank first() {
        return ranks.get(0);
    }

    public Rank last() {
        return ranks.get(ranks.size() - 1);
    }

    /** The player's current rank. Unknown or deleted ranks fall back to the starting rank. */
    public Rank of(PlayerData data) {
        Rank rank = get(data.rank());
        return rank != null ? rank : first();
    }

    public Rank next(Rank rank) {
        return rank.index() + 1 < ranks.size() ? ranks.get(rank.index() + 1) : null;
    }

    /** What it costs a player at the given prestige to rank up into {@code target}. */
    public double cost(Rank target, long prestige) {
        return target.cost() * plugin.prestige().rankCostMultiplier(prestige);
    }

    /** Total cost of going from the first rank to the last at the given prestige. */
    public double totalCost(long prestige) {
        double total = 0;
        for (Rank rank : ranks) {
            if (rank.index() > 0) {
                total += cost(rank, prestige);
            }
        }
        return total;
    }

    /** Ranks the player up once and tells them how it went. */
    public Result rankup(Player player) {
        PlayerData data = plugin.data(player);
        Rank next = next(of(data));
        if (next == null) {
            plugin.messages().send(player, "rankup-max-rank");
            return Result.MAX_RANK;
        }
        double cost = cost(next, data.prestige());
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            double needed = cost - plugin.economy().balance(player.getUniqueId());
            plugin.messages().send(player, "rankup-not-enough", "rank", next.name(),
                    "rank_prefix", next.prefix(), "cost", plugin.money(cost), "needed", plugin.money(needed));
            return Result.NOT_ENOUGH;
        }
        apply(player, data, next);
        plugin.permissions().refresh(player);
        plugin.messages().send(player, "rankup-success", "rank", next.name(), "rank_prefix", next.prefix(),
                "cost", plugin.money(cost));
        announce(player, next);
        return Result.SUCCESS;
    }

    /** Ranks the player up as many times as they can afford and tells them how it went. */
    public int rankupMax(Player player) {
        PlayerData data = plugin.data(player);
        Rank before = of(data);
        if (next(before) == null) {
            plugin.messages().send(player, "rankup-max-rank");
            return 0;
        }
        double balanceBefore = plugin.economy().balance(player.getUniqueId());
        int count = rankupSilently(player, data);
        if (count == 0) {
            Rank next = next(before);
            double cost = cost(next, data.prestige());
            plugin.messages().send(player, "rankup-not-enough", "rank", next.name(), "rank_prefix", next.prefix(),
                    "cost", plugin.money(cost), "needed", plugin.money(cost - balanceBefore));
            return 0;
        }
        plugin.permissions().refresh(player);
        Rank now = of(data);
        double spent = balanceBefore - plugin.economy().balance(player.getUniqueId());
        plugin.messages().send(player, "rankup-max-success", "count", count, "rank", now.name(),
                "rank_prefix", now.prefix(), "cost", plugin.money(spent));
        announce(player, now);
        return count;
    }

    /** Ranks up as far as the balance allows without messages or a permission refresh. */
    public int rankupSilently(Player player, PlayerData data) {
        int count = 0;
        while (true) {
            Rank next = next(of(data));
            if (next == null) {
                break;
            }
            if (!plugin.economy().withdraw(player.getUniqueId(), cost(next, data.prestige()))) {
                break;
            }
            apply(player, data, next);
            count++;
        }
        return count;
    }

    private void apply(Player player, PlayerData data, Rank rank) {
        data.rank(rank.name());
        for (String command : rank.commands()) {
            plugin.runConsole(command, player, "rank", rank.name());
        }
    }

    private void announce(Player player, Rank rank) {
        boolean isFinal = next(rank) == null;
        if (broadcastRankup || (isFinal && broadcastFinalRank)) {
            String message = plugin.messages().get(isFinal ? "rankup-final-broadcast" : "rankup-broadcast",
                    "player", player.getName(), "rank", rank.name(), "rank_prefix", rank.prefix());
            if (!message.isEmpty()) {
                Bukkit.broadcastMessage(message);
            }
        }
    }

    // ------------------------------------------------------------------ admin editing

    public static boolean validName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public void setCost(Rank rank, double cost) {
        config.set("ranks." + rank.name() + ".cost", clean(cost));
        saveAndReload();
    }

    public void setPrefix(Rank rank, String prefix) {
        config.set("ranks." + rank.name() + ".prefix", prefix);
        saveAndReload();
    }

    /** Adds a new rank at the end of the ladder. */
    public void create(String name, double cost, String prefix) {
        config.set("ranks." + name + ".cost", clean(cost));
        config.set("ranks." + name + ".prefix", prefix == null ? "&7[&f" + name + "&7]" : prefix);
        saveAndReload();
    }

    public void delete(Rank rank) {
        config.set("ranks." + rank.name(), null);
        saveAndReload();
    }

    /** Re-prices the whole ladder: the second rank costs {@code firstCost}, each later one {@code multiplier}x more. */
    public void autoCost(double firstCost, double multiplier) {
        double cost = firstCost;
        for (Rank rank : ranks) {
            if (rank.index() == 0) {
                config.set("ranks." + rank.name() + ".cost", 0);
                continue;
            }
            config.set("ranks." + rank.name() + ".cost", clean(roundNice(cost)));
            cost *= multiplier;
        }
        saveAndReload();
    }

    /** Rounds to two significant figures so prices look tidy (43,256 becomes 43,000). */
    static double roundNice(double value) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)) - 1);
        if (magnitude < 1) {
            return Math.round(value);
        }
        return Math.round(value / magnitude) * magnitude;
    }

    /** Stores whole numbers without a trailing ".0" so the file stays readable. */
    private static Object clean(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 9e15) {
            return (long) value;
        }
        return value;
    }

    private void saveAndReload() {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save ranks.yml", ex);
        }
        load();
        plugin.permissions().refreshAll();
    }
}
