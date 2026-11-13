package dev.prisoncore.economy;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.util.Numbers;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Everything that touches Vault classes lives in this file, so the plugin still loads when Vault is absent.
 * Only call these methods after checking that the Vault plugin is installed.
 */
public final class VaultHook {

    private VaultHook() {
    }

    /** Exposes the built-in economy to other plugins (shops, crates, ...) through Vault. */
    public static void registerProvider(PrisonCore plugin, InternalEconomy economy) {
        Bukkit.getServicesManager().register(Economy.class, new Provider(plugin, economy), plugin,
                ServicePriority.Highest);
    }

    public static void unregisterProvider(PrisonCore plugin) {
        Bukkit.getServicesManager().unregisterAll(plugin);
    }

    /** Returns another plugin's economy, or null when PrisonCore is the only one. */
    public static EconomyService external(PrisonCore plugin) {
        for (RegisteredServiceProvider<Economy> registration
                : Bukkit.getServicesManager().getRegistrations(Economy.class)) {
            if (registration.getPlugin() != plugin) {
                return new External(registration.getProvider(), registration.getPlugin().getName());
            }
        }
        return null;
    }

    /** Name of another plugin that also provides an economy, or null. */
    public static String otherProvider(PrisonCore plugin) {
        for (RegisteredServiceProvider<Economy> registration
                : Bukkit.getServicesManager().getRegistrations(Economy.class)) {
            if (registration.getPlugin() != plugin) {
                return registration.getPlugin().getName();
            }
        }
        return null;
    }

    /** Uses another plugin's economy for all prison money. */
    private static final class External implements EconomyService {

        private final Economy economy;
        private final String owner;

        External(Economy economy, String owner) {
            this.economy = economy;
            this.owner = owner;
        }

        @Override
        public String providerName() {
            return owner + " (via Vault)";
        }

        @Override
        public boolean internal() {
            return false;
        }

        @Override
        public double balance(UUID player) {
            return economy.getBalance(Bukkit.getOfflinePlayer(player));
        }

        @Override
        public void deposit(UUID player, double amount) {
            if (amount > 0) {
                economy.depositPlayer(Bukkit.getOfflinePlayer(player), amount);
            }
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
            if (amount < 0 || economy.getBalance(offline) < amount) {
                return false;
            }
            return amount == 0 || economy.withdrawPlayer(offline, amount).transactionSuccess();
        }

        @Override
        public void set(UUID player, double amount) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
            double current = economy.getBalance(offline);
            if (amount > current) {
                economy.depositPlayer(offline, amount - current);
            } else if (amount < current) {
                economy.withdrawPlayer(offline, current - amount);
            }
        }
    }

    /** The built-in economy as seen by other plugins. */
    private static final class Provider implements Economy {

        private final PrisonCore plugin;
        private final InternalEconomy economy;

        Provider(PrisonCore plugin, InternalEconomy economy) {
            this.plugin = plugin;
            this.economy = economy;
        }

        private UUID id(String name) {
            PlayerData data = plugin.store().find(name);
            if (data != null) {
                return data.uuid();
            }
            return Bukkit.getOfflinePlayer(name).getUniqueId();
        }

        private EconomyResponse ok(double amount, UUID player) {
            return new EconomyResponse(amount, economy.balance(player), EconomyResponse.ResponseType.SUCCESS, null);
        }

        private EconomyResponse fail(UUID player, String reason) {
            return new EconomyResponse(0, economy.balance(player), EconomyResponse.ResponseType.FAILURE, reason);
        }

        private EconomyResponse noBanks() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                    "PrisonCore does not support bank accounts");
        }

        private EconomyResponse withdraw(UUID player, double amount) {
            if (amount < 0) {
                return fail(player, "Cannot withdraw a negative amount");
            }
            if (!economy.withdraw(player, amount)) {
                return fail(player, "Insufficient funds");
            }
            return ok(amount, player);
        }

        private EconomyResponse deposit(UUID player, double amount) {
            if (amount < 0) {
                return fail(player, "Cannot deposit a negative amount");
            }
            economy.deposit(player, amount);
            return ok(amount, player);
        }

        @Override
        public boolean isEnabled() {
            return plugin.isEnabled();
        }

        @Override
        public String getName() {
            return "PrisonCore";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 2;
        }

        @Override
        public String format(double amount) {
            return plugin.currencySymbol() + Numbers.full(amount);
        }

        @Override
        public String currencyNamePlural() {
            return "dollars";
        }

        @Override
        public String currencyNameSingular() {
            return "dollar";
        }

        @Override
        public boolean hasAccount(String playerName) {
            return plugin.store().find(playerName) != null;
        }

        @Override
        public boolean hasAccount(OfflinePlayer player) {
            return plugin.store().get(player.getUniqueId()) != null;
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return hasAccount(playerName);
        }

        @Override
        public boolean hasAccount(OfflinePlayer player, String worldName) {
            return hasAccount(player);
        }

        @Override
        public double getBalance(String playerName) {
            return economy.balance(id(playerName));
        }

        @Override
        public double getBalance(OfflinePlayer player) {
            return economy.balance(player.getUniqueId());
        }

        @Override
        public double getBalance(String playerName, String world) {
            return getBalance(playerName);
        }

        @Override
        public double getBalance(OfflinePlayer player, String world) {
            return getBalance(player);
        }

        @Override
        public boolean has(String playerName, double amount) {
            return getBalance(playerName) >= amount;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return getBalance(player) >= amount;
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return has(playerName, amount);
        }

        @Override
        public boolean has(OfflinePlayer player, String worldName, double amount) {
            return has(player, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            return withdraw(id(playerName), amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            return withdraw(player.getUniqueId(), amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return withdrawPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
            return withdrawPlayer(player, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            return deposit(id(playerName), amount);
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            return deposit(player.getUniqueId(), amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return depositPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
            return depositPlayer(player, amount);
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return noBanks();
        }

        @Override
        public EconomyResponse createBank(String name, OfflinePlayer player) {
            return noBanks();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return noBanks();
        }

        @Override
        public EconomyResponse isBankMember(String name, OfflinePlayer player) {
            return noBanks();
        }

        @Override
        public List<String> getBanks() {
            return Collections.emptyList();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            plugin.store().getOrCreate(id(playerName), playerName);
            return true;
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player) {
            plugin.store().getOrCreate(player.getUniqueId(), player.getName());
            return true;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return createPlayerAccount(playerName);
        }

        @Override
        public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
            return createPlayerAccount(player);
        }
    }
}
