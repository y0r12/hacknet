package dev.prisoncore.mine;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Owns every mine: storage in mines.yml, access rules, resets and the admin selection wand. */
public final class MineManager {

    /** Two corners picked by an admin with the wand or /prison mine pos1 and pos2. */
    public static final class Selection {
        private String world;
        private int[] first;
        private int[] second;

        public boolean complete() {
            return world != null && first != null && second != null;
        }

        public long volume() {
            if (!complete()) {
                return 0;
            }
            return (long) (Math.abs(first[0] - second[0]) + 1) * (Math.abs(first[1] - second[1]) + 1)
                    * (Math.abs(first[2] - second[2]) + 1);
        }
    }

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");

    private final PrisonCore plugin;
    private final Map<String, Mine> mines = new LinkedHashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final NamespacedKey wandKey;
    private final Deque<ResetJob> resetQueue = new ArrayDeque<>();
    private BukkitTask resetTask;
    private File file;

    public MineManager(PrisonCore plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "mine_wand");
    }

    // ------------------------------------------------------------------ storage

    public void load() {
        file = new File(plugin.getDataFolder(), "mines.yml");
        Map<String, Mine> previous = new HashMap<>(mines);
        mines.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("mines");
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) {
                continue;
            }
            int[] min = parsePoint(entry.getString("min"));
            int[] max = parsePoint(entry.getString("max"));
            String world = entry.getString("world");
            if (min == null || max == null || world == null || !VALID_NAME.matcher(name).matches()) {
                plugin.getLogger().warning("mines.yml: skipping mine '" + name + "' because its region is invalid.");
                continue;
            }
            Mine mine = new Mine(name, world, min[0], min[1], min[2], max[0], max[1], max[2]);
            mine.spawnString(entry.getString("spawn"));
            ConfigurationSection blocks = entry.getConfigurationSection("blocks");
            if (blocks != null) {
                readBlocks(blocks, mine.blocks(), "mine " + name);
            }
            mine.resetInterval(entry.getInt("reset-interval", -1));
            mine.resetThreshold(entry.getDouble("reset-threshold", -1));
            mine.requiredRank(entry.getString("required-rank"));
            mine.requiredPrestige(entry.getLong("required-prestige", 0));

            // Keep counters across /prison reload so mines do not lose track of what was dug out.
            Mine old = previous.get(name.toLowerCase(Locale.ROOT));
            if (old != null && !old.resetting()) {
                mine.mined(old.mined());
                mine.filled(old.filled());
                mine.secondsLeft(old.secondsLeft());
            } else {
                mine.secondsLeft(interval(mine));
            }
            mines.put(name.toLowerCase(Locale.ROOT), mine);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().setHeader(List.of(
                "Mines are created in-game with /prison mine ... and saved here automatically.",
                "You can edit this file by hand, then run /prison reload."));
        for (Mine mine : mines.values()) {
            String path = "mines." + mine.name() + ".";
            config.set(path + "world", mine.world());
            config.set(path + "min", mine.minX() + "," + mine.minY() + "," + mine.minZ());
            config.set(path + "max", mine.maxX() + "," + mine.maxY() + "," + mine.maxZ());
            config.set(path + "spawn", mine.spawnString());
            config.set(path + "required-rank", mine.requiredRank());
            config.set(path + "required-prestige", mine.requiredPrestige());
            config.set(path + "reset-interval", mine.resetInterval());
            config.set(path + "reset-threshold", mine.resetThreshold());
            for (Map.Entry<Material, Double> block : mine.blocks().entrySet()) {
                config.set(path + "blocks." + block.getKey().name(), block.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mines.yml", ex);
        }
    }

    private static int[] parsePoint(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void readBlocks(ConfigurationSection section, Map<Material, Double> into, String where) {
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            double weight = section.getDouble(key, 0);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Unknown block '" + key + "' in " + where + " - skipped.");
                continue;
            }
            if (weight > 0) {
                into.put(material, weight);
            }
        }
    }

    // ------------------------------------------------------------------ lookups

    public static boolean validName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    public Mine get(String name) {
        return name == null ? null : mines.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Mine> all() {
        return Collections.unmodifiableCollection(mines.values());
    }

    public Mine at(Block block) {
        if (mines.isEmpty()) {
            return null;
        }
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        for (Mine mine : mines.values()) {
            if (mine.contains(world, x, y, z)) {
                return mine;
            }
        }
        return null;
    }

    /** The mine a player is in, or standing on top of / right next to. */
    public Mine near(Location location) {
        for (Mine mine : mines.values()) {
            if (mine.near(location, 2)) {
                return mine;
            }
        }
        return null;
    }

    public int interval(Mine mine) {
        return mine.resetInterval() >= 0 ? mine.resetInterval()
                : plugin.getConfig().getInt("mines.default-reset-interval", 600);
    }

    /** Starts the mine's countdown again, for example after its timer was changed. */
    public void restartTimer(Mine mine) {
        mine.secondsLeft(interval(mine));
    }

    public double threshold(Mine mine) {
        return mine.resetThreshold() >= 0 ? mine.resetThreshold()
                : plugin.getConfig().getDouble("mines.default-reset-threshold", 70);
    }

    // ------------------------------------------------------------------ access

    /** True when the player's rank and prestige unlock this mine. Mines with no requirement are permission-only. */
    public boolean meetsRequirements(PlayerData data, Mine mine) {
        boolean hasRequirement = false;
        Rank required = plugin.ranks().get(mine.requiredRank());
        if (required != null) {
            hasRequirement = true;
            if (plugin.ranks().of(data).index() < required.index()) {
                return false;
            }
        }
        if (mine.requiredPrestige() > 0) {
            hasRequirement = true;
            if (data.prestige() < mine.requiredPrestige()) {
                return false;
            }
        }
        return hasRequirement;
    }

    public boolean canAccess(Player player, Mine mine) {
        if (player.hasPermission("prisoncore.mine.*") || player.hasPermission(mine.permission())) {
            return true;
        }
        return meetsRequirements(plugin.data(player), mine);
    }

    /** "Rank C", "Prestige 5", "Rank C + Prestige 5" or the permission node. */
    public String requirementText(Mine mine) {
        List<String> parts = new ArrayList<>(2);
        Rank required = plugin.ranks().get(mine.requiredRank());
        if (required != null) {
            parts.add("Rank " + required.name());
        }
        if (mine.requiredPrestige() > 0) {
            parts.add("Prestige " + Numbers.whole(mine.requiredPrestige()));
        }
        return parts.isEmpty() ? mine.permission() : String.join(" + ", parts);
    }

    /** The best mine the player can use: highest prestige requirement first, then highest rank. */
    public Mine best(Player player) {
        Mine best = null;
        long bestScore = Long.MIN_VALUE;
        for (Mine mine : mines.values()) {
            if (!canAccess(player, mine)) {
                continue;
            }
            Rank required = plugin.ranks().get(mine.requiredRank());
            long score = mine.requiredPrestige() * 10_000L + (required == null ? -1 : required.index());
            if (score > bestScore) {
                best = mine;
                bestScore = score;
            }
        }
        return best;
    }

    public boolean teleport(Player player, Mine mine) {
        Location spawn = mine.spawn();
        if (spawn == null) {
            return false;
        }
        player.teleport(spawn);
        return true;
    }

    // ------------------------------------------------------------------ editing

    public Mine create(String name, Selection selection, Location spawn) {
        Mine mine = new Mine(name, selection.world, selection.first[0], selection.first[1], selection.first[2],
                selection.second[0], selection.second[1], selection.second[2]);
        if (spawn != null && !mine.contains(spawn)) {
            mine.spawn(spawn);
        }
        Rank rank = plugin.ranks().get(name);
        if (rank != null) {
            mine.requiredRank(rank.name());
        }
        applyPreset(mine, rank == null ? null : rank.name());
        mine.secondsLeft(interval(mine));
        mines.put(name.toLowerCase(Locale.ROOT), mine);
        save();
        plugin.permissions().refreshAll();
        return mine;
    }

    /** Fills in the block mix from config.yml: the preset for this rank, or the default blocks. */
    public boolean applyPreset(Mine mine, String presetName) {
        ConfigurationSection presets = plugin.getConfig().getConfigurationSection("mines.presets");
        ConfigurationSection preset = null;
        if (presets != null && presetName != null) {
            for (String key : presets.getKeys(false)) {
                if (key.equalsIgnoreCase(presetName)) {
                    preset = presets.getConfigurationSection(key);
                    break;
                }
            }
        }
        boolean found = preset != null;
        if (preset == null) {
            preset = plugin.getConfig().getConfigurationSection("mines.default-blocks");
        }
        mine.blocks().clear();
        if (preset != null) {
            readBlocks(preset, mine.blocks(), "config.yml preset");
        }
        if (mine.blocks().isEmpty()) {
            mine.blocks().put(Material.STONE, 100.0);
        }
        return found;
    }

    public void redefine(Mine mine, Selection selection) {
        mine.region(selection.world, selection.first[0], selection.first[1], selection.first[2],
                selection.second[0], selection.second[1], selection.second[2]);
        mine.mined(0);
        mine.filled(false);
        save();
    }

    public void delete(Mine mine) {
        mines.remove(mine.name().toLowerCase(Locale.ROOT));
        save();
        plugin.permissions().refreshAll();
    }

    // ------------------------------------------------------------------ resets

    /** Called once per second. */
    public void tick() {
        for (Mine mine : mines.values()) {
            if (mine.resetting()) {
                continue;
            }
            int interval = interval(mine);
            if (interval <= 0) {
                continue;
            }
            mine.secondsLeft(mine.secondsLeft() - 1);
            if (mine.secondsLeft() <= 0) {
                mine.secondsLeft(interval);
                // A mine nobody has touched is still full; skip the work.
                if (mine.mined() > 0 || !mine.filled()) {
                    reset(mine);
                }
            }
        }
    }

    /** Counts a mined block and triggers a reset once enough of the mine is gone. */
    public void blockMined(Mine mine) {
        mine.mined(mine.mined() + 1);
        double threshold = threshold(mine);
        if (threshold <= 0 || mine.resetting() || mine.resetQueued()) {
            return;
        }
        if (mine.mined() * 100.0 / mine.volume() >= threshold) {
            mine.resetQueued(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                mine.resetQueued(false);
                if (mines.get(mine.name().toLowerCase(Locale.ROOT)) == mine) {
                    reset(mine);
                }
            });
        }
    }

    public void resetAll() {
        for (Mine mine : mines.values()) {
            reset(mine);
        }
    }

    /**
     * Refills the mine. The work goes into a queue that is processed a little every tick, so even a huge
     * mine - or every mine resetting at once - never freezes the server.
     *
     * @return false if the mine is already resetting, has no blocks, or its world is not loaded
     */
    public boolean reset(Mine mine) {
        if (mine.resetting() || mine.blocks().isEmpty()) {
            return false;
        }
        World world = Bukkit.getWorld(mine.world());
        if (world == null) {
            return false;
        }
        mine.resetting(true);
        evacuate(mine, world, false);
        resetQueue.add(new ResetJob(mine, world));
        if (resetTask == null) {
            resetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processResets, 0L, 1L);
        }
        return true;
    }

    /** Works through queued resets, one mine at a time, until this tick's time budget is used up. */
    private void processResets() {
        long budgetNanos = Math.max(1, plugin.getConfig().getInt("mines.reset-ms-per-tick", 15)) * 1_000_000L;
        long deadline = System.nanoTime() + budgetNanos;
        while (!resetQueue.isEmpty()) {
            ResetJob job = resetQueue.peek();
            if (!job.fill(deadline)) {
                return; // Out of time; carry on next tick.
            }
            resetQueue.poll();
            Mine mine = job.mine;
            mine.mined(0);
            mine.filled(true);
            mine.resetting(false);
            mine.secondsLeft(interval(mine));
            evacuate(mine, job.world, true);
        }
        if (resetTask != null) {
            resetTask.cancel();
            resetTask = null;
        }
    }

    /** One mine being refilled, block by block, from the top layer down. */
    private static final class ResetJob {
        private final Mine mine;
        private final World world;
        private final Material[] materials;
        private final double[] cumulative;
        private final double total;
        // The region is copied so an admin moving the mine mid-refill cannot send blocks astray.
        private final int minX;
        private final int minZ;
        private final int maxY;
        private final long volume;
        private final int sizeX;
        private final long layer;
        private long index;

        ResetJob(Mine mine, World world) {
            this.mine = mine;
            this.world = world;
            int count = mine.blocks().size();
            materials = new Material[count];
            cumulative = new double[count];
            double running = 0;
            int i = 0;
            for (Map.Entry<Material, Double> entry : mine.blocks().entrySet()) {
                running += entry.getValue();
                materials[i] = entry.getKey();
                cumulative[i] = running;
                i++;
            }
            total = running;
            minX = mine.minX();
            minZ = mine.minZ();
            maxY = mine.maxY();
            volume = mine.volume();
            sizeX = mine.sizeX();
            layer = (long) sizeX * mine.sizeZ();
        }

        /** Places blocks until done or the deadline passes. Returns true once the mine is full. */
        boolean fill(long deadline) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int sinceCheck = 0;
            while (index < volume) {
                int x = minX + (int) (index % sizeX);
                int z = minZ + (int) ((index % layer) / sizeX);
                int y = maxY - (int) (index / layer);
                double roll = random.nextDouble() * total;
                Material material = materials[materials.length - 1];
                for (int m = 0; m < materials.length; m++) {
                    if (roll < cumulative[m]) {
                        material = materials[m];
                        break;
                    }
                }
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != material) {
                    block.setType(material, false);
                }
                index++;
                if (++sinceCheck >= 256) {
                    sinceCheck = 0;
                    if (System.nanoTime() > deadline) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Moves players out of the mine so they are not buried, optionally announcing the reset to those nearby. */
    private void evacuate(Mine mine, World world, boolean announce) {
        Location spawn = mine.spawn();
        for (Player player : world.getPlayers()) {
            Location location = player.getLocation();
            if (spawn != null && mine.contains(location)) {
                player.teleport(spawn);
            }
            if (announce && mine.near(location, 16)) {
                plugin.messages().send(player, "mine-reset", "mine", mine.name());
            }
        }
    }

    // ------------------------------------------------------------------ selection wand

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color("&6&lMine Wand"));
            meta.setLore(Text.color(List.of("&7Left-click a block: &fcorner 1", "&7Right-click a block: &fcorner 2",
                    "&7Then: &f/prison mine create <name>")));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public Selection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
    }

    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    /** Records a corner. Picking a corner in a different world starts a fresh selection. */
    public Selection select(Player player, Block block, boolean firstCorner) {
        Selection selection = selection(player);
        String world = block.getWorld().getName();
        if (selection.world != null && !selection.world.equals(world)) {
            selection.first = null;
            selection.second = null;
        }
        selection.world = world;
        int[] point = {block.getX(), block.getY(), block.getZ()};
        if (firstCorner) {
            selection.first = point;
        } else {
            selection.second = point;
        }
        return selection;
    }
}
