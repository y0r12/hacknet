package dev.prisoncore.sell;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Loads prices.yml, works out sell multipliers and sells items. */
public final class SellManager {

    /** Outcome of a sale. {@code items} is 0 when nothing could be sold. */
    public record SellResult(long items, double total, double multiplier) {
    }

    /** How a player's multiplier is made up. */
    public record Multiplier(double prestige, double bonus, double permission, double global) {
        public double total() {
            return (1 + prestige + bonus + permission) * global;
        }
    }

    private static final String MULTIPLIER_PERMISSION = "prisoncore.multiplier.";
    private static final long CACHE_MILLIS = 3000;

    private final PrisonCore plugin;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);
    private final Map<UUID, Double> permissionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> permissionCacheTime = new ConcurrentHashMap<>();
    private File file;
    private YamlConfiguration config;

    public SellManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "prices.yml");
        if (!file.exists()) {
            plugin.saveResource("prices.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        prices.clear();
        ConfigurationSection section = config.getConfigurationSection("prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("prices.yml: unknown item '" + key + "' - skipped.");
                    continue;
                }
                double price = section.getDouble(key, 0);
                if (price > 0) {
                    prices.put(material, price);
                }
            }
        }
        permissionCache.clear();
        permissionCacheTime.clear();
    }

    public Map<Material, Double> prices() {
        return Collections.unmodifiableMap(prices);
    }

    /** Base price of one item, or 0 if it cannot be sold. */
    public double price(Material material) {
        Double price = prices.get(material);
        return price == null ? 0 : price;
    }

    public void setPrice(Material material, double price) {
        config.set("prices." + material.name(), price > 0 ? price : null);
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save prices.yml", ex);
        }
        if (price > 0) {
            prices.put(material, price);
        } else {
            prices.remove(material);
        }
    }

    // ------------------------------------------------------------------ multipliers

    public Multiplier multiplier(Player player) {
        PlayerData data = plugin.data(player);
        double perPrestige = plugin.getConfig().getDouble("sell.multiplier-per-prestige", 0.05);
        double prestigeBonus = data.prestige() * perPrestige;
        double cap = plugin.getConfig().getDouble("sell.max-prestige-multiplier", 0);
        if (cap > 0) {
            prestigeBonus = Math.min(prestigeBonus, cap);
        }
        double global = Math.max(0, plugin.getConfig().getDouble("sell.global-multiplier", 1.0));
        return new Multiplier(prestigeBonus, data.bonusMultiplier(), permissionBonus(player), global);
    }

    /**
     * Bonus from prisoncore.multiplier.&lt;number&gt; permissions. The highest one counts, so
     * prisoncore.multiplier.2 doubles sales (+1.0) and prisoncore.multiplier.1.5 adds +0.5.
     */
    private double permissionBonus(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long cachedAt = permissionCacheTime.get(id);
        if (cachedAt != null && now - cachedAt < CACHE_MILLIS) {
            return permissionCache.getOrDefault(id, 0.0);
        }
        double best = 1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (node.length() > MULTIPLIER_PERMISSION.length()
                    && node.regionMatches(true, 0, MULTIPLIER_PERMISSION, 0, MULTIPLIER_PERMISSION.length())) {
                try {
                    double value = Double.parseDouble(node.substring(MULTIPLIER_PERMISSION.length()));
                    if (value > best && !Double.isInfinite(value)) {
                        best = value;
                    }
                } catch (NumberFormatException ignored) {
                    // Not a number (for example prisoncore.multiplier.*), ignore it.
                }
            }
        }
        permissionCache.put(id, best - 1);
        permissionCacheTime.put(id, now);
        return best - 1;
    }

    /** Forgets cached permission lookups for this player (after rank or permission changes). */
    public void invalidate(Player player) {
        permissionCache.remove(player.getUniqueId());
        permissionCacheTime.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ selling

    /** Plain items only: anything renamed, enchanted or otherwise customised is left alone. */
    private boolean sellable(ItemStack item) {
        return item != null && item.getAmount() > 0 && price(item.getType()) > 0 && Items.plain(item);
    }

    /** Sells everything sellable in the player's main inventory. */
    public SellResult sellInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        double base = 0;
        long items = 0;
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!sellable(item)) {
                continue;
            }
            base += price(item.getType()) * item.getAmount();
            items += item.getAmount();
            contents[slot] = null;
            changed = true;
        }
        double multiplier = multiplier(player).total();
        if (!changed) {
            return new SellResult(0, 0, multiplier);
        }
        inventory.setStorageContents(contents);
        double total = base * multiplier;
        plugin.economy().deposit(player.getUniqueId(), total);
        return new SellResult(items, total, multiplier);
    }

    /**
     * Autosell: sells the sellable stacks straight out of a list of block drops, removing them from the
     * collection. Whatever is left should still go to the player's inventory.
     */
    public SellResult sellDrops(Player player, Collection<ItemStack> drops) {
        double base = 0;
        long items = 0;
        for (Iterator<ItemStack> iterator = drops.iterator(); iterator.hasNext(); ) {
            ItemStack item = iterator.next();
            if (sellable(item)) {
                base += price(item.getType()) * item.getAmount();
                items += item.getAmount();
                iterator.remove();
            }
        }
        double multiplier = multiplier(player).total();
        if (items == 0) {
            return new SellResult(0, 0, multiplier);
        }
        double total = base * multiplier;
        plugin.economy().deposit(player.getUniqueId(), total);
        return new SellResult(items, total, multiplier);
    }
}
