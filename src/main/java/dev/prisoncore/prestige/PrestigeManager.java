package dev.prisoncore.prestige;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Loads prestige.yml and handles prestiging, including milestone prizes. */
public final class PrestigeManager {

    public enum Result { SUCCESS, DISABLED, NOT_MAX_RANK, NOT_ENOUGH, MAX_PRESTIGE }

    private enum Mode { LINEAR, EXPONENTIAL }

    private final PrisonCore plugin;
    private final TreeMap<Long, Reward> milestones = new TreeMap<>();
    private final TreeMap<Long, Reward> repeating = new TreeMap<>();

    private boolean enabled = true;
    private long maxPrestige;
    private double costBase;
    private double costIncrease;
    private Mode rankCostMode = Mode.LINEAR;
    private double rankCostIncrease;
    private double rankCostMaxMultiplier;
    private boolean resetBalance;
    private int maxPerCommand;
    private boolean broadcastEvery;
    private String prefixFormat = "";

    public PrestigeManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "prestige.yml");
        if (!file.exists()) {
            plugin.saveResource("prestige.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);
        maxPrestige = Math.max(0, config.getLong("max-prestige", 0));
        costBase = Math.max(0, config.getDouble("cost.base", 0));
        costIncrease = Math.max(0, config.getDouble("cost.increase-per-prestige", 0));
        try {
            rankCostMode = Mode.valueOf(config.getString("rank-cost.mode", "LINEAR").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("prestige.yml: rank-cost.mode must be LINEAR or EXPONENTIAL. Using LINEAR.");
            rankCostMode = Mode.LINEAR;
        }
        rankCostIncrease = Math.max(0, config.getDouble("rank-cost.increase-per-prestige", 0.10));
        rankCostMaxMultiplier = Math.max(0, config.getDouble("rank-cost.max-multiplier", 0));
        resetBalance = config.getBoolean("reset-balance", true);
        maxPerCommand = Math.max(1, config.getInt("max-per-command", 1000));
        broadcastEvery = config.getBoolean("broadcast-every-prestige", false);
        prefixFormat = config.getString("prefix", "&8[&dP%prestige%&8] ");

        milestones.clear();
        repeating.clear();
        readRewards(config.getConfigurationSection("milestones"), milestones, "milestones");
        readRewards(config.getConfigurationSection("repeating"), repeating, "repeating");
    }

    private void readRewards(ConfigurationSection section, Map<Long, Reward> into, String label) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            long level = Numbers.parseLong(key);
            if (entry == null || level <= 0) {
                plugin.getLogger().warning("prestige.yml: ignoring '" + label + "." + key
                        + "' (the key must be a prestige number such as 10).");
                continue;
            }
            into.put(level, Reward.read(entry));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public long maxPrestige() {
        return maxPrestige;
    }

    public boolean resetBalance() {
        return resetBalance;
    }

    public NavigableMap<Long, Reward> milestones() {
        return Collections.unmodifiableNavigableMap(milestones);
    }

    public NavigableMap<Long, Reward> repeating() {
        return Collections.unmodifiableNavigableMap(repeating);
    }

    /** The next milestone above the given prestige, or null if there is none. */
    public Long nextMilestone(long prestige) {
        return milestones.higherKey(prestige);
    }

    /** How much more expensive rank-ups are at this prestige (1.0 = normal price). */
    public double rankCostMultiplier(long prestige) {
        if (prestige <= 0 || rankCostIncrease <= 0) {
            return 1;
        }
        double multiplier = rankCostMode == Mode.EXPONENTIAL
                ? Math.pow(1 + rankCostIncrease, prestige)
                : 1 + prestige * rankCostIncrease;
        if (rankCostMaxMultiplier > 0) {
            multiplier = Math.min(multiplier, rankCostMaxMultiplier);
        }
        if (Double.isNaN(multiplier) || multiplier > 1e200) {
            multiplier = 1e200;
        }
        return multiplier;
    }

    /** The fee for going from {@code prestige} to {@code prestige + 1}. */
    public double prestigeCost(long prestige) {
        return costBase * (1 + prestige * costIncrease);
    }

    /** Coloured prefix for chat and the scoreboard; empty until the first prestige. */
    public String prefix(long prestige) {
        if (prestige <= 0 || prefixFormat == null) {
            return "";
        }
        return prefixFormat.replace("%prestige%", Numbers.whole(prestige));
    }

    /** All rewards earned on reaching exactly this prestige. */
    public List<Reward> rewardsAt(long prestige) {
        List<Reward> rewards = new ArrayList<>(2);
        Reward milestone = milestones.get(prestige);
        if (milestone != null) {
            rewards.add(milestone);
        }
        for (Map.Entry<Long, Reward> entry : repeating.entrySet()) {
            if (prestige % entry.getKey() == 0) {
                rewards.add(entry.getValue());
            }
        }
        return rewards;
    }

    public Result check(PlayerData data) {
        if (!enabled) {
            return Result.DISABLED;
        }
        if (maxPrestige > 0 && data.prestige() >= maxPrestige) {
            return Result.MAX_PRESTIGE;
        }
        if (plugin.ranks().next(plugin.ranks().of(data)) != null) {
            return Result.NOT_MAX_RANK;
        }
        if (!plugin.economy().has(data.uuid(), prestigeCost(data.prestige()))) {
            return Result.NOT_ENOUGH;
        }
        return Result.SUCCESS;
    }

    /** Prestiges once and tells the player how it went. */
    public Result prestige(Player player) {
        PlayerData data = plugin.data(player);
        Result result = check(data);
        if (result != Result.SUCCESS) {
            explain(player, data, result);
            return result;
        }
        perform(player, data, true);
        plugin.permissions().refresh(player);
        return Result.SUCCESS;
    }

    /** Ranks up and prestiges repeatedly for as long as the player can afford it. */
    public long prestigeMax(Player player) {
        PlayerData data = plugin.data(player);
        if (!enabled) {
            explain(player, data, Result.DISABLED);
            return 0;
        }
        long count = 0;
        int rankups = 0;
        double balanceBefore = plugin.economy().balance(data.uuid());
        while (count < maxPerCommand) {
            rankups += plugin.ranks().rankupSilently(player, data);
            if (check(data) != Result.SUCCESS) {
                break;
            }
            perform(player, data, false);
            count++;
        }
        if (count > 0 || rankups > 0) {
            plugin.permissions().refresh(player);
        }
        if (count == 0) {
            if (rankups > 0) {
                Rank now = plugin.ranks().of(data);
                double spent = balanceBefore - plugin.economy().balance(data.uuid());
                plugin.messages().send(player, "rankup-max-success", "count", rankups, "rank", now.name(),
                        "rank_prefix", now.prefix(), "cost", plugin.money(spent));
            }
            explain(player, data, check(data));
            return 0;
        }
        Rank now = plugin.ranks().of(data);
        plugin.messages().send(player, "prestige-max-success", "count", Numbers.whole(count),
                "prestige", Numbers.whole(data.prestige()), "rank", now.name(), "rank_prefix", now.prefix());
        if (broadcastEvery) {
            broadcast(player, data);
        }
        return count;
    }

    private void explain(Player player, PlayerData data, Result result) {
        switch (result) {
            case DISABLED -> plugin.messages().send(player, "prestige-disabled");
            case MAX_PRESTIGE -> plugin.messages().send(player, "prestige-max");
            case NOT_MAX_RANK -> {
                Rank last = plugin.ranks().last();
                plugin.messages().send(player, "prestige-not-max-rank", "rank", last.name(),
                        "rank_prefix", last.prefix());
            }
            case NOT_ENOUGH -> {
                double cost = prestigeCost(data.prestige());
                plugin.messages().send(player, "prestige-not-enough", "cost", plugin.money(cost),
                        "needed", plugin.money(cost - plugin.economy().balance(data.uuid())));
            }
            default -> {
            }
        }
    }

    /** Does the actual prestige. The caller has already verified {@link #check}. */
    private void perform(Player player, PlayerData data, boolean announce) {
        plugin.economy().withdraw(data.uuid(), prestigeCost(data.prestige()));
        if (resetBalance) {
            plugin.economy().set(data.uuid(), 0);
        }
        Rank first = plugin.ranks().first();
        data.rank(first.name());
        data.prestige(data.prestige() + 1);
        long level = data.prestige();

        if (announce) {
            plugin.messages().send(player, "prestige-success", "prestige", Numbers.whole(level),
                    "rank", first.name(), "rank_prefix", first.prefix());
            if (broadcastEvery) {
                broadcast(player, data);
            }
        }
        for (Reward reward : rewardsAt(level)) {
            grant(player, data, level, reward);
        }
    }

    private void broadcast(Player player, PlayerData data) {
        String message = plugin.messages().get("prestige-broadcast", "player", player.getName(),
                "prestige", Numbers.whole(data.prestige()));
        if (!message.isEmpty()) {
            Bukkit.broadcastMessage(message);
        }
    }

    private void grant(Player player, PlayerData data, long level, Reward reward) {
        if (reward.money() > 0) {
            plugin.economy().deposit(data.uuid(), reward.money());
        }
        if (reward.multiplier() > 0) {
            data.bonusMultiplier(data.bonusMultiplier() + reward.multiplier());
            plugin.sell().invalidate(player);
        }
        for (String command : reward.commands()) {
            plugin.runConsole(command, player, "prestige", String.valueOf(level));
        }
        String prestigeText = Numbers.whole(level);
        if (!reward.message().isBlank()) {
            plugin.messages().sendRaw(player, reward.message(), "player", player.getName(),
                    "prestige", prestigeText, "reward", reward.describe(plugin));
        } else {
            plugin.messages().send(player, "prestige-reward", "prestige", prestigeText,
                    "reward", reward.describe(plugin));
        }
        if (!reward.broadcast().isBlank()) {
            Bukkit.broadcastMessage(plugin.messages().format(reward.broadcast(), "player", player.getName(),
                    "prestige", prestigeText, "reward", reward.describe(plugin)));
        }
    }
}
