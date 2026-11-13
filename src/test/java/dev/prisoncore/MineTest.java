package dev.prisoncore;

import dev.prisoncore.mine.Mine;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineTest extends PrisonTestBase {

    /** Counts every block type inside a mine. */
    private Map<Material, Integer> census(Mine mine) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (int x = mine.minX(); x <= mine.maxX(); x++) {
            for (int y = mine.minY(); y <= mine.maxY(); y++) {
                for (int z = mine.minZ(); z <= mine.maxZ(); z++) {
                    counts.merge(world.getBlockAt(x, y, z).getType(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    /** Breaks a block as the player, with the given drops standing in for vanilla loot. */
    private BlockBreakEvent mineBlock(PlayerMock player, Block block, ItemStack... drops) {
        ((BlockMock) block).setDrops(List.of(drops));
        return new PlayerSimulation(player).simulateBlockBreak(block);
    }

    private int count(PlayerMock player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    @Test
    void creatingARankMineFillsItWithThatRanksBlocks() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 9, 69, 9);

        assertNotNull(mine);
        assertEquals(1000, mine.volume());
        assertEquals("A", mine.requiredRank());
        assertFalse(mine.resetting());

        Map<Material, Integer> counts = census(mine);
        assertEquals(Set.of(Material.STONE, Material.ANDESITE, Material.COAL_ORE), counts.keySet());
        // Preset is 60/30/10; allow generous room for randomness.
        assertTrue(counts.get(Material.STONE) > 500 && counts.get(Material.STONE) < 700, counts.toString());
        assertTrue(counts.get(Material.ANDESITE) > 220 && counts.get(Material.ANDESITE) < 380, counts.toString());
        assertTrue(counts.get(Material.COAL_ORE) > 50 && counts.get(Material.COAL_ORE) < 160, counts.toString());
        // Nothing outside the region was touched.
        assertEquals(Material.AIR, world.getBlockAt(10, 65, 5).getType());
        assertEquals(Material.AIR, world.getBlockAt(5, 70, 5).getType());
    }

    @Test
    void cornersCanBeGivenInAnyOrder() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 9, 69, 9, 0, 60, 0);
        assertEquals(0, mine.minX());
        assertEquals(60, mine.minY());
        assertEquals(9, mine.maxZ());
        assertEquals(1000, mine.volume());
    }

    @Test
    void theWandSelectsCorners() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison wand");
        ItemStack wand = admin.getInventory().getItemInMainHand().getType() == Material.BLAZE_ROD
                ? admin.getInventory().getItemInMainHand() : admin.getInventory().getItem(1);
        assertNotNull(wand);
        assertTrue(plugin.mines().isWand(wand));

        PlayerInteractEvent left = new PlayerInteractEvent(admin, Action.LEFT_CLICK_BLOCK, wand,
                world.getBlockAt(20, 60, 20), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(left);
        PlayerInteractEvent right = new PlayerInteractEvent(admin, Action.RIGHT_CLICK_BLOCK, wand,
                world.getBlockAt(24, 64, 24), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(right);

        assertTrue(plugin.mines().selection(admin).complete());
        assertEquals(125, plugin.mines().selection(admin).volume());
        admin.performCommand("prison mine create wandmine");
        tick(5);
        assertEquals(125, plugin.mines().get("wandmine").volume());
    }

    @Test
    void theWandDoesNothingForNormalPlayers() {
        PlayerMock admin = admin("Admin");
        PlayerMock player = join("Steve");
        ItemStack wand = plugin.mines().wand();
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, wand,
                world.getBlockAt(20, 60, 20), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(click);
        assertFalse(plugin.mines().selection(player).complete());
        assertNotNull(admin);
    }

    @Test
    void creatingAMineNeedsASelectionAndAUniqueName() {
        PlayerMock admin = admin("Admin");
        admin.performCommand("prison mine create A");
        assertNull(plugin.mines().get("A"));
        assertTrue(allMessages(admin).contains("Select both corners first"));

        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        admin.performCommand("prison mine pos1");
        admin.performCommand("prison mine pos2");
        admin.performCommand("prison mine create a");
        assertTrue(allMessages(admin).contains("already exists"));
        assertEquals(1, plugin.mines().all().size());
    }

    @Test
    void hugeSelectionsAreRefused() {
        PlayerMock admin = admin("Admin");
        plugin.getConfig().set("mines.max-volume", 100);
        assertNull(createMine(admin, "A", 0, 60, 0, 9, 69, 9));
    }

    @Test
    void playersCanOnlyMineWhatTheirRankUnlocks() {
        PlayerMock admin = admin("Admin");
        Mine mineA = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        Mine mineB = createMine(admin, "B", 20, 60, 0, 24, 64, 4);
        PlayerMock player = join("Steve");

        assertTrue(plugin.mines().canAccess(player, mineA));
        assertFalse(plugin.mines().canAccess(player, mineB));
        assertTrue(player.hasPermission("prisoncore.mine.a"));
        assertFalse(player.hasPermission("prisoncore.mine.b"));

        Block inB = world.getBlockAt(22, 62, 2);
        Material before = inB.getType();
        BlockBreakEvent denied = mineBlock(player, inB, new ItemStack(Material.COBBLESTONE));
        assertTrue(denied.isCancelled());
        assertEquals(before, inB.getType());
        assertEquals(0, data(player).blocksMined());
        assertTrue(allMessages(player).contains("You need: Rank B"));

        setBalance(player, 2500);
        player.performCommand("rankup");
        assertTrue(player.hasPermission("prisoncore.mine.b"));
        BlockBreakEvent allowed = mineBlock(player, inB, new ItemStack(Material.COBBLESTONE));
        assertFalse(allowed.isCancelled());
        assertEquals(Material.AIR, inB.getType());
        // Higher ranks keep access to lower mines.
        assertTrue(plugin.mines().canAccess(player, mineA));
    }

    @Test
    void minedBlocksGoStraightIntoTheInventory() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        player.getInventory().clear();

        BlockBreakEvent event = mineBlock(player, world.getBlockAt(2, 62, 2), new ItemStack(Material.COBBLESTONE));

        assertFalse(event.isCancelled());
        assertFalse(event.isDropItems(), "drops should not also fall on the floor");
        assertEquals(1, count(player, Material.COBBLESTONE));
        assertEquals(1, data(player).blocksMined());
        assertEquals(1, mine.mined());
    }

    @Test
    void rawOresAreSmeltedOnPickup() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        player.getInventory().clear();

        mineBlock(player, world.getBlockAt(1, 62, 1), new ItemStack(Material.RAW_IRON, 2));
        mineBlock(player, world.getBlockAt(2, 62, 1), new ItemStack(Material.RAW_GOLD));
        mineBlock(player, world.getBlockAt(3, 62, 1), new ItemStack(Material.RAW_COPPER, 4));

        assertEquals(2, count(player, Material.IRON_INGOT));
        assertEquals(1, count(player, Material.GOLD_INGOT));
        assertEquals(4, count(player, Material.COPPER_INGOT));
        assertEquals(0, count(player, Material.RAW_IRON));

        plugin.getConfig().set("mines.auto-smelt", false);
        mineBlock(player, world.getBlockAt(1, 63, 1), new ItemStack(Material.RAW_IRON));
        assertEquals(1, count(player, Material.RAW_IRON));
    }

    @Test
    void fortuneWorksOnEveryBlockWhenEnabled() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 9, 69, 9);
        mine.blocks().clear();
        mine.blocks().put(Material.STONE, 100.0);
        plugin.mines().reset(mine);
        tick(5);

        PlayerMock player = join("Steve");
        player.getInventory().clear();
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
        player.getInventory().setItemInMainHand(pickaxe);

        int blocks = 200;
        int mined = 0;
        outer:
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 9; z++) {
                for (int y = 69; y >= 68; y--) {
                    mineBlock(player, world.getBlockAt(x, y, z), new ItemStack(Material.COBBLESTONE));
                    if (++mined >= blocks) {
                        break outer;
                    }
                }
            }
        }
        int cobble = count(player, Material.COBBLESTONE);
        // Fortune III averages 2.2x; 200 blocks should land well above 200 and below the 4x maximum.
        assertTrue(cobble > 300 && cobble <= 800, "got " + cobble + " cobblestone from 200 blocks");
    }

    @Test
    void fortuneCanBeLimitedToOres() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 9, 69, 9);
        plugin.getConfig().set("mines.fortune-all-blocks", false);
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
        player.getInventory().setItemInMainHand(pickaxe);
        for (int x = 0; x < 10; x++) {
            mineBlock(player, world.getBlockAt(x, 69, 0), new ItemStack(Material.COBBLESTONE));
        }
        assertEquals(10, count(player, Material.COBBLESTONE));
    }

    @Test
    void aFullInventoryDropsTheRestAndWarns() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        // Fill every slot (the simulated server also uses armour and off-hand slots for pickups).
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        BlockBreakEvent event = mineBlock(player, world.getBlockAt(2, 62, 2), new ItemStack(Material.COBBLESTONE));
        assertFalse(event.isCancelled());
        assertEquals(0, count(player, Material.COBBLESTONE));
        assertTrue(allMessages(player).contains("inventory is full"));
    }

    @Test
    void autosellPaysInsteadOfFillingTheInventory() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        player.getInventory().clear();

        // Without the permission the command is refused.
        player.performCommand("autosell");
        assertFalse(data(player).autosell());

        player.addAttachment(plugin, "prisoncore.autosell", true);
        player.performCommand("autosell");
        assertTrue(data(player).autosell());

        mineBlock(player, world.getBlockAt(2, 62, 2), new ItemStack(Material.COAL, 3));
        assertEquals(21, balance(player), 1e-9);
        assertEquals(0, count(player, Material.COAL));

        // Things with no price still go to the inventory.
        mineBlock(player, world.getBlockAt(3, 62, 2), new ItemStack(Material.FLINT));
        assertEquals(1, count(player, Material.FLINT));

        player.performCommand("autosell");
        mineBlock(player, world.getBlockAt(1, 62, 2), new ItemStack(Material.COAL));
        assertEquals(21, balance(player), 1e-9);
        assertEquals(1, count(player, Material.COAL));
    }

    @Test
    void mineRefillsOnceEnoughHasBeenMined() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 60, 3); // 20 blocks
        PlayerMock player = join("Steve");
        player.getInventory().clear();
        player.teleport(new Location(world, 30, 80, 30));
        assertEquals(70, plugin.mines().threshold(mine));

        int broken = 0;
        for (int x = 0; x <= 4 && broken < 13; x++) {
            for (int z = 0; z <= 3 && broken < 13; z++) {
                mineBlock(player, world.getBlockAt(x, 60, z), new ItemStack(Material.COBBLESTONE));
                broken++;
            }
        }
        tick(3);
        assertEquals(13, mine.mined(), "65% mined - not enough for a reset yet");
        assertEquals(Material.AIR, world.getBlockAt(0, 60, 0).getType());

        mineBlock(player, world.getBlockAt(4, 60, 3), new ItemStack(Material.COBBLESTONE));
        tick(3);
        assertEquals(0, mine.mined(), "70% mined should trigger a reset");
        assertEquals(100, mine.remainingPercent());
        assertFalse(census(mine).containsKey(Material.AIR));
    }

    @Test
    void mineRefillsOnATimerOnlyIfItWasUsed() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        admin.performCommand("prison mine settimer A 5");
        admin.performCommand("prison mine setthreshold A off");
        assertEquals(5, plugin.mines().interval(mine));
        assertEquals(0, plugin.mines().threshold(mine));
        PlayerMock player = join("Steve");
        player.teleport(new Location(world, 30, 80, 30));
        tick(60); // Let the automatic refill that follows server start-up finish first.

        // Untouched: the timer runs out but nothing needs doing. Dig a hole by hand to prove it is left alone.
        world.getBlockAt(0, 64, 0).setType(Material.AIR);
        tick(20 * 12);
        assertEquals(Material.AIR, world.getBlockAt(0, 64, 0).getType());

        mineBlock(player, world.getBlockAt(2, 64, 2), new ItemStack(Material.COBBLESTONE));
        assertEquals(1, mine.mined());
        tick(20 * 7);
        assertEquals(0, mine.mined());
        assertFalse(census(mine).containsKey(Material.AIR));
    }

    @Test
    void playersInsideAreMovedOutWhenTheMineRefills() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        world.getBlockAt(2, 62, 2).setType(Material.AIR);
        player.teleport(new Location(world, 2.5, 62, 2.5));
        messages(player);

        admin.performCommand("prison mine reset A");
        tick(5);

        assertFalse(mine.contains(player.getLocation()), "player should have been moved out of the mine");
        assertEquals(mine.spawn().getBlockY(), player.getLocation().getBlockY());
        assertTrue(allMessages(player).contains("Mine A has been reset"));
    }

    @Test
    void bigMinesRefillOverSeveralTicksWithoutFreezing() {
        PlayerMock admin = admin("Admin");
        plugin.getConfig().set("mines.reset-ms-per-tick", 1);
        admin.teleport(new Location(world, 0.5, 60, 0.5));
        admin.performCommand("prison mine pos1");
        admin.teleport(new Location(world, 79.5, 99, 79.5));
        admin.performCommand("prison mine pos2");
        admin.teleport(new Location(world, 0.5, 101, 0.5));
        admin.performCommand("prison mine create A");
        Mine mine = plugin.mines().get("A");
        assertEquals(256_000, mine.volume());

        tick(1);
        assertTrue(mine.resetting(), "a quarter of a million blocks should not be placed in one tick");
        // Nobody can mine while it is refilling.
        PlayerMock player = join("Steve");
        Block top = world.getBlockAt(0, 99, 0);
        assertTrue(mineBlock(player, top, new ItemStack(Material.COBBLESTONE)).isCancelled());

        int ticks = 1;
        while (mine.resetting() && ticks < 20_000) {
            tick(1);
            ticks++;
        }
        assertFalse(mine.resetting(), "the refill should finish");
        assertTrue(ticks > 3, "the work should have been spread over several ticks, took " + ticks);
        assertFalse(census(mine).containsKey(Material.AIR));
        assertEquals(0, mine.mined());
    }

    @Test
    void severalMinesResettingTogetherShareOneQueue() {
        PlayerMock admin = admin("Admin");
        Mine a = createMine(admin, "A", 0, 60, 0, 9, 69, 9);
        Mine b = createMine(admin, "B", 20, 60, 0, 29, 69, 9);
        Mine c = createMine(admin, "C", 40, 60, 0, 49, 69, 9);
        for (Mine mine : List.of(a, b, c)) {
            world.getBlockAt(mine.minX(), 69, 0).setType(Material.AIR);
        }
        admin.performCommand("prison mine reset all");
        assertTrue(a.resetting() && b.resetting() && c.resetting());
        tick(10);
        for (Mine mine : List.of(a, b, c)) {
            assertFalse(mine.resetting());
            assertFalse(census(mine).containsKey(Material.AIR), "mine " + mine.name());
        }
        // Resetting an already-queued mine is refused rather than queued twice.
        assertTrue(plugin.mines().reset(a));
        assertFalse(plugin.mines().reset(a));
        tick(5);
        assertFalse(a.resetting());
    }

    @Test
    void aSpawnInsideTheMineIsNeverUsed() {
        PlayerMock admin = admin("Admin");
        admin.teleport(new Location(world, 0.5, 60, 0.5));
        admin.performCommand("prison mine pos1");
        admin.teleport(new Location(world, 4.5, 64, 4.5));
        admin.performCommand("prison mine pos2");
        // Still standing in the corner of the selection when creating it.
        admin.performCommand("prison mine create A");
        tick(5);
        Mine mine = plugin.mines().get("A");
        assertFalse(mine.hasSpawn());
        assertFalse(mine.contains(mine.spawn()), "the default arrival point is on top of the mine");
        assertEquals(65, mine.spawn().getBlockY());
        assertTrue(allMessages(admin).contains("standing inside it"));
        assertFalse(mine.contains(admin.getLocation()), "the admin was moved out when it filled");

        world.getBlockAt(2, 62, 2).setType(Material.AIR);
        admin.teleport(new Location(world, 2.5, 62, 2.5));
        admin.performCommand("prison mine setspawn A");
        assertFalse(mine.hasSpawn());
        assertTrue(allMessages(admin).contains("standing inside the mine"));
    }

    @Test
    void normalPlayersCannotPlaceBlocksInMines() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        world.getBlockAt(2, 64, 2).setType(Material.AIR);

        BlockPlaceEvent denied = new PlayerSimulation(player).simulateBlockPlace(Material.DIRT,
                new Location(world, 2, 64, 2));
        assertTrue(denied == null || denied.isCancelled());
        assertEquals(Material.AIR, world.getBlockAt(2, 64, 2).getType());

        BlockPlaceEvent allowed = new PlayerSimulation(admin).simulateBlockPlace(Material.DIRT,
                new Location(world, 2, 64, 2));
        assertNotNull(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void protectedWorldsOnlyAllowMining() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        PlayerMock player = join("Steve");
        Block outside = world.getBlockAt(40, 60, 40);
        outside.setType(Material.STONE);

        // Off by default: PrisonCore leaves the rest of the world alone.
        assertFalse(mineBlock(player, outside).isCancelled());

        outside.setType(Material.STONE);
        plugin.getConfig().set("protection.worlds", List.of("prison"));
        assertTrue(mineBlock(player, outside).isCancelled());
        assertFalse(mineBlock(player, world.getBlockAt(2, 62, 2)).isCancelled(), "mines still work");
        assertFalse(mineBlock(admin, outside).isCancelled(), "admins can still build");
    }

    @Test
    void creativeAdminsDoNotCollectDrops() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        admin.getInventory().clear();
        admin.setGameMode(GameMode.CREATIVE);
        mineBlock(admin, world.getBlockAt(2, 62, 2), new ItemStack(Material.COBBLESTONE));
        assertEquals(0, count(admin, Material.COBBLESTONE));
        assertEquals(0, data(admin).blocksMined());
    }

    @Test
    void mineCommandTeleportsToTheBestUnlockedMine() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        Mine mineB = createMine(admin, "B", 20, 60, 0, 24, 64, 4);
        createMine(admin, "C", 40, 60, 0, 44, 64, 4);
        PlayerMock player = join("Steve");
        data(player).rank("B");
        plugin.permissions().refresh(player);

        player.performCommand("mine");
        assertTrue(mineB.near(player.getLocation(), 2), "should arrive at mine B, the best one unlocked");
        assertTrue(allMessages(player).contains("Welcome to mine B"));

        player.performCommand("mine C");
        assertTrue(mineB.near(player.getLocation(), 2), "mine C is locked, so the player stays put");
        assertTrue(allMessages(player).contains("You need: Rank C"));

        player.performCommand("mine a");
        assertTrue(plugin.mines().get("A").near(player.getLocation(), 2));
    }

    @Test
    void prestigeMinesAndPermissionMines() {
        PlayerMock admin = admin("Admin");
        Mine prestigeMine = createMine(admin, "P5", 0, 60, 0, 4, 64, 4);
        Mine vipMine = createMine(admin, "vip", 20, 60, 0, 24, 64, 4);
        admin.performCommand("prison mine setprestige P5 5");
        PlayerMock player = join("Steve");

        assertFalse(plugin.mines().canAccess(player, prestigeMine));
        assertFalse(plugin.mines().canAccess(player, vipMine), "a mine with no rank needs its permission");
        assertEquals("Prestige 5", plugin.mines().requirementText(prestigeMine));
        assertEquals("prisoncore.mine.vip", plugin.mines().requirementText(vipMine));

        data(player).prestige(5);
        plugin.permissions().refresh(player);
        assertTrue(plugin.mines().canAccess(player, prestigeMine));
        assertTrue(player.hasPermission("prisoncore.mine.p5"));

        player.addAttachment(plugin, "prisoncore.mine.vip", true);
        assertTrue(plugin.mines().canAccess(player, vipMine));

        admin.performCommand("prison mine setrank vip A");
        PlayerMock other = join("Alex");
        assertTrue(plugin.mines().canAccess(other, vipMine), "linking it to rank A opens it to everyone");
    }

    @Test
    void minesAreSavedAndSurviveAReload() {
        PlayerMock admin = admin("Admin");
        createMine(admin, "A", 0, 60, 0, 4, 64, 4);
        admin.performCommand("prison mine addblock A DIAMOND_BLOCK 50");
        admin.performCommand("prison mine removeblock A ANDESITE");
        admin.performCommand("prison mine settimer A 120");
        admin.performCommand("prison mine setthreshold A 45");
        admin.performCommand("prison mine setprestige A 3");
        admin.teleport(new Location(world, 7.5, 70, 7.5, 90f, 10f));
        admin.performCommand("prison mine setspawn A");

        plugin.reload();

        Mine mine = plugin.mines().get("A");
        assertNotNull(mine);
        assertEquals(125, mine.volume());
        assertEquals(Set.of(Material.STONE, Material.COAL_ORE, Material.DIAMOND_BLOCK), mine.blocks().keySet());
        assertEquals(50.0, mine.blocks().get(Material.DIAMOND_BLOCK));
        assertEquals(120, mine.resetInterval());
        assertEquals(45.0, mine.resetThreshold());
        assertEquals("A", mine.requiredRank());
        assertEquals(3, mine.requiredPrestige());
        assertEquals(7.5, mine.spawn().getX());
        assertEquals(70, mine.spawn().getY());
        assertEquals(90f, mine.spawn().getYaw());
    }

    @Test
    void presetsRedefineAndDelete() {
        PlayerMock admin = admin("Admin");
        Mine mine = createMine(admin, "A", 0, 60, 0, 4, 64, 4);

        admin.performCommand("prison mine preset A Z");
        assertEquals(Set.of(Material.QUARTZ_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK),
                mine.blocks().keySet());

        admin.teleport(new Location(world, 50.5, 60, 50.5));
        admin.performCommand("prison mine pos1");
        admin.teleport(new Location(world, 52.5, 62, 52.5));
        admin.performCommand("prison mine pos2");
        admin.performCommand("prison mine redefine A");
        tick(5);
        assertEquals(27, mine.volume());
        assertEquals(50, mine.minX());
        assertFalse(census(mine).containsKey(Material.AIR), "the new region should be filled");

        admin.performCommand("prison mine delete A");
        assertNull(plugin.mines().get("A"));
        plugin.reload();
        assertTrue(plugin.mines().all().isEmpty());
    }

    @Test
    void everyRankHasAUsablePreset() {
        PlayerMock admin = admin("Admin");
        int offset = 0;
        for (var rank : plugin.ranks().all()) {
            Mine mine = createMine(admin, rank.name(), offset, 60, 100, offset + 2, 62, 102);
            assertNotNull(mine, "mine " + rank.name());
            assertEquals(3, mine.blocks().size(), "preset for " + rank.name() + " should have three blocks");
            for (Material material : mine.blocks().keySet()) {
                assertTrue(material.isBlock(), material + " in preset " + rank.name());
            }
            assertFalse(census(mine).containsKey(Material.AIR), "mine " + rank.name() + " should be full");
            offset += 5;
        }
        admin.performCommand("prison setup");
        String checklist = allMessages(admin);
        assertTrue(checklist.contains("26/26"), checklist);
        assertTrue(checklist.contains("Everything is set up"), checklist);
    }

    @Test
    void laterMinesAreWorthMoreThanEarlierOnes() {
        PlayerMock admin = admin("Admin");
        double previous = 0;
        int offset = 0;
        for (var rank : plugin.ranks().all()) {
            Mine mine = createMine(admin, rank.name(), offset, 60, 100, offset + 1, 61, 101);
            double value = 0;
            for (Map.Entry<Material, Double> block : mine.blocks().entrySet()) {
                value += block.getValue() / mine.totalWeight() * blockValue(block.getKey());
            }
            assertTrue(value > previous, "mine " + rank.name() + " (" + value + ") should beat the one before ("
                    + previous + ")");
            previous = value;
            offset += 4;
        }
    }

    /** Average sale value of mining one block with a plain pickaxe, using the default prices. */
    private double blockValue(Material block) {
        return switch (block) {
            case STONE -> plugin.sell().price(Material.COBBLESTONE);
            case COAL_ORE -> plugin.sell().price(Material.COAL);
            case IRON_ORE -> plugin.sell().price(Material.RAW_IRON);
            case GOLD_ORE -> plugin.sell().price(Material.RAW_GOLD);
            case COPPER_ORE -> plugin.sell().price(Material.RAW_COPPER) * 3.5;
            case REDSTONE_ORE -> plugin.sell().price(Material.REDSTONE) * 4.5;
            case LAPIS_ORE -> plugin.sell().price(Material.LAPIS_LAZULI) * 6.5;
            case DIAMOND_ORE -> plugin.sell().price(Material.DIAMOND);
            case EMERALD_ORE -> plugin.sell().price(Material.EMERALD);
            case NETHER_QUARTZ_ORE -> plugin.sell().price(Material.QUARTZ);
            default -> {
                double price = plugin.sell().price(block);
                assertTrue(price > 0, block + " is used in a preset but has no sell price");
                yield price;
            }
        };
    }
}
