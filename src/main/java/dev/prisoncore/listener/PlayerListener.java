package dev.prisoncore.listener;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.menu.Menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/** Joins, quits, chat formatting and menu clicks. */
public final class PlayerListener implements Listener {

    private static final String NAME_TOKEN = "\u0001NAME\u0001";
    private static final String MESSAGE_TOKEN = "\u0001MESSAGE\u0001";

    private final PrisonCore plugin;

    public PlayerListener(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        plugin.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }
        String format = plugin.getConfig().getString("chat.format",
                "%prestige_prefix%%rank_prefix% &f%player%&7: &f%message%");
        // Keep the name and message as Bukkit's own format arguments so other plugins still see them.
        String prepared = format.replace("%player%", NAME_TOKEN).replace("%message%", MESSAGE_TOKEN);
        String formatted = plugin.placeholders().apply(event.getPlayer(), prepared)
                .replace("%", "%%")
                .replace(NAME_TOKEN, "%1$s")
                .replace(MESSAGE_TOKEN, "%2$s");
        if (!formatted.contains("%2$s")) {
            formatted = formatted + " %2$s";
        }
        try {
            event.setFormat(formatted);
        } catch (RuntimeException badFormat) {
            // Leave the default chat format in place if the configured one is invalid.
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == top && event.getWhoClicked() instanceof Player player) {
            menu.click(player, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
