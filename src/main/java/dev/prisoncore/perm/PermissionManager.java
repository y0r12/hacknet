package dev.prisoncore.perm;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.prestige.Reward;
import dev.prisoncore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Hands out permissions based on rank and prestige, so no separate permissions plugin is required.
 *
 * <p>A player automatically receives:
 * <ul>
 *   <li>{@code prisoncore.rank.<rank>} for their rank and every rank below it</li>
 *   <li>the {@code permissions} listed for those ranks in ranks.yml</li>
 *   <li>{@code prisoncore.mine.<mine>} for every mine their rank and prestige unlock</li>
 *   <li>the {@code permissions} of every prestige milestone they have reached</li>
 * </ul>
 */
public final class PermissionManager {

    private final PrisonCore plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    /** Works out what the player should have right now, without applying it. */
    public Map<String, Boolean> compute(PlayerData data) {
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        Rank current = plugin.ranks().of(data);
        for (Rank rank : plugin.ranks().all()) {
            if (rank.index() > current.index()) {
                break;
            }
            nodes.put("prisoncore.rank." + rank.name().toLowerCase(Locale.ROOT), true);
            for (String node : rank.permissions()) {
                add(nodes, node);
            }
        }
        for (Mine mine : plugin.mines().all()) {
            if (plugin.mines().meetsRequirements(data, mine)) {
                nodes.put(mine.permission(), true);
            }
        }
        for (Map.Entry<Long, Reward> milestone : plugin.prestige().milestones().headMap(data.prestige(), true)
                .entrySet()) {
            for (String node : milestone.getValue().permissions()) {
                add(nodes, node);
            }
        }
        return nodes;
    }

    /** A leading "-" takes a permission away instead of granting it. */
    private static void add(Map<String, Boolean> nodes, String node) {
        if (node == null || node.isBlank()) {
            return;
        }
        String trimmed = node.trim();
        if (trimmed.startsWith("-") && trimmed.length() > 1) {
            nodes.put(trimmed.substring(1), false);
        } else {
            nodes.put(trimmed, true);
        }
    }

    public void refresh(Player player) {
        remove(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        for (Map.Entry<String, Boolean> node : compute(plugin.data(player)).entrySet()) {
            attachment.setPermission(node.getKey(), node.getValue());
        }
        attachments.put(player.getUniqueId(), attachment);
        plugin.sell().invalidate(player);
        try {
            player.updateCommands();
        } catch (Throwable ignored) {
            // Only refreshes tab-completion; safe to skip where unsupported.
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void remove(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // Already gone (player logged out).
            }
        }
    }

    public void removeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
        }
        attachments.clear();
    }
}
