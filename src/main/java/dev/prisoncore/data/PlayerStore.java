package dev.prisoncore.data;

import dev.prisoncore.PrisonCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Player storage backed by a single SQLite file (players.db).
 *
 * <p>All records are loaded into memory on startup so every read is instant and offline players can be
 * looked up, paid and ranked on leaderboards. Changed records are written back in the background.
 */
public final class PlayerStore {

    private final PrisonCore plugin;
    private final Map<UUID, PlayerData> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private Connection connection;
    private ExecutorService writer;

    public PlayerStore(PrisonCore plugin) {
        this.plugin = plugin;
    }

    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("The SQLite driver is missing from this server.", ex);
        }
        File file = new File(plugin.getDataFolder(), "players.db");
        plugin.getDataFolder().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY, name TEXT NOT NULL, rank TEXT, prestige INTEGER NOT NULL DEFAULT 0, "
                    + "balance REAL NOT NULL DEFAULT 0, blocks INTEGER NOT NULL DEFAULT 0, "
                    + "bonus REAL NOT NULL DEFAULT 0, autosell INTEGER NOT NULL DEFAULT 0, "
                    + "scoreboard INTEGER NOT NULL DEFAULT 1)");
            try (ResultSet rs = statement.executeQuery(
                    "SELECT uuid, name, rank, prestige, balance, blocks, bonus, autosell, scoreboard FROM players")) {
                while (rs.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rs.getString(1));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    PlayerData data = new PlayerData(uuid, rs.getString(2));
                    data.rank(rs.getString(3));
                    data.prestige(rs.getLong(4));
                    data.balance(rs.getDouble(5));
                    data.blocksMined(rs.getLong(6));
                    data.bonusMultiplier(rs.getDouble(7));
                    data.autosell(rs.getInt(8) != 0);
                    data.scoreboard(rs.getInt(9) != 0);
                    data.dirty(false);
                    byId.put(uuid, data);
                    if (data.name() != null) {
                        byName.put(data.name().toLowerCase(Locale.ROOT), uuid);
                    }
                }
            }
        }
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "PrisonCore-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public PlayerData get(UUID uuid) {
        return byId.get(uuid);
    }

    /** Returns the record for this player, creating a fresh one with the starting balance if needed. */
    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData data = byId.get(uuid);
        if (data == null) {
            data = new PlayerData(uuid, name == null ? uuid.toString().substring(0, 8) : name);
            data.balance(plugin.getConfig().getDouble("economy.starting-balance", 0));
            data.dirty(true);
            byId.put(uuid, data);
            byName.put(data.name().toLowerCase(Locale.ROOT), uuid);
        } else if (name != null && !name.equals(data.name())) {
            if (data.name() != null) {
                byName.remove(data.name().toLowerCase(Locale.ROOT), uuid);
            }
            data.name(name);
            byName.put(name.toLowerCase(Locale.ROOT), uuid);
        }
        return data;
    }

    /** Finds a player who has joined before by name (case-insensitive). */
    public PlayerData find(String name) {
        if (name == null) {
            return null;
        }
        UUID uuid = byName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : byId.get(uuid);
    }

    public Collection<PlayerData> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    /** Writes every changed record. Call from the main thread; the disk work happens off-thread when async. */
    public void saveDirty(boolean async) {
        List<PlayerData.Snapshot> snapshots = new ArrayList<>();
        for (PlayerData data : byId.values()) {
            if (data.dirty()) {
                data.dirty(false);
                snapshots.add(data.snapshot());
            }
        }
        if (snapshots.isEmpty() || connection == null) {
            return;
        }
        if (async && writer != null && !writer.isShutdown()) {
            writer.execute(() -> write(snapshots));
        } else {
            write(snapshots);
        }
    }

    private void write(List<PlayerData.Snapshot> snapshots) {
        synchronized (lock) {
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "REPLACE INTO players (uuid, name, rank, prestige, balance, blocks, bonus, autosell, scoreboard) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (PlayerData.Snapshot s : snapshots) {
                        ps.setString(1, s.uuid().toString());
                        ps.setString(2, s.name());
                        ps.setString(3, s.rank());
                        ps.setLong(4, s.prestige());
                        ps.setDouble(5, s.balance());
                        ps.setLong(6, s.blocksMined());
                        ps.setDouble(7, s.bonusMultiplier());
                        ps.setInt(8, s.autosell() ? 1 : 0);
                        ps.setInt(9, s.scoreboard() ? 1 : 0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player data", ex);
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // Nothing more we can do here.
                }
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // Connection is closing.
                }
            }
        }
    }

    public void close() {
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        saveDirty(false);
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // Shutting down anyway.
                }
                connection = null;
            }
        }
    }
}
