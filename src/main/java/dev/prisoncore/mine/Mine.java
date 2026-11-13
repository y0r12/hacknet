package dev.prisoncore.mine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** A cuboid region that refills itself with a weighted mix of blocks. */
public final class Mine {

    private final String name;
    private String world;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    private String spawnWorld;
    private double spawnX;
    private double spawnY;
    private double spawnZ;
    private float spawnYaw;
    private float spawnPitch;

    private final Map<Material, Double> blocks = new LinkedHashMap<>();
    /** Seconds between timed resets. -1 = use the default from config.yml, 0 = never. */
    private int resetInterval = -1;
    /** Reset once this percentage has been mined. -1 = use the default, 0 = never. */
    private double resetThreshold = -1;
    private String requiredRank;
    private long requiredPrestige;

    // Runtime state, not saved.
    private long mined;
    private boolean resetting;
    private boolean resetQueued;
    private boolean filled;
    private int secondsLeft;

    public Mine(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        region(world, x1, y1, z1, x2, y2, z2);
    }

    public void region(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String name() {
        return name;
    }

    /** Permission node that lets a player use this mine regardless of rank. */
    public String permission() {
        return "prisoncore.mine." + name.toLowerCase(Locale.ROOT);
    }

    public String world() {
        return world;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ
                && world.equals(worldName);
    }

    public boolean contains(Block block) {
        return contains(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public boolean contains(Location location) {
        return location.getWorld() != null && contains(location.getWorld().getName(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }

    /** True when the location is inside the mine or within {@code margin} blocks of it. */
    public boolean near(Location location, int margin) {
        return location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getBlockX() >= minX - margin && location.getBlockX() <= maxX + margin
                && location.getBlockY() >= minY - margin && location.getBlockY() <= maxY + margin
                && location.getBlockZ() >= minZ - margin && location.getBlockZ() <= maxZ + margin;
    }

    public void spawn(Location location) {
        if (location == null || location.getWorld() == null) {
            spawnWorld = null;
            return;
        }
        spawnWorld = location.getWorld().getName();
        spawnX = location.getX();
        spawnY = location.getY();
        spawnZ = location.getZ();
        spawnYaw = location.getYaw();
        spawnPitch = location.getPitch();
    }

    public boolean hasSpawn() {
        return spawnWorld != null;
    }

    /** Where players arrive: the set spawn, or the centre of the mine's top surface. Null if the world is unloaded. */
    public Location spawn() {
        if (spawnWorld != null) {
            World target = Bukkit.getWorld(spawnWorld);
            if (target != null) {
                Location custom = new Location(target, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
                // A spawn inside the mine would bury players on every reset, so it is ignored.
                if (!contains(custom)) {
                    return custom;
                }
            }
        }
        World target = Bukkit.getWorld(world);
        if (target == null) {
            return null;
        }
        return new Location(target, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    String spawnString() {
        if (spawnWorld == null) {
            return null;
        }
        return spawnWorld + "," + spawnX + "," + spawnY + "," + spawnZ + "," + spawnYaw + "," + spawnPitch;
    }

    void spawnString(String value) {
        spawnWorld = null;
        if (value == null) {
            return;
        }
        String[] parts = value.split(",");
        if (parts.length < 4) {
            return;
        }
        try {
            spawnX = Double.parseDouble(parts[1]);
            spawnY = Double.parseDouble(parts[2]);
            spawnZ = Double.parseDouble(parts[3]);
            spawnYaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
            spawnPitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0;
            spawnWorld = parts[0];
        } catch (NumberFormatException ignored) {
            // Leave the spawn unset; the mine falls back to its top surface.
        }
    }

    /** Block mix as material -> weight. Weights are relative; they do not have to add up to 100. */
    public Map<Material, Double> blocks() {
        return blocks;
    }

    public double totalWeight() {
        double total = 0;
        for (double weight : blocks.values()) {
            total += weight;
        }
        return total;
    }

    /** The most common block, used as the mine's icon. */
    public Material primaryBlock() {
        Material best = Material.STONE;
        double bestWeight = -1;
        for (Map.Entry<Material, Double> entry : blocks.entrySet()) {
            if (entry.getValue() > bestWeight) {
                best = entry.getKey();
                bestWeight = entry.getValue();
            }
        }
        return best;
    }

    public int resetInterval() {
        return resetInterval;
    }

    public void resetInterval(int resetInterval) {
        this.resetInterval = resetInterval;
    }

    public double resetThreshold() {
        return resetThreshold;
    }

    public void resetThreshold(double resetThreshold) {
        this.resetThreshold = resetThreshold;
    }

    public String requiredRank() {
        return requiredRank;
    }

    public void requiredRank(String requiredRank) {
        this.requiredRank = requiredRank;
    }

    public long requiredPrestige() {
        return requiredPrestige;
    }

    public void requiredPrestige(long requiredPrestige) {
        this.requiredPrestige = Math.max(0, requiredPrestige);
    }

    public long mined() {
        return mined;
    }

    void mined(long mined) {
        this.mined = mined;
    }

    /** Percentage of the mine that is still there, 0-100. */
    public double remainingPercent() {
        long volume = volume();
        if (volume <= 0) {
            return 0;
        }
        return Math.max(0, 100.0 - mined * 100.0 / volume);
    }

    public boolean resetting() {
        return resetting;
    }

    void resetting(boolean resetting) {
        this.resetting = resetting;
    }

    boolean resetQueued() {
        return resetQueued;
    }

    void resetQueued(boolean resetQueued) {
        this.resetQueued = resetQueued;
    }

    boolean filled() {
        return filled;
    }

    void filled(boolean filled) {
        this.filled = filled;
    }

    /** Seconds until the next timed reset. */
    public int secondsLeft() {
        return secondsLeft;
    }

    void secondsLeft(int secondsLeft) {
        this.secondsLeft = secondsLeft;
    }
}
