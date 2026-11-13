package dev.prisoncore;

import dev.prisoncore.data.PlayerData;
import dev.prisoncore.rank.Rank;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankAndPrestigeTest extends PrisonTestBase {

    private void setRank(PlayerMock player, String rank) {
        data(player).rank(rank);
        plugin.permissions().refresh(player);
    }

    @Test
    void shipsWithRanksAToZ() {
        List<Rank> ranks = plugin.ranks().all();
        assertEquals(26, ranks.size());
        for (int i = 0; i < 26; i++) {
            assertEquals(String.valueOf((char) ('A' + i)), ranks.get(i).name());
        }
        assertEquals(0, ranks.get(0).cost());
        assertEquals(2500, plugin.ranks().get("B").cost());
        assertEquals(42_000_000, plugin.ranks().get("z").cost());
        for (int i = 2; i < 26; i++) {
            assertTrue(ranks.get(i).cost() > ranks.get(i - 1).cost(), "rank prices should keep rising");
        }
    }

    @Test
    void newPlayersStartAtTheFirstRankWithAPickaxe() {
        PlayerMock player = join("Steve");
        PlayerData data = data(player);
        assertEquals("A", plugin.ranks().of(data).name());
        assertEquals(0, data.prestige());
        assertEquals(0, balance(player));

        ItemStack pickaxe = player.getInventory().getItem(0);
        assertNotNull(pickaxe);
        assertEquals(Material.DIAMOND_PICKAXE, pickaxe.getType());
        assertEquals(3, pickaxe.getEnchantmentLevel(Enchantment.EFFICIENCY));
        assertEquals(3, pickaxe.getEnchantmentLevel(Enchantment.UNBREAKING));
    }

    @Test
    void starterKitIsOnlyGivenOnce() {
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        player.disconnect();
        player.reconnect();
        assertTrue(player.getInventory().isEmpty(), "returning players should not get the kit again");
    }

    @Test
    void rankupTakesMoneyAndMovesUpOneRank() {
        PlayerMock player = join("Steve");
        setBalance(player, 3000);
        assertTrue(player.performCommand("rankup"));
        assertEquals("B", plugin.ranks().of(data(player)).name());
        assertEquals(500, balance(player));
        assertTrue(allMessages(player).contains("ranked up"));
    }

    @Test
    void rankupWithoutEnoughMoneyChangesNothing() {
        PlayerMock player = join("Steve");
        setBalance(player, 2499);
        player.performCommand("rankup");
        assertEquals("A", plugin.ranks().of(data(player)).name());
        assertEquals(2499, balance(player));
        assertTrue(allMessages(player).contains("$1 more"));
    }

    @Test
    void rankupMaxGoesAsFarAsTheMoneyAllows() {
        PlayerMock player = join("Steve");
        // B + C + D = 2,500 + 3,800 + 5,600 = 11,900. E costs another 8,400.
        setBalance(player, 12_000);
        player.performCommand("rankupmax");
        assertEquals("D", plugin.ranks().of(data(player)).name());
        assertEquals(100, balance(player));
        assertTrue(allMessages(player).contains("3 time(s)"));
    }

    @Test
    void rankupAtTheFinalRankPointsToPrestige() {
        PlayerMock player = join("Steve");
        setRank(player, "Z");
        setBalance(player, 1e12);
        player.performCommand("rankup");
        assertEquals("Z", plugin.ranks().of(data(player)).name());
        assertEquals(1e12, balance(player));
        assertTrue(allMessages(player).contains("/prestige"));
    }

    @Test
    void rankGrantsPermissionsForItselfAndLowerRanks() {
        PlayerMock player = join("Steve");
        assertTrue(player.hasPermission("prisoncore.rank.a"));
        assertFalse(player.hasPermission("prisoncore.rank.b"));
        setBalance(player, 12_000);
        player.performCommand("rankupmax");
        assertTrue(player.hasPermission("prisoncore.rank.a"));
        assertTrue(player.hasPermission("prisoncore.rank.d"));
        assertFalse(player.hasPermission("prisoncore.rank.e"));
    }

    @Test
    void extraRankPermissionsAndCommandsFromRanksYml() {
        PlayerMock player = join("Steve");
        editFile("ranks.yml", config -> {
            config.set("ranks.B.permissions", List.of("essentials.kits.miner", "-some.blocked.node"));
            config.set("ranks.B.commands", List.of("eco give %player% 77"));
        });
        assertFalse(player.hasPermission("essentials.kits.miner"));
        setBalance(player, 2500);
        player.performCommand("rankup");
        assertTrue(player.hasPermission("essentials.kits.miner"));
        assertFalse(player.hasPermission("some.blocked.node"));
        assertEquals(77, balance(player), "the rank's console command should have paid out");
    }

    @Test
    void prestigeNeedsTheFinalRank() {
        PlayerMock player = join("Steve");
        setBalance(player, 1e12);
        player.performCommand("prestige");
        assertEquals(0, data(player).prestige());
        assertEquals(1e12, balance(player));
        assertTrue(allMessages(player).contains("must reach rank"));
    }

    @Test
    void prestigeNeedsTheFee() {
        PlayerMock player = join("Steve");
        setRank(player, "Z");
        setBalance(player, 49_999_999);
        player.performCommand("prestige");
        assertEquals(0, data(player).prestige());
        assertEquals("Z", plugin.ranks().of(data(player)).name());
        assertTrue(allMessages(player).contains("costs $50M"));
    }

    @Test
    void firstPrestigeResetsRankAndGivesTheFirstPrize() {
        PlayerMock player = join("Steve");
        PlayerMock watcher = join("Alex");
        setRank(player, "Z");
        setBalance(player, 60_000_000);

        player.performCommand("prestige");

        PlayerData data = data(player);
        assertEquals(1, data.prestige());
        assertEquals("A", plugin.ranks().of(data).name());
        // Balance is wiped, then the prestige 1 prize of $50,000 is paid.
        assertEquals(50_000, balance(player));
        assertEquals(0.10, data.bonusMultiplier(), 1e-9);
        assertFalse(player.hasPermission("prisoncore.rank.z"));
        assertTrue(allMessages(player).contains("Prestige 1"));
        assertTrue(allMessages(watcher).contains("Steve has prestiged for the first time"));
    }

    @Test
    void rankupsCostMoreAfterPrestiging() {
        PlayerMock player = join("Steve");
        assertEquals(2500, plugin.ranks().cost(plugin.ranks().get("B"), 0));
        assertEquals(2750, plugin.ranks().cost(plugin.ranks().get("B"), 1), 1e-6);
        assertEquals(5000, plugin.ranks().cost(plugin.ranks().get("B"), 10), 1e-6);
        assertEquals(2500 * 1001.0, plugin.ranks().cost(plugin.ranks().get("B"), 10_000), 1e-3);

        data(player).prestige(1);
        setBalance(player, 2700);
        player.performCommand("rankup");
        assertEquals("A", plugin.ranks().of(data(player)).name(), "2,700 is not enough at prestige 1");
        setBalance(player, 2750);
        player.performCommand("rankup");
        assertEquals("B", plugin.ranks().of(data(player)).name());
    }

    @Test
    void prestigeFeeGrowsWithPrestige() {
        assertEquals(50_000_000, plugin.prestige().prestigeCost(0));
        assertEquals(55_000_000, plugin.prestige().prestigeCost(1), 1e-3);
        assertEquals(100_000_000, plugin.prestige().prestigeCost(10), 1e-3);
    }

    @Test
    void tenthPrestigeUnlocksAutosellAndPaysOut() {
        PlayerMock player = join("Steve");
        data(player).prestige(9);
        setRank(player, "Z");
        assertFalse(player.hasPermission("prisoncore.autosell"));
        setBalance(player, plugin.prestige().prestigeCost(9));

        player.performCommand("prestige");

        PlayerData data = data(player);
        assertEquals(10, data.prestige());
        assertEquals(500_000, balance(player));
        assertEquals(0.25, data.bonusMultiplier(), 1e-9);
        assertTrue(player.hasPermission("prisoncore.autosell"), "milestone permission should be granted");
        assertTrue(allMessages(player).contains("/autosell"));
    }

    @Test
    void milestonePermissionsSurviveRejoining() {
        PlayerMock player = join("Steve");
        data(player).prestige(12);
        player.disconnect();
        assertFalse(player.hasPermission("prisoncore.autosell"));
        player.reconnect();
        assertTrue(player.hasPermission("prisoncore.autosell"));
    }

    @Test
    void milestoneCommandsRun() {
        PlayerMock player = join("Steve");
        editFile("prestige.yml", config ->
                config.set("milestones.50.commands", List.of("eco give %player% %prestige%")));
        data(player).prestige(49);
        setRank(player, "Z");
        setBalance(player, plugin.prestige().prestigeCost(49));
        player.performCommand("prestige");
        assertEquals(50, data(player).prestige());
        // $5M prize plus the command's 50.
        assertEquals(5_000_050, balance(player));
        // +0.50 from the milestone, and 50 is also a multiple of 25 so the repeating +0.05 is paid too.
        assertEquals(0.55, data(player).bonusMultiplier(), 1e-9);
    }

    @Test
    void repeatingPrizeIsPaidEveryTwentyFivePrestiges() {
        PlayerMock player = join("Steve");
        for (long target : new long[]{25, 75}) {
            PlayerData data = data(player);
            data.prestige(target - 1);
            data.bonusMultiplier(0);
            setRank(player, "Z");
            setBalance(player, plugin.prestige().prestigeCost(target - 1));
            player.performCommand("prestige");
            assertEquals(target, data.prestige());
            assertEquals(0.05, data.bonusMultiplier(), 1e-9, "prestige " + target + " should pay the repeating prize");
        }
        // Prestige 26 is neither a milestone nor a multiple of 25.
        PlayerData data = data(player);
        data.prestige(25);
        data.bonusMultiplier(0);
        setRank(player, "Z");
        setBalance(player, plugin.prestige().prestigeCost(25));
        player.performCommand("prestige");
        assertEquals(26, data.prestige());
        assertEquals(0, data.bonusMultiplier(), 1e-9);
    }

    @Test
    void everyDefaultMilestoneExists() {
        Map<Long, ?> milestones = plugin.prestige().milestones();
        for (long level : new long[]{1, 10, 50, 100, 500, 1000, 5000, 10_000, 50_000, 100_000}) {
            assertTrue(milestones.containsKey(level), "missing milestone " + level);
        }
        assertEquals(10L, plugin.prestige().nextMilestone(1));
        assertEquals(10_000L, plugin.prestige().nextMilestone(5000));
        assertNull(plugin.prestige().nextMilestone(100_000));
    }

    @Test
    void prestigeMaxKeepsGoingWhileTheMoneyLasts() {
        editFile("prestige.yml", config -> {
            config.set("reset-balance", false);
            config.set("milestones", null);
            config.set("repeating", null);
        });
        PlayerMock player = join("Steve");
        // Work out what three full prestiges cost, then give exactly that plus a little.
        double needed = 0;
        for (long p = 0; p < 3; p++) {
            needed += plugin.ranks().totalCost(p) + plugin.prestige().prestigeCost(p);
        }
        setBalance(player, needed + 10);

        player.performCommand("prestigemax");

        assertEquals(3, data(player).prestige());
        assertEquals("A", plugin.ranks().of(data(player)).name());
        assertEquals(10, balance(player), 1e-3);
        assertTrue(allMessages(player).contains("3 time(s)"));
    }

    @Test
    void prestigeMaxWithResetBalanceDoesOnePrestige() {
        PlayerMock player = join("Steve");
        setBalance(player, 1e15);
        player.performCommand("prestigemax");
        assertEquals(1, data(player).prestige());
        // Wiped, then the $50K first-prestige prize. That buys ranks at the prestige 1 prices
        // (B 2,750 + C 4,180 + D 6,160 + E 9,240 + F 14,300 = 36,630); G would cost 20,900.
        assertEquals("F", plugin.ranks().of(data(player)).name());
        assertEquals(50_000 - 36_630, balance(player), 1e-3);
    }

    @Test
    void prestigeCanBeCapped() {
        editFile("prestige.yml", config -> config.set("max-prestige", 2));
        PlayerMock player = join("Steve");
        data(player).prestige(2);
        setRank(player, "Z");
        setBalance(player, 1e15);
        player.performCommand("prestige");
        assertEquals(2, data(player).prestige());
        assertTrue(allMessages(player).contains("highest prestige"));
    }

    @Test
    void prestigeCanBeTurnedOff() {
        editFile("prestige.yml", config -> config.set("enabled", false));
        PlayerMock player = join("Steve");
        setRank(player, "Z");
        setBalance(player, 1e15);
        player.performCommand("prestige");
        assertEquals(0, data(player).prestige());
        assertTrue(allMessages(player).contains("turned off"));
    }

    @Test
    void exponentialModeAndCap() {
        editFile("prestige.yml", config -> {
            config.set("rank-cost.mode", "EXPONENTIAL");
            config.set("rank-cost.increase-per-prestige", 1.0);
            config.set("rank-cost.max-multiplier", 100);
        });
        assertEquals(1, plugin.prestige().rankCostMultiplier(0));
        assertEquals(2, plugin.prestige().rankCostMultiplier(1));
        assertEquals(8, plugin.prestige().rankCostMultiplier(3));
        assertEquals(100, plugin.prestige().rankCostMultiplier(50), "capped by max-multiplier");
    }

    @Test
    void veryHighPrestigeNeverBreaksTheMaths() {
        PlayerMock player = join("Steve");
        data(player).prestige(5_000_000_000L);
        double cost = plugin.ranks().cost(plugin.ranks().get("Z"), data(player).prestige());
        assertTrue(cost > 0 && !Double.isInfinite(cost) && !Double.isNaN(cost));
        setRank(player, "Z");
        setBalance(player, plugin.prestige().prestigeCost(data(player).prestige()));
        player.performCommand("prestige");
        assertEquals(5_000_000_001L, data(player).prestige());
    }

    @Test
    void prestigesCommandListsPrizes() {
        PlayerMock player = join("Steve");
        data(player).prestige(10);
        player.performCommand("prestiges");
        String page1 = allMessages(player);
        assertTrue(page1.contains("✔ Prestige 1 - $50K, +0.1x sell multiplier"), page1);
        assertTrue(page1.contains("✔ Prestige 10 - $500K, +0.25x sell multiplier and /autosell"), page1);
        assertTrue(page1.contains("✘ Prestige 50"), page1);
        assertTrue(page1.contains("Prestige 100,000"), page1);
        player.performCommand("prestiges 2");
        assertTrue(allMessages(player).contains("Every 25 prestiges"));
    }

    @Test
    void ranksListShowsPricesAtYourPrestige() {
        PlayerMock player = join("Steve");
        data(player).prestige(10);
        player.performCommand("ranks list");
        String text = allMessages(player);
        assertTrue(text.contains("[B] - $5K"), text);
        assertTrue(text.contains("[A] - - < you are here"), text);
    }
}
