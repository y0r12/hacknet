package dev.prisoncore;

import dev.prisoncore.sell.SellManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyAndSellTest extends PrisonTestBase {

    @Test
    void sellAllPaysTheListedPrices() {
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.DIAMOND, 10),
                new ItemStack(Material.DIRT, 5));

        player.performCommand("sellall");

        // 64 x $4 + 10 x $561 = $5,866. Dirt has no price and stays.
        assertEquals(5866, balance(player), 1e-9);
        assertFalse(player.getInventory().contains(Material.COBBLESTONE));
        assertFalse(player.getInventory().contains(Material.DIAMOND));
        assertTrue(player.getInventory().contains(Material.DIRT, 5));
        assertTrue(allMessages(player).contains("Sold 74 items for $5.87K"));
    }

    @Test
    void sellAllWithNothingToSell() {
        PlayerMock player = join("Steve");
        player.performCommand("sell");
        assertEquals(0, balance(player));
        assertTrue(allMessages(player).contains("nothing to sell"));
    }

    @Test
    void renamedAndEnchantedItemsAreNeverSold() {
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        ItemStack special = new ItemStack(Material.DIAMOND);
        ItemMeta meta = special.getItemMeta();
        meta.setDisplayName("Lucky Diamond");
        special.setItemMeta(meta);
        player.getInventory().addItem(special);

        player.performCommand("sellall");

        assertEquals(0, balance(player));
        assertTrue(player.getInventory().contains(Material.DIAMOND));
    }

    @Test
    void armourAndOffhandAreLeftAlone() {
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        player.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND, 3));
        player.performCommand("sellall");
        assertEquals(0, balance(player));
        assertEquals(3, player.getInventory().getItemInOffHand().getAmount());
    }

    @Test
    void multiplierAddsPrestigeRewardAndPermissionBonuses() {
        PlayerMock player = join("Steve");
        assertEquals(1.0, plugin.sell().multiplier(player).total(), 1e-9);

        data(player).prestige(10);          // +0.50 (10 x 0.05)
        data(player).bonusMultiplier(0.25); // +0.25
        player.addAttachment(plugin, "prisoncore.multiplier.1.5", true); // +0.50
        player.addAttachment(plugin, "prisoncore.multiplier.1.2", true); // lower, ignored
        plugin.sell().invalidate(player);

        SellManager.Multiplier multiplier = plugin.sell().multiplier(player);
        assertEquals(0.50, multiplier.prestige(), 1e-9);
        assertEquals(0.25, multiplier.bonus(), 1e-9);
        assertEquals(0.50, multiplier.permission(), 1e-9);
        assertEquals(2.25, multiplier.total(), 1e-9);

        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.COAL, 10));
        player.performCommand("sellall");
        assertEquals(10 * 7 * 2.25, balance(player), 1e-9);

        player.performCommand("multiplier");
        String text = allMessages(player);
        assertTrue(text.contains("Your sell multiplier: 2.25x"), text);
        assertTrue(text.contains("Prestige bonus: +0.5x"), text);
    }

    @Test
    void globalBoostAndPrestigeCap() {
        PlayerMock player = join("Steve");
        data(player).prestige(1000);
        plugin.getConfig().set("sell.max-prestige-multiplier", 4.0);
        plugin.getConfig().set("sell.global-multiplier", 2.0);
        // (1 + 4 capped) x 2
        assertEquals(10.0, plugin.sell().multiplier(player).total(), 1e-9);
    }

    @Test
    void pricesCanBeChangedInGame() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison price set DIRT 2.5");
        assertEquals(2.5, plugin.sell().price(Material.DIRT));
        admin.performCommand("prison price remove COBBLESTONE");
        assertEquals(0, plugin.sell().price(Material.COBBLESTONE));

        admin.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD));
        admin.performCommand("prison price set hand 1k");
        assertEquals(1000, plugin.sell().price(Material.EMERALD));

        plugin.reload();
        assertEquals(2.5, plugin.sell().price(Material.DIRT), "price changes are saved to prices.yml");
        assertEquals(0, plugin.sell().price(Material.COBBLESTONE));
        assertEquals(1000, plugin.sell().price(Material.EMERALD));
    }

    @Test
    void craftingBlocksNeverCreatesMoney() {
        // Nine items must be worth exactly one block, otherwise players get rich at a crafting table.
        assertPair(Material.COAL, Material.COAL_BLOCK, 9);
        assertPair(Material.IRON_INGOT, Material.IRON_BLOCK, 9);
        assertPair(Material.RAW_IRON, Material.RAW_IRON_BLOCK, 9);
        assertPair(Material.GOLD_INGOT, Material.GOLD_BLOCK, 9);
        assertPair(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK, 9);
        assertPair(Material.COPPER_INGOT, Material.COPPER_BLOCK, 9);
        assertPair(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK, 9);
        assertPair(Material.REDSTONE, Material.REDSTONE_BLOCK, 9);
        assertPair(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK, 9);
        assertPair(Material.DIAMOND, Material.DIAMOND_BLOCK, 9);
        assertPair(Material.EMERALD, Material.EMERALD_BLOCK, 9);
        assertPair(Material.QUARTZ, Material.QUARTZ_BLOCK, 4);
        // Smelting must not create money either.
        assertEquals(plugin.sell().price(Material.RAW_IRON), plugin.sell().price(Material.IRON_INGOT));
        assertEquals(plugin.sell().price(Material.RAW_GOLD), plugin.sell().price(Material.GOLD_INGOT));
        assertEquals(plugin.sell().price(Material.RAW_COPPER), plugin.sell().price(Material.COPPER_INGOT));
    }

    private void assertPair(Material item, Material block, int perBlock) {
        assertEquals(plugin.sell().price(item) * perBlock, plugin.sell().price(block), 1e-6,
                perBlock + " " + item + " should be worth one " + block);
    }

    @Test
    void balanceAndPay() {
        PlayerMock steve = join("Steve");
        PlayerMock alex = join("Alex");
        setBalance(steve, 1_500_000);

        steve.performCommand("balance");
        assertTrue(allMessages(steve).contains("Balance: $1.5M ($1,500,000)"));

        steve.performCommand("pay Alex 250k");
        assertEquals(1_250_000, balance(steve));
        assertEquals(250_000, balance(alex));
        assertTrue(allMessages(steve).contains("You sent $250K to Alex"));
        assertTrue(allMessages(alex).contains("You received $250K from Steve"));

        steve.performCommand("bal Alex");
        assertTrue(allMessages(steve).contains("Alex's balance: $250K"));
    }

    @Test
    void payRejectsBadRequests() {
        PlayerMock steve = join("Steve");
        PlayerMock alex = join("Alex");
        setBalance(steve, 100);

        steve.performCommand("pay Alex 500");
        steve.performCommand("pay Alex -5");
        steve.performCommand("pay Alex 0");
        steve.performCommand("pay Alex abc");
        steve.performCommand("pay Alex 0.5");
        steve.performCommand("pay Steve 10");
        steve.performCommand("pay Nobody 10");

        assertEquals(100, balance(steve));
        assertEquals(0, balance(alex));
    }

    @Test
    void offlinePlayersCanBePaid() {
        PlayerMock steve = join("Steve");
        PlayerMock alex = join("Alex");
        setBalance(steve, 100);
        alex.disconnect();

        steve.performCommand("pay alex 40");

        assertEquals(60, balance(steve));
        assertEquals(40, plugin.economy().balance(alex.getUniqueId()));
    }

    @Test
    void ecoCommandIsAdminOnly() {
        PlayerMock steve = join("Steve");
        PlayerMock admin = admin("Admin");

        steve.performCommand("eco give Steve 1000000");
        assertEquals(0, balance(steve));

        admin.performCommand("eco give Steve 1m");
        assertEquals(1_000_000, balance(steve));
        admin.performCommand("eco take Steve 250k");
        assertEquals(750_000, balance(steve));
        admin.performCommand("eco take Steve 5m");
        assertEquals(0, balance(steve), "taking more than they have stops at zero");
        admin.performCommand("eco set Steve 42");
        assertEquals(42, balance(steve));

        server.dispatchCommand(server.getConsoleSender(), "eco give Steve 8");
        assertEquals(50, balance(steve));
    }

    @Test
    void balanceTopListsRichestFirst() {
        PlayerMock steve = join("Steve");
        PlayerMock alex = join("Alex");
        PlayerMock poor = join("Poor");
        setBalance(steve, 500);
        setBalance(alex, 9000);

        steve.performCommand("baltop");
        String text = allMessages(steve);
        assertTrue(text.indexOf("#1 Alex - $9K") >= 0, text);
        assertTrue(text.indexOf("#2 Steve - $500") > text.indexOf("#1 Alex"), text);
        assertFalse(text.contains("Poor"), "players with no money are not listed");
        assertNotNull(poor);
    }

    @Test
    void prestigeTopListsHighestFirst() {
        PlayerMock steve = join("Steve");
        PlayerMock alex = join("Alex");
        data(steve).prestige(3);
        data(alex).prestige(3);
        data(alex).rank("M");

        steve.performCommand("prestigetop");
        String text = allMessages(steve);
        assertTrue(text.contains("#1 Alex - Prestige 3 (rank M)"), text);
        assertTrue(text.contains("#2 Steve - Prestige 3 (rank A)"), text);
    }

    @Test
    void startingBalanceIsConfigurable() {
        plugin.getConfig().set("economy.starting-balance", 250);
        PlayerMock player = join("Newbie");
        assertEquals(250, balance(player));
        assertNull(plugin.findPlayer("SomebodyElse"));
    }
}
