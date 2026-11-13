package dev.prisoncore.menu;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.rank.RankManager;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the /ranks and /mines menus. */
public final class Menus {

    private static final int PAGE_SIZE = 45;

    private final PrisonCore plugin;

    public Menus(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void openRanks(Player player, int page) {
        PlayerData data = plugin.data(player);
        List<Rank> ranks = plugin.ranks().all();
        Rank current = plugin.ranks().of(data);
        int pages = Math.max(1, (ranks.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(plugin.messages().get("menu-ranks-title"), 6);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = shown * PAGE_SIZE + i;
            if (index >= ranks.size()) {
                break;
            }
            Rank rank = ranks.get(index);
            double cost = plugin.ranks().cost(rank, data.prestige());
            List<String> lore = new ArrayList<>();
            Material icon;
            Runnable click = null;
            if (rank.index() < current.index()) {
                icon = Material.LIME_STAINED_GLASS_PANE;
                lore.add(plugin.messages().get("menu-rank-completed"));
            } else if (rank.index() == current.index()) {
                icon = Material.NETHER_STAR;
                lore.add(plugin.messages().get("menu-rank-current"));
            } else {
                boolean isNext = rank.index() == current.index() + 1;
                icon = isNext ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
                lore.add(plugin.messages().get("menu-rank-cost", "cost", plugin.money(cost)));
                if (isNext) {
                    lore.add(plugin.messages().get("menu-rank-click"));
                    click = () -> {
                        if (plugin.ranks().rankup(player) == RankManager.Result.SUCCESS) {
                            openRanks(player, shown);
                        }
                    };
                } else {
                    lore.add(plugin.messages().get("menu-rank-locked"));
                }
            }
            Runnable action = click;
            menu.set(i, Menu.item(icon, rank.prefix(), lore), action == null ? null : p -> action.run());
        }
        addNavigation(menu, shown, pages, target -> openRanks(player, target));
        menu.set(49, Menu.item(Material.BOOK, plugin.messages().get("menu-info-title"), List.of(
                plugin.messages().get("menu-info-rank", "rank_prefix", current.prefix()),
                plugin.messages().get("menu-info-prestige", "prestige", Numbers.whole(data.prestige())),
                plugin.messages().get("menu-info-balance", "balance",
                        plugin.money(plugin.economy().balance(player.getUniqueId()))))), null);
        menu.open(player);
    }

    public void openMines(Player player, int page) {
        List<Mine> mines = new ArrayList<>(plugin.mines().all());
        int pages = Math.max(1, (mines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int shown = Math.max(0, Math.min(page, pages - 1));
        Menu menu = new Menu(plugin.messages().get("menu-mines-title"), 6);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = shown * PAGE_SIZE + i;
            if (index >= mines.size()) {
                break;
            }
            Mine mine = mines.get(index);
            boolean access = plugin.mines().canAccess(player, mine);
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().get("menu-mine-requirement", "requirement",
                    plugin.mines().requirementText(mine)));
            double total = mine.totalWeight();
            for (Map.Entry<Material, Double> block : mine.blocks().entrySet()) {
                lore.add(plugin.messages().get("menu-mine-block", "block", Text.pretty(block.getKey().name()),
                        "percent", Numbers.format(total <= 0 ? 0 : block.getValue() * 100 / total)));
            }
            lore.add(plugin.messages().get("menu-mine-remaining", "percent",
                    String.valueOf((int) Math.round(mine.remainingPercent()))));
            lore.add(plugin.messages().get(access ? "menu-mine-click" : "menu-mine-locked"));
            menu.set(i, Menu.item(access ? mine.primaryBlock() : Material.BARRIER,
                            (access ? "&a&l" : "&c&l") + "Mine " + mine.name(), lore),
                    access ? p -> {
                        p.closeInventory();
                        plugin.teleportToMine(p, mine);
                    } : null);
        }
        addNavigation(menu, shown, pages, target -> openMines(player, target));
        menu.open(player);
    }

    private void addNavigation(Menu menu, int page, int pages, java.util.function.IntConsumer open) {
        if (page > 0) {
            menu.set(45, Menu.item(Material.ARROW, plugin.messages().get("menu-previous"), null),
                    p -> open.accept(page - 1));
        }
        if (page < pages - 1) {
            menu.set(53, Menu.item(Material.ARROW, plugin.messages().get("menu-next"), null),
                    p -> open.accept(page + 1));
        }
    }
}
