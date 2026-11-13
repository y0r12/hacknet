package dev.prisoncore.command;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.prestige.Reward;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.sell.SellManager;
import dev.prisoncore.util.Numbers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everyday player commands: ranking up, prestiging, mines, selling and the scoreboard toggle. */
public final class PlayerCommands implements TabExecutor {

    private static final int PAGE_SIZE = 10;

    private final PrisonCore plugin;

    public PlayerCommands(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        // Commands that also make sense from the console.
        switch (name) {
            case "ranks" -> {
                if (sender instanceof Player player && !(args.length > 0 && args[0].equalsIgnoreCase("list"))) {
                    plugin.menus().openRanks(player, 0);
                } else {
                    listRanks(sender);
                }
                return true;
            }
            case "prestiges" -> {
                listPrestiges(sender, args.length > 0 ? page(args[0]) : 1);
                return true;
            }
            case "prestigetop" -> {
                prestigeTop(sender, args.length > 0 ? page(args[0]) : 1);
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }

        switch (name) {
            case "rankup" -> plugin.ranks().rankup(player);
            case "rankupmax" -> plugin.ranks().rankupMax(player);
            case "prestige" -> plugin.prestige().prestige(player);
            case "prestigemax" -> plugin.prestige().prestigeMax(player);
            case "mines" -> plugin.menus().openMines(player, 0);
            case "mine" -> mine(player, args);
            case "sellall" -> sellAll(player);
            case "autosell" -> {
                PlayerData data = plugin.data(player);
                data.autosell(!data.autosell());
                plugin.messages().send(player, data.autosell() ? "autosell-on" : "autosell-off");
            }
            case "multiplier" -> multiplier(player);
            case "sb" -> {
                if (!plugin.sidebar().enabled()) {
                    plugin.messages().send(player, "scoreboard-disabled");
                } else {
                    plugin.messages().send(player, plugin.sidebar().toggle(player) ? "scoreboard-on" : "scoreboard-off");
                }
            }
            default -> {
                return false;
            }
        }
        plugin.sidebar().update(player);
        return true;
    }

    private static int page(String input) {
        long page = Numbers.parseLong(input);
        return page < 1 ? 1 : (int) Math.min(page, 100_000);
    }

    private void mine(Player player, String[] args) {
        Mine mine;
        if (args.length == 0) {
            mine = plugin.mines().best(player);
            if (mine == null) {
                plugin.messages().send(player, "no-mines");
                return;
            }
        } else {
            mine = plugin.mines().get(args[0]);
            if (mine == null) {
                plugin.messages().send(player, "mine-not-found", "mine", args[0]);
                return;
            }
            if (!plugin.mines().canAccess(player, mine)) {
                plugin.messages().send(player, "mine-no-access", "mine", mine.name(),
                        "requirement", plugin.mines().requirementText(mine));
                return;
            }
        }
        plugin.teleportToMine(player, mine);
    }

    private void sellAll(Player player) {
        SellManager.SellResult result = plugin.sell().sellInventory(player);
        if (result.items() == 0) {
            plugin.messages().send(player, "sell-nothing");
            return;
        }
        plugin.messages().send(player, "sell-success", "items", Numbers.whole(result.items()),
                "total", plugin.money(result.total()), "multiplier", Numbers.format(result.multiplier()));
    }

    private void multiplier(Player player) {
        SellManager.Multiplier multiplier = plugin.sell().multiplier(player);
        plugin.messages().send(player, "multiplier-info",
                "total", Numbers.format(multiplier.total()),
                "prestige", Numbers.format(multiplier.prestige()),
                "bonus", Numbers.format(multiplier.bonus()),
                "permission", Numbers.format(multiplier.permission()),
                "global", Numbers.format(multiplier.global()));
    }

    private void listRanks(CommandSender sender) {
        long prestige = sender instanceof Player player ? plugin.data(player).prestige() : 0;
        Rank current = sender instanceof Player player ? plugin.ranks().of(plugin.data(player)) : null;
        plugin.messages().send(sender, "ranks-header", "prestige", Numbers.whole(prestige));
        for (Rank rank : plugin.ranks().all()) {
            String key = current != null && rank.index() == current.index() ? "ranks-line-current" : "ranks-line";
            plugin.messages().send(sender, key, "rank", rank.name(), "rank_prefix", rank.prefix(),
                    "cost", rank.index() == 0 ? "-" : plugin.money(plugin.ranks().cost(rank, prestige)));
        }
    }

    private void listPrestiges(CommandSender sender, int page) {
        long prestige = sender instanceof Player player ? plugin.data(player).prestige() : 0;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, Reward> entry : plugin.prestige().milestones().entrySet()) {
            boolean reached = prestige >= entry.getKey();
            lines.add(plugin.messages().get(reached ? "prestiges-line-done" : "prestiges-line",
                    "prestige", Numbers.whole(entry.getKey()), "reward", entry.getValue().describe(plugin)));
        }
        for (Map.Entry<Long, Reward> entry : plugin.prestige().repeating().entrySet()) {
            lines.add(plugin.messages().get("prestiges-line-repeating",
                    "prestige", Numbers.whole(entry.getKey()), "reward", entry.getValue().describe(plugin)));
        }
        int pages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.min(page, pages);
        plugin.messages().send(sender, "prestiges-header", "prestige", Numbers.whole(prestige),
                "cost", plugin.money(plugin.prestige().prestigeCost(prestige)), "page", shown, "pages", pages);
        if (lines.isEmpty()) {
            plugin.messages().send(sender, "prestiges-empty");
        }
        for (int i = (shown - 1) * PAGE_SIZE; i < Math.min(lines.size(), shown * PAGE_SIZE); i++) {
            sender.sendMessage(lines.get(i));
        }
    }

    private void prestigeTop(CommandSender sender, int page) {
        List<PlayerData> sorted = new ArrayList<>(plugin.store().all());
        sorted.removeIf(data -> data.prestige() <= 0 && plugin.ranks().of(data).index() == 0);
        sorted.sort(Comparator.comparingLong((PlayerData data) -> data.prestige())
                .thenComparingInt(data -> plugin.ranks().of(data).index()).reversed());
        int pages = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.min(page, pages);
        plugin.messages().send(sender, "prestigetop-header", "page", shown, "pages", pages);
        if (sorted.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
        }
        for (int i = (shown - 1) * PAGE_SIZE; i < Math.min(sorted.size(), shown * PAGE_SIZE); i++) {
            PlayerData data = sorted.get(i);
            plugin.messages().send(sender, "prestigetop-line", "position", i + 1, "player", data.name(),
                    "prestige", Numbers.whole(data.prestige()), "rank", plugin.ranks().of(data).name());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("mine") && args.length == 1 && sender instanceof Player player) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            for (Mine mine : plugin.mines().all()) {
                if (mine.name().toLowerCase(Locale.ROOT).startsWith(typed) && plugin.mines().canAccess(player, mine)) {
                    options.add(mine.name());
                }
            }
        }
        return options;
    }
}
