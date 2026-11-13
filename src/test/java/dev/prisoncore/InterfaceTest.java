package dev.prisoncore;

import dev.prisoncore.menu.Menu;
import dev.prisoncore.mine.Mine;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scoreboard, chat format, placeholders and the /ranks and /mines menus. */
class InterfaceTest extends PrisonTestBase {

    private List<String> board(PlayerMock player) {
        return plugin.sidebar().shownLines(player).stream().map(ChatColor::stripColor).toList();
    }

    @Test
    void scoreboardShowsRankPrestigeAndBalance() {
        PlayerMock player = join("Steve");
        setBalance(player, 1250);
        tick(20);

        Objective objective = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        assertNotNull(objective, "the player should have a sidebar");
        assertEquals("PRISON", ChatColor.stripColor(objective.getDisplayName()));

        List<String> lines = board(player);
        assertEquals(15, lines.size());
        assertTrue(lines.contains("Steve"), lines.toString());
        assertTrue(lines.contains(" Rank: [A]"), lines.toString());
        assertTrue(lines.contains(" Prestige: 0"), lines.toString());
        assertTrue(lines.contains(" Balance: $1.25K"), lines.toString());
        assertTrue(lines.contains(" Multiplier: 1x"), lines.toString());
        assertTrue(lines.contains("Next: B"), lines.toString());
        assertTrue(lines.contains(" Cost: $2.5K"), lines.toString());
        assertTrue(lines.contains(" ||||||||||" + " 50%"), lines.toString());
        assertTrue(lines.contains("Mine: None"), lines.toString());
    }

    @Test
    void scoreboardUpdatesAfterRankingUp() {
        PlayerMock player = join("Steve");
        setBalance(player, 2500);
        player.performCommand("rankup");
        List<String> lines = board(player);
        assertTrue(lines.contains(" Rank: [B]"), lines.toString());
        assertTrue(lines.contains("Next: C"), lines.toString());
        assertTrue(lines.contains(" Balance: $0"), lines.toString());
    }

