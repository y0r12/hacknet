package dev.prisoncore.listener;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.mine.MineManager;
import dev.prisoncore.sell.SellManager;
import dev.prisoncore.util.Items;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Mining inside mines (access, auto-pickup, autosell, fortune), build protection and the selection wand. */
public final class MineListener implements Listener {

    private static final Map<Material, Material> SMELTED = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT);

    private static final long WARNING_COOLDOWN_MILLIS = 1500;

    private final PrisonCore plugin;
    private final Map<UUID, Long> lastWarning = new HashMap<>();

    public MineListener(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void forget(Player player) {
        lastWarning.remove(player.getUniqueId());
    }

    /** Sends a message at most once every 1.5 seconds so holding down the mouse does not flood chat. */
    private void warn(Player player, String key, Object... replacements) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < WARNING_COOLDOWN_MILLIS) {
            return;
        }
        lastWarning.put(player.getUniqueId(), now);
        plugin.messages().send(player, key, replacements);
    }

    private boolean protectedWorld(World world) {
        List<String> worlds = plugin.getConfig().getStringList("protection.worlds");
        if (worlds.isEmpty()) {
            return false;
        }
        for (String name : worlds) {
            if (name.equalsIgnoreCase(world.getName()) || name.equals("*")) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (plugin.mines().isWand(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }

        Mine mine = plugin.mines().at(block);
        if (mine == null) {
            if (protectedWorld(block.getWorld()) && !player.hasPermission("prisoncore.build")) {
                event.setCancelled(true);
                warn(player, "build-denied");
            }
            return;
        }
        if (mine.resetting()) {
            event.setCancelled(true);
            warn(player, "mine-resetting", "mine", mine.name());
            return;
        }
        if (!plugin.mines().canAccess(player, mine)) {
            event.setCancelled(true);
            warn(player, "mine-no-access", "mine", mine.name(),
                    "requirement", plugin.mines().requirementText(mine));
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            PlayerData data = plugin.data(player);
            data.blocksMined(data.blocksMined() + 1);
            if (plugin.getConfig().getBoolean("mines.auto-pickup", true)) {
                collectDrops(event, player, block, data);
            }
        }
        plugin.mines().blockMined(mine);
    }

    /** Sends the block's drops straight to the player (or sells them) instead of dropping them on the floor. */
    private void collectDrops(BlockBreakEvent event, Player player, Block block, PlayerData data) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> vanilla;
        try {
            vanilla = block.getDrops(tool, player);
        } catch (Throwable unsupported) {
            return; // Fall back to normal drops.
        }

        int fortune = tool.getType().isAir() ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
        boolean silkTouch = !tool.getType().isAir() && tool.containsEnchantment(Enchantment.SILK_TOUCH);
        // Vanilla already applies Fortune to ores. Optionally give every other block the same bonus.
        boolean bonusFortune = fortune > 0 && !silkTouch
                && plugin.getConfig().getBoolean("mines.fortune-all-blocks", true)
                && !block.getType().name().endsWith("_ORE");
        boolean autoSmelt = plugin.getConfig().getBoolean("mines.auto-smelt", true);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : vanilla) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) {
                continue;
            }
            int amount = drop.getAmount();
            if (bonusFortune) {
                int bonus = Math.max(0, random.nextInt(fortune + 2) - 1);
                amount = (int) Math.min(100_000L, (long) amount * (bonus + 1));
            }
            Material smelted = autoSmelt && Items.plain(drop) ? SMELTED.get(drop.getType()) : null;
            ItemStack template = smelted != null ? new ItemStack(smelted) : drop.clone();
            int max = Math.max(1, template.getMaxStackSize());
            while (amount > 0) {
                ItemStack stack = template.clone();
                stack.setAmount(Math.min(max, amount));
                amount -= stack.getAmount();
                drops.add(stack);
            }
        }

        event.setDropItems(false);
        int experience = event.getExpToDrop();
        if (experience > 0) {
            event.setExpToDrop(0);
            player.giveExp(experience);
        }

        if (data.autosell() && player.hasPermission("prisoncore.autosell")) {
            SellManager.SellResult sold = plugin.sell().sellDrops(player, drops);
            if (sold.items() > 0) {
                Text.actionBar(player, plugin.messages().get("autosell-earned", "total", plugin.money(sold.total()),
                        "items", sold.items(), "multiplier", Numbers.format(sold.multiplier())));
            }
        }

        boolean full = false;
        for (ItemStack stack : drops) {
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                full = true;
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), leftover);
            }
        }
        if (full) {
            warn(player, "inventory-full");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("prisoncore.build")) {
            return;
        }
        Block block = event.getBlock();
        if (plugin.mines().at(block) != null || protectedWorld(block.getWorld())) {
            event.setCancelled(true);
            warn(player, "build-denied");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.mines().isWand(event.getItem()) || !player.hasPermission("prisoncore.admin")) {
            return;
        }
        event.setCancelled(true);
        boolean first = action == Action.LEFT_CLICK_BLOCK;
        Block block = event.getClickedBlock();
        MineManager.Selection selection = plugin.mines().select(player, block, first);
        plugin.tell(player, "&aCorner " + (first ? "1" : "2") + " set to &f" + block.getX() + ", " + block.getY()
                + ", " + block.getZ() + (selection.complete()
                ? " &7(" + Numbers.whole(selection.volume()) + " blocks selected)" : ""));
    }
}
