package dev.prisoncore;

import dev.prisoncore.data.PlayerData;
import dev.prisoncore.data.PlayerStore;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The /prison admin command and saving player data. */
class AdminAndStorageTest extends PrisonTestBase {

    @Test
    void normalPlayersCannotUseAdminCommands() {
        PlayerMock player = join("Steve");
        player.performCommand("prison player setprestige Steve 500");
        player.performCommand("prison rank setcost B 1");
        player.performCommand("prison reload");
        assertEquals(0, data(player).prestige());
        assertEquals(2500, plugin.ranks().get("B").cost());
    }

    @Test
    void setupChecklistShowsWhatIsMissing() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison setup");
        String before = allMessages(admin);
        assertTrue(before.contains("Ranks: 26 (A to Z)"), before);
        assertTrue(before.contains("Mines: 0/26 ranks have a mine"), before);
        assertTrue(before.contains("/prison mine create A"), before);
        assertTrue(before.contains("Economy: PrisonCore (built-in)"), before);

        createMine(admin, "A", 0, 60, 0, 2, 62, 2);
        admin.performCommand("prison setup");
        String after = allMessages(admin);
        assertTrue(after.contains("Mines: 1/26"), after);
        assertTrue(after.contains("/prison mine create B"), after);
    }

    @Test
    void setupWarnsAboutMinesInsideSpawnProtection() {
        PlayerMock admin = admin("Admin");
        int spawnX = world.getSpawnLocation().getBlockX();
        int spawnZ = world.getSpawnLocation().getBlockZ();
        server.setSpawnRadius(16);

        admin.teleport(new org.bukkit.Location(world, spawnX + 3.5, 60, spawnZ + 3.5));
        admin.performCommand("prison mine pos1");
        admin.teleport(new org.bukkit.Location(world, spawnX + 5.5, 62, spawnZ + 5.5));
        admin.performCommand("prison mine pos2");
        admin.teleport(new org.bukkit.Location(world, spawnX + 3.5, 63, spawnZ + 3.5));
        admin.performCommand("prison mine create A");
        tick(5);
        assertTrue(allMessages(admin).contains("inside the server's protected spawn area"),
                "creating a mine next to spawn should warn straight away");
        createMine(admin, "B", spawnX + 300, 60, spawnZ + 300, spawnX + 302, 62, spawnZ + 302);

        assertTrue(plugin.mines().insideSpawnProtection(plugin.mines().get("A")));
        assertFalse(plugin.mines().insideSpawnProtection(plugin.mines().get("B")));
        admin.performCommand("prison setup");
        String warned = allMessages(admin);
        assertTrue(warned.contains("Spawn protection: A is inside"), warned);
        assertTrue(warned.contains("spawn-protection=0"), warned);

        server.setSpawnRadius(0);
        assertFalse(plugin.mines().insideSpawnProtection(plugin.mines().get("A")));
        admin.performCommand("prison setup");
        assertFalse(allMessages(admin).contains("Spawn protection"));
    }

    @Test
    void helpListsCommandsAcrossPages() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison");
        assertTrue(allMessages(admin).contains("/prison setup"));
        admin.performCommand("prison help 3");
        assertTrue(allMessages(admin).contains("/eco <give|take|set>"));
        admin.performCommand("prison nonsense");
        assertTrue(allMessages(admin).contains("Unknown sub-command"));
    }

    @Test
    void rankPricesCanBeChangedAndAreSaved() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison rank setcost B 9999");
        assertEquals(9999, plugin.ranks().get("B").cost());

        admin.performCommand("prison rank autocost 1000 2");
        assertEquals(0, plugin.ranks().get("A").cost());
        assertEquals(1000, plugin.ranks().get("B").cost());
        assertEquals(2000, plugin.ranks().get("C").cost());
        assertEquals(4000, plugin.ranks().get("D").cost());
        // 1000 x 2^24, rounded to two significant figures.
        assertEquals(17_000_000_000.0, plugin.ranks().get("Z").cost());

        plugin.reload();
        assertEquals(2000, plugin.ranks().get("C").cost(), "prices should be saved to ranks.yml");
        assertEquals(26, plugin.ranks().all().size());
        assertEquals("&7[&fA&7]", plugin.ranks().get("A").prefix(), "prefixes are untouched by re-pricing");
    }

    @Test
    void ranksCanBeAddedRenamedAndRemoved() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison rank create Free 100m &a[&lFREE&a]");
        assertEquals(27, plugin.ranks().all().size());
        assertEquals("Free", plugin.ranks().last().name());
        assertEquals(100_000_000, plugin.ranks().last().cost());
        assertEquals("&a[&lFREE&a]", plugin.ranks().last().prefix());

        admin.performCommand("prison rank setprefix A &b<Newbie>");
        assertEquals("&b<Newbie>", plugin.ranks().get("A").prefix());

        admin.performCommand("prison rank create Free 5");
        assertTrue(allMessages(admin).contains("already exists"));
        admin.performCommand("prison rank create bad.name 5");
        assertNull(plugin.ranks().get("bad.name"));

        admin.performCommand("prison rank delete Free");
        assertEquals(26, plugin.ranks().all().size());
        assertEquals("Z", plugin.ranks().last().name());
    }

    @Test
    void deletedRanksFallBackToTheFirstRank() {
        PlayerMock admin = admin("Admin");
        PlayerMock player = join("Steve");
        data(player).rank("C");
        admin.performCommand("prison rank delete C");
        assertEquals("A", plugin.ranks().of(data(player)).name());
        assertEquals(25, plugin.ranks().all().size());
    }

    @Test
    void adminsCanManagePlayers() {
        PlayerMock admin = admin("Admin");
        PlayerMock player = join("Steve");

        admin.performCommand("prison player setrank Steve M");
        assertEquals("M", plugin.ranks().of(data(player)).name());
        assertTrue(player.hasPermission("prisoncore.rank.m"), "permissions update straight away");

        admin.performCommand("prison player setprestige Steve 40");
        admin.performCommand("prison player addprestige Steve 2");
        assertEquals(42, data(player).prestige());
        assertTrue(player.hasPermission("prisoncore.autosell"), "prestige 10+ unlocks autosell");

        player.performCommand("autosell");
        assertTrue(data(player).autosell());
        admin.performCommand("prison player setmultiplier Steve 2");
        admin.performCommand("prison player addmultiplier Steve 0.5");
        assertEquals(2.5, data(player).bonusMultiplier(), 1e-9);

        admin.performCommand("prison player info Steve");
        String info = allMessages(admin);
        assertTrue(info.contains("Rank: [M] | Prestige: 42"), info);
        assertTrue(info.contains("Bonus sell multiplier: +2.5x"), info);

        setBalance(player, 5000);
        admin.performCommand("prison player reset Steve");
        assertEquals("A", plugin.ranks().of(data(player)).name());
        assertEquals(0, data(player).prestige());
        assertEquals(0, data(player).bonusMultiplier());
        assertEquals(0, balance(player));
        assertFalse(player.hasPermission("prisoncore.rank.m"));
        assertFalse(data(player).autosell(), "a reset also switches autosell off");

        admin.performCommand("prison player setrank Ghost B");
        assertTrue(allMessages(admin).contains("never joined"));
    }

    @Test
    void playerDataIsSavedToDisk() throws Exception {
        PlayerMock player = join("Steve");
        PlayerData data = data(player);
        data.rank("K");
        data.prestige(1234);
        data.balance(9.87e15);
        data.blocksMined(555_000);
        data.bonusMultiplier(3.25);
        data.autosell(true);
        data.scoreboard(false);

        plugin.store().saveDirty(false);
        assertTrue(new File(plugin.getDataFolder(), "players.db").exists());

        // Read the file back with a brand new store, as a restarted server would.
        PlayerStore reopened = new PlayerStore(plugin);
        reopened.open();
        try {
            PlayerData loaded = reopened.get(player.getUniqueId());
            assertNotNull(loaded);
            assertEquals("Steve", loaded.name());
            assertEquals("K", loaded.rank());
            assertEquals(1234, loaded.prestige());
            assertEquals(9.87e15, loaded.balance());
            assertEquals(555_000, loaded.blocksMined());
            assertEquals(3.25, loaded.bonusMultiplier());
            assertTrue(loaded.autosell());
            assertFalse(loaded.scoreboard());
            assertNotNull(reopened.find("steve"), "names are looked up without caring about case");
        } finally {
            reopened.close();
        }
    }

    @Test
    void dataIsSavedInTheBackgroundAndOnShutdown() throws Exception {
        PlayerMock player = join("Steve");
        data(player).prestige(7);
        plugin.store().saveDirty(true);
        data(player).prestige(8);
        File folder = plugin.getDataFolder();
        java.util.UUID id = player.getUniqueId();

        server.getPluginManager().disablePlugin(plugin);

        PlayerStore reopened = new PlayerStore(plugin);
        reopened.open();
        try {
            assertEquals(8, reopened.get(id).prestige(), "the latest value must win");
        } finally {
            reopened.close();
        }
        assertTrue(new File(folder, "players.db").length() > 0);
    }
}
