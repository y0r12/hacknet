package dev.prisoncore.placeholder;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The %placeholders% usable in the scoreboard, chat format and (with a {@code prisoncore_} prefix)
 * through PlaceholderAPI.
 */
public final class Placeholders {

    /** Every supported placeholder, for documentation and tab-completion. */
    public static final List<String> KEYS = List.of(
            "player", "rank", "rank_prefix", "next_rank", "next_rank_prefix",
            "prestige", "prestige_prefix", "prestige_cost", "next_milestone",
            "balance", "balance_full", "rankup_cost", "rankup_needed", "rankup_progress", "rankup_bar",
            "multiplier", "blocks_mined", "blocks_mined_full", "autosell",
            "mine", "mine_remaining", "mine_reset", "online", "max_players");

    private static final Pattern TOKEN = Pattern.compile("%([a-z0-9_]+)%");

    private final PrisonCore plugin;

    public Placeholders(PrisonCore plugin) {
        this.plugin = plugin;
    }

    /** Replaces placeholders for this player and applies colour codes. */
    public String apply(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String result = text;
        if (result.indexOf('%') >= 0) {
            PlayerData data = plugin.data(player);
            Matcher matcher = TOKEN.matcher(result);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String value = resolve(data, player, matcher.group(1));
                matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
            }
            matcher.appendTail(out);
            result = out.toString();
            if (plugin.papiPresent() && result.indexOf('%') >= 0) {
                try {
                    result = PapiHook.apply(player, result);
                } catch (Throwable ignored) {
                    // PlaceholderAPI problems must never break the scoreboard or chat.
                }
            }
        }
        return Text.color(result);
    }

    /** The price of the player's next goal: the next rank, or the prestige fee at the top rank. -1 if none. */
    private double goalCost(PlayerData data) {
        Rank next = plugin.ranks().next(plugin.ranks().of(data));
        if (next != null) {
            return plugin.ranks().cost(next, data.prestige());
        }
        if (canPrestigeFurther(data)) {
            return plugin.prestige().prestigeCost(data.prestige());
        }
        return -1;
    }

    private boolean canPrestigeFurther(PlayerData data) {
        return plugin.prestige().enabled()
                && (plugin.prestige().maxPrestige() <= 0 || data.prestige() < plugin.prestige().maxPrestige());
    }

    private double goalFraction(PlayerData data) {
        double cost = goalCost(data);
        if (cost < 0) {
            return 1;
        }
        if (cost == 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, plugin.economy().balance(data.uuid()) / cost));
    }

    /**
     * Looks up one placeholder. Values may contain {@code &} colour codes.
     *
     * @param online the player if they are online, otherwise null
     * @return the value, or null if the key is not one of ours
     */
    public String resolve(PlayerData data, Player online, String key) {
        switch (key) {
            case "player":
                return data.name();
            case "rank":
                return plugin.ranks().of(data).name();
            case "rank_prefix":
                return plugin.ranks().of(data).prefix();
            case "next_rank": {
                Rank next = plugin.ranks().next(plugin.ranks().of(data));
                if (next != null) {
                    return next.name();
                }
                return plugin.messages().get(canPrestigeFurther(data) ? "placeholder-prestige" : "placeholder-max");
            }
            case "next_rank_prefix": {
                Rank next = plugin.ranks().next(plugin.ranks().of(data));
                if (next != null) {
                    return next.prefix();
                }
                return plugin.messages().get(canPrestigeFurther(data) ? "placeholder-prestige" : "placeholder-max");
            }
            case "prestige":
                return Numbers.whole(data.prestige());
            case "prestige_prefix":
                return plugin.prestige().prefix(data.prestige());
            case "prestige_cost":
                return plugin.money(plugin.prestige().prestigeCost(data.prestige()));
            case "next_milestone": {
                Long next = plugin.prestige().nextMilestone(data.prestige());
                return next == null ? "-" : Numbers.whole(next);
            }
            case "balance":
                return plugin.money(plugin.economy().balance(data.uuid()));
            case "balance_full":
                return plugin.currencySymbol() + Numbers.full(plugin.economy().balance(data.uuid()));
            case "rankup_cost": {
                double cost = goalCost(data);
                return cost < 0 ? "-" : plugin.money(cost);
            }
            case "rankup_needed": {
                double cost = goalCost(data);
                if (cost < 0) {
                    return "-";
                }
                return plugin.money(Math.max(0, cost - plugin.economy().balance(data.uuid())));
            }
            case "rankup_progress":
                return String.valueOf((int) Math.floor(goalFraction(data) * 100));
            case "rankup_bar":
                return Text.progressBar(goalFraction(data),
                        plugin.getConfig().getInt("scoreboard.progress-bar.length", 10),
                        plugin.getConfig().getString("scoreboard.progress-bar.symbol", "|"),
                        plugin.getConfig().getString("scoreboard.progress-bar.done-color", "&a"),
                        plugin.getConfig().getString("scoreboard.progress-bar.todo-color", "&7"));
            case "multiplier":
                if (online != null) {
                    return Numbers.format(plugin.sell().multiplier(online).total());
                }
                return Numbers.format(1 + data.bonusMultiplier());
            case "blocks_mined":
                return Numbers.format(data.blocksMined());
            case "blocks_mined_full":
                return Numbers.whole(data.blocksMined());
            case "autosell":
                return data.autosell() ? "On" : "Off";
            case "mine": {
                Mine mine = currentMine(online);
                return mine == null ? plugin.messages().get("placeholder-no-mine") : mine.name();
            }
            case "mine_remaining": {
                Mine mine = currentMine(online);
                return mine == null ? "-" : (int) Math.round(mine.remainingPercent()) + "%";
            }
            case "mine_reset": {
                Mine mine = currentMine(online);
                if (mine == null || plugin.mines().interval(mine) <= 0) {
                    return "-";
                }
                return Numbers.time(mine.secondsLeft());
            }
            case "online":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players":
                return String.valueOf(Bukkit.getMaxPlayers());
            default:
                return null;
        }
    }

    private Mine currentMine(Player online) {
        if (online == null || !Bukkit.isPrimaryThread()) {
            return null;
        }
        return plugin.mines().near(online.getLocation());
    }
}