    @Test
    void scoreboardPointsToPrestigeAtTheFinalRank() {
        PlayerMock player = join("Steve");
        data(player).rank("Z");
        data(player).prestige(4);
        setBalance(player, 35_000_000);
        tick(20);
        List<String> lines = board(player);
        assertTrue(lines.contains("Next: Prestige"), lines.toString());
        // Prestige 4 -> 5 costs 50M x 1.4 = 70M, and the player has half of it.
        assertTrue(lines.contains(" Cost: $70M"), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.endsWith(" 50%")), lines.toString());
        assertTrue(lines.contains(" Prestige: 4"), lines.toString());
    }

    @Test
    void scoreboardShowsTheMineYouAreIn() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        player.teleport(mine.spawn());
        tick(20);
        List<String> lines = board(player);
        assertTrue(lines.contains("Mine: A"), lines.toString());
        assertTrue(lines.contains(" Blocks left: 100%"), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith(" Reset in: 9:")), lines.toString());
    }

    @Test
    void playersCanHideTheScoreboard() {
        PlayerMock player = join("Steve");
        assertTrue(plugin.sidebar().showing(player));

        player.performCommand("sb");
        assertFalse(plugin.sidebar().showing(player));
        assertNull(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR));
        assertFalse(data(player).scoreboard());

        // The choice is remembered when they come back.
        player.disconnect();
        player.reconnect();
        assertFalse(plugin.sidebar().showing(player));

        player.performCommand("sb");
        assertTrue(plugin.sidebar().showing(player));
    }

    @Test
    void scoreboardCanBeDisabledOrChangedInConfig() {
        PlayerMock player = join("Steve");
        editFile("config.yml", config -> {
            config.set("scoreboard.title", "&bMY SERVER");
            config.set("scoreboard.lines", List.of("&fHello %player%", "&7Rank %rank%"));
        });
        assertEquals(List.of("Hello Steve", "Rank A"), board(player));
        assertEquals("MY SERVER",
                ChatColor.stripColor(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getDisplayName()));

        editFile("config.yml", config -> config.set("scoreboard.enabled", false));
        assertFalse(plugin.sidebar().showing(player));
        player.performCommand("sb");
        assertTrue(allMessages(player).contains("turned off"));
    }

    @Test
    void chatShowsPrestigeAndRank() {
        PlayerMock player = join("Steve");
        data(player).rank("C");

        AsyncPlayerChatEvent first = new AsyncPlayerChatEvent(false, player, "hello 100%", new HashSet<>());
        server.getPluginManager().callEvent(first);
        assertEquals("[C] Steve: hello 100%",
                ChatColor.stripColor(String.format(first.getFormat(), player.getDisplayName(), first.getMessage())));

        data(player).prestige(12);
        AsyncPlayerChatEvent second = new AsyncPlayerChatEvent(false, player, "&cnot red", new HashSet<>());
        server.getPluginManager().callEvent(second);
        assertEquals("[P12] [C] Steve: &cnot red",
                ChatColor.stripColor(String.format(second.getFormat(), player.getDisplayName(), second.getMessage())));
    }

    @Test
    void chatFormatCanBeTurnedOff() {
        PlayerMock player = join("Steve");
        plugin.getConfig().set("chat.enabled", false);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player, "hello", new HashSet<>());
        String before = event.getFormat();
        server.getPluginManager().callEvent(event);
        assertEquals(before, event.getFormat());
    }

    @Test
    void placeholdersResolve() {
        PlayerMock player = join("Steve");
        data(player).prestige(1);
        setBalance(player, 1375);
        assertEquals("B", plugin.placeholders().resolve(data(player), player, "next_rank"));
        // B costs 2,750 at prestige 1.
        assertEquals("$2.75K", plugin.placeholders().resolve(data(player), player, "rankup_cost"));
        assertEquals("$1.38K", plugin.placeholders().resolve(data(player), player, "rankup_needed"));
        assertEquals("50", plugin.placeholders().resolve(data(player), player, "rankup_progress"));
        assertEquals("10", plugin.placeholders().resolve(data(player), player, "next_milestone"));
        assertEquals("$1,375", plugin.placeholders().resolve(data(player), player, "balance_full"));
        assertEquals("1.05", plugin.placeholders().resolve(data(player), player, "multiplier"));
        assertNull(plugin.placeholders().resolve(data(player), player, "not_a_placeholder"));
        assertEquals("Rank A, %unknown% stays", ChatColor.stripColor(
                plugin.placeholders().apply(player, "Rank %rank%, %unknown% stays")));
        for (String key : dev.prisoncore.placeholder.Placeholders.KEYS) {
            assertNotNull(plugin.placeholders().resolve(data(player), player, key), key);
            assertNotNull(plugin.placeholders().resolve(data(player), null, key), key + " (offline)");
        }
    }

    @Test
    void ranksMenuLetsYouRankUpWithAClick() {
        PlayerMock player = join("Steve");
        setBalance(player, 3000);
        player.performCommand("ranks");

        Inventory menu = player.getOpenInventory().getTopInventory();
        assertInstanceOf(Menu.class, menu.getHolder());
        assertEquals(54, menu.getSize());
        assertEquals(Material.NETHER_STAR, menu.getItem(0).getType(), "current rank");
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, menu.getItem(1).getType(), "next rank");
        assertEquals(Material.RED_STAINED_GLASS_PANE, menu.getItem(2).getType(), "locked rank");
        assertNotNull(menu.getItem(25), "rank Z should be on the first page");
        assertNull(menu.getItem(26));

        // Clicking a locked rank does nothing; clicking the next rank buys it.
        ((Menu) menu.getHolder()).click(player, 5);
        assertEquals("A", plugin.ranks().of(data(player)).name());
        ((Menu) menu.getHolder()).click(player, 1);
        assertEquals("B", plugin.ranks().of(data(player)).name());
        assertEquals(500, balance(player));

        Inventory refreshed = player.getOpenInventory().getTopInventory();
        assertEquals(Material.LIME_STAINED_GLASS_PANE, refreshed.getItem(0).getType());
        assertEquals(Material.NETHER_STAR, refreshed.getItem(1).getType());
    }

    @Test
    void menuItemsCannotBeTaken() {
        PlayerMock player = join("Steve");
        player.performCommand("ranks");
        var click = new PlayerSimulation(player).simulateInventoryClick(3);
        assertTrue(click.isCancelled());
    }

    @Test
    void minesMenuTeleportsToUnlockedMines() {
        PlayerMock admin = admin("Admin");
        Mine mineA = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        createMine(admin, "B", 20, 60, 0, 24, 64, 4);
        PlayerMock player = join("Steve");
        player.teleport(new Location(world, 200, 80, 200));

        player.performCommand("mines");
        Inventory menu = player.getOpenInventory().getTopInventory();
        Menu holder = assertInstanceOf(Menu.class, menu.getHolder());
        ItemStack open = menu.getItem(0);
        ItemStack locked = menu.getItem(1);
        assertEquals(Material.STONE, open.getType(), "unlocked mines show their main block");
        assertEquals(Material.BARRIER, locked.getType(), "locked mines show a barrier");
        assertTrue(ChatColor.stripColor(String.join("|", locked.getItemMeta().getLore())).contains("Needs: Rank B"));

        holder.click(player, 1);
        assertEquals(200, player.getLocation().getBlockX(), "clicking a locked mine does nothing");
        holder.click(player, 0);
        assertTrue(mineA.near(player.getLocation(), 2));
    }

    @Test
    void consoleCanListRanks() {
        server.dispatchCommand(server.getConsoleSender(), "ranks");
        String first = server.getConsoleSender().nextMessage();
        assertNotNull(first);
        assertTrue(ChatColor.stripColor(first).contains("Ranks"));
    }
}
