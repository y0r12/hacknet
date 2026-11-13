package dev.prisoncore.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Reads simple item descriptions from config files. */
public final class Items {

    private static final Map<Material, ItemStack> PRISTINE = new EnumMap<>(Material.class);

    private Items() {
    }

    /**
     * True for an item exactly as it comes out of the ground: no custom name, lore, enchantments, damage
     * or other special data. Only plain items are ever sold or smelted automatically.
     */
    public static boolean plain(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemStack pristine = PRISTINE.computeIfAbsent(item.getType(), ItemStack::new);
        return item.isSimilar(pristine);
    }

    /**
     * Parses {@code MATERIAL [amount] [enchantment:level ...] [unbreakable]}, for example
     * {@code DIAMOND_PICKAXE efficiency:5 unbreaking:3 unbreakable} or {@code BREAD 16}.
     *
     * @return the item, or null if the material is unknown
     */
    public static ItemStack parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] parts = spec.trim().split("\\s+");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            if (part.equals("unbreakable")) {
                if (meta != null) {
                    meta.setUnbreakable(true);
                }
                continue;
            }
            int colon = part.indexOf(':');
            try {
                if (colon > 0) {
                    Enchantment enchantment = enchantment(part.substring(0, colon));
                    int level = Integer.parseInt(part.substring(colon + 1));
                    if (enchantment != null && meta != null && level > 0) {
                        meta.addEnchant(enchantment, level, true);
                    }
                } else {
                    item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), Integer.parseInt(part))));
                }
            } catch (RuntimeException ignored) {
                // Skip anything we do not understand rather than failing the whole item.
            }
        }
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Looks an enchantment up by its Minecraft name, e.g. "efficiency". Null if there is no such enchantment. */
    @SuppressWarnings("deprecation")
    private static Enchantment enchantment(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name);
        try {
            return Registry.ENCHANTMENT.get(key);
        } catch (Throwable registryUnavailable) {
            // Fall through to the older lookup below.
        }
        try {
            return Enchantment.getByKey(key);
        } catch (Throwable unavailable) {
            return null;
        }
    }
}
