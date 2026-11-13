package dev.prisoncore.economy;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import org.bukkit.Bukkit;

import java.util.UUID;

/** PrisonCore's built-in economy. Balances are stored with the rest of the player data. */
public final class InternalEconomy implements EconomyService {

    private final PrisonCore plugin;

    public InternalEconomy(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String providerName() {
        return "PrisonCore (built-in)";
    }

    @Override
    public boolean internal() {
        return true;
    }

    private PlayerData data(UUID player, boolean create) {
        PlayerData data = plugin.store().get(player);
        if (data == null && create) {
            String name = null;
            try {
                name = Bukkit.getOfflinePlayer(player).getName();
            } catch (Throwable ignored) {
                // Name lookups are best effort.
            }
            data = plugin.store().getOrCreate(player, name);
        }
        return data;
    }

    @Override
    public double balance(UUID player) {
        PlayerData data = data(player, false);
        return data == null ? 0 : data.balance();
    }

    @Override
    public void deposit(UUID player, double amount) {
        if (amount <= 0 || Double.isNaN(amount)) {
            return;
        }
        PlayerData data = data(player, true);
        data.balance(data.balance() + amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (Double.isNaN(amount) || amount < 0) {
            return false;
        }
        PlayerData data = data(player, false);
        if (data == null || data.balance() < amount) {
            return false;
        }
        data.balance(data.balance() - amount);
        return true;
    }

    @Override
    public void set(UUID player, double amount) {
        data(player, true).balance(amount);
    }
}
