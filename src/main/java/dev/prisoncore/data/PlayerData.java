package dev.prisoncore.data;

import java.util.UUID;

/** Everything PrisonCore remembers about one player. Only touched from the main server thread. */
public final class PlayerData {

    private final UUID uuid;
    private String name;
    private String rank;
    private long prestige;
    private double balance;
    private long blocksMined;
    private double bonusMultiplier;
    private boolean autosell;
    private boolean scoreboard = true;
    private volatile boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    /** Stored rank name. May be null or stale; resolve it through the rank manager. */
    public String rank() {
        return rank;
    }

    public void rank(String rank) {
        this.rank = rank;
        dirty = true;
    }

    public long prestige() {
        return prestige;
    }

    public void prestige(long prestige) {
        this.prestige = Math.max(0, prestige);
        dirty = true;
    }

    public double balance() {
        return balance;
    }

    public void balance(double balance) {
        if (Double.isNaN(balance) || balance < 0) {
            balance = 0;
        }
        if (Double.isInfinite(balance)) {
            balance = Double.MAX_VALUE;
        }
        this.balance = balance;
        dirty = true;
    }

    public long blocksMined() {
        return blocksMined;
    }

    public void blocksMined(long blocksMined) {
        this.blocksMined = blocksMined;
        dirty = true;
    }

    /** Extra sell multiplier earned from rewards, added on top of the base 1x. */
    public double bonusMultiplier() {
        return bonusMultiplier;
    }

    public void bonusMultiplier(double bonusMultiplier) {
        this.bonusMultiplier = Math.max(0, bonusMultiplier);
        dirty = true;
    }

    public boolean autosell() {
        return autosell;
    }

    public void autosell(boolean autosell) {
        this.autosell = autosell;
        dirty = true;
    }

    public boolean scoreboard() {
        return scoreboard;
    }

    public void scoreboard(boolean scoreboard) {
        this.scoreboard = scoreboard;
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    void dirty(boolean dirty) {
        this.dirty = dirty;
    }

    Snapshot snapshot() {
        return new Snapshot(uuid, name, rank, prestige, balance, blocksMined, bonusMultiplier, autosell, scoreboard);
    }

    /** Immutable copy handed to the storage thread. */
    record Snapshot(UUID uuid, String name, String rank, long prestige, double balance, long blocksMined,
                    double bonusMultiplier, boolean autosell, boolean scoreboard) {
    }
}
