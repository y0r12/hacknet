package dev.prisoncore;

import dev.prisoncore.command.AdminCommand;
import dev.prisoncore.command.EconomyCommands;
import dev.prisoncore.command.PlayerCommands;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.data.PlayerStore;
import dev.prisoncore.economy.EconomyService;
import dev.prisoncore.economy.InternalEconomy;
import dev.prisoncore.economy.VaultHook;
import dev.prisoncore.listener.MineListener;
import dev.prisoncore.listener.PlayerListener;
import dev.prisoncore.menu.Menus;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.mine.MineManager;
import dev.prisoncore.perm.PermissionManager;
import dev.prisoncore.placeholder.PapiHook;
import dev.prisoncore.placeholder.Placeholders;
import dev.prisoncore.prestige.PrestigeManager;
import dev.prisoncore.rank.RankManager;
import dev.prisoncore.scoreboard.SidebarManager;
import dev.prisoncore.sell.SellManager;
import dev.prisoncore.util.Items;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Level;

/** PrisonCore - an all-in-one prison server plugin. */
public class PrisonCore extends JavaPlugin {

    private Messages messages;
    private PlayerStore store;
    private InternalEconomy internalEconomy;
    private volatile EconomyService economy;
    private RankManager ranks;
    private PrestigeManager prestige;
    private MineManager mines;
    private SellManager sell;
    private PermissionManager permissions;
    private Placeholders placeholders;
    private SidebarManager sidebar;
    private Menus menus;
    private MineListener mineListener;
    private boolean vaultPresent;
    private boolean papiPresent;
    private String currencySymbol = "$";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        store = new PlayerStore(this);
        ranks = new RankManager(this);
        prestige = new PrestigeManager(this);
        mines = new MineManager(this);
        sell = new SellManager(this);
        permissions = new PermissionManager(this);
        placeholders = new Placeholders(this);
        sidebar = new SidebarManager(this);
        menus = new Menus(this);

        try {
            store.open();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Could not open players.db - PrisonCore cannot start.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        internalEconomy = new InternalEconomy(this);
        economy = internalEconomy;

        currencySymbol = getConfig().getString("economy.currency-symbol", "$");
        messages.load();
        ranks.load();
        prestige.load();
        sell.load();
        mines.load();

        setupEconomy();
        papiPresent = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiPresent) {
            try {
                PapiHook.register(this);
                getLogger().info("Hooked into PlaceholderAPI (%prisoncore_rank%, %prisoncore_prestige%, ...).");
            } catch (Throwable ex) {
                papiPresent = false;
                getLogger().warning("Could not hook into PlaceholderAPI: " + ex);
            }
        }

        mineListener = new MineListener(this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(mineListener, this);

        PlayerCommands playerCommands = new PlayerCommands(this);
        for (String name : new String[]{"rankup", "rankupmax", "ranks", "prestige", "prestigemax", "prestiges",
                "prestigetop", "mine", "mines", "sellall", "autosell", "multiplier", "sb"}) {
            register(name, playerCommands);
        }
        EconomyCommands economyCommands = new EconomyCommands(this);
        for (String name : new String[]{"balance", "pay", "baltop", "eco"}) {
            register(name, economyCommands);
        }
        register("prison", new AdminCommand(this));

        // Players already online (after /reload or a plugin manager load).
        for (Player player : Bukkit.getOnlinePlayers()) {
            store.getOrCreate(player.getUniqueId(), player.getName());
            permissions.refresh(player);
        }

        long autosave = Math.max(10, getConfig().getLong("storage.autosave-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> store.saveDirty(true), autosave, autosave);
        // The mine timer is scheduled before the scoreboard so countdowns on the board are always current.
        Bukkit.getScheduler().runTaskTimer(this, mines::tick, 20L, 20L);
        sidebar.load();
        if (getConfig().getBoolean("mines.reset-on-startup", true)) {
            Bukkit.getScheduler().runTaskLater(this, mines::resetAll, 40L);
        }

        getLogger().info("Loaded " + ranks.all().size() + " ranks, " + mines.all().size() + " mines, "
                + sell.prices().size() + " sell prices and " + store.size() + " players. "
                + "Run /prison setup in-game for the setup checklist.");
    }

    @Override
    public void onDisable() {
        if (sidebar != null) {
            sidebar.shutdown();
        }
        if (permissions != null) {
            permissions.removeAll();
        }
        if (store != null) {
            store.close();
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * Decides where money lives. "internal" (default) uses PrisonCore's own economy and shares it with
     * other plugins through Vault. "vault" uses another economy plugin, such as EssentialsX.
     */
    private void setupEconomy() {
        vaultPresent = getServer().getPluginManager().getPlugin("Vault") != null;
        String mode = getConfig().getString("economy.provider", "internal").toLowerCase(Locale.ROOT);
        boolean wantExternal = mode.equals("vault") || mode.equals("external");
        if (!vaultPresent) {
            if (wantExternal) {
                getLogger().warning("economy.provider is 'vault' but Vault is not installed. "
                        + "Using the built-in economy instead.");
            }
            return;
        }
        try {
            if (!wantExternal) {
                VaultHook.registerProvider(this, internalEconomy);
                getLogger().info("Vault found: other plugins can now use the PrisonCore economy.");
            }
            // Other economy plugins may register after us, so settle the final choice once the server is up.
            Bukkit.getScheduler().runTask(this, () -> finishEconomySetup(wantExternal));
        } catch (Throwable ex) {
            getLogger().warning("Could not hook into Vault: " + ex);
        }
    }

    private void finishEconomySetup(boolean wantExternal) {
        try {
            if (wantExternal) {
                EconomyService external = VaultHook.external(this);
                if (external != null) {
                    economy = external;
                    getLogger().info("Using the economy from " + external.providerName() + ".");
                } else {
                    getLogger().severe("economy.provider is 'vault' but no other economy plugin was found. "
                            + "Using the built-in economy instead.");
                    VaultHook.registerProvider(this, internalEconomy);
                }
            } else {
                String other = VaultHook.otherProvider(this);
                if (other != null) {
                    getLogger().warning("Another economy plugin (" + other + ") is also installed, so players may "
                            + "see two different balances. Either set economy.provider to 'vault' in PrisonCore's "
                            + "config.yml to use " + other + "'s money, or turn off " + other + "'s economy.");
                }
            }
        } catch (Throwable ex) {
            getLogger().warning("Could not finish the Vault economy setup: " + ex);
        }
    }

    /** Re-reads every config file. */
    public void reload() {
        reloadConfig();
        currencySymbol = getConfig().getString("economy.currency-symbol", "$");
        messages.load();
        ranks.load();
        prestige.load();
        sell.load();
        mines.load();
        permissions.refreshAll();
        sidebar.load();
    }

    // ------------------------------------------------------------------ player lifecycle

    public void handleJoin(Player player) {
        // New to PrisonCore, or new to the server (another plugin may have opened their account first).
        boolean firstJoin = store.get(player.getUniqueId()) == null || !player.hasPlayedBefore();
        store.getOrCreate(player.getUniqueId(), player.getName());
        permissions.refresh(player);
        sidebar.show(player);
        if (firstJoin) {
            giveStarterKit(player);
        }
    }

    /** Hands out the first-join items and runs the first-join commands from config.yml. */
    private void giveStarterKit(Player player) {
        for (String spec : getConfig().getStringList("first-join.items")) {
            ItemStack item = Items.parse(spec);
            if (item == null) {
                getLogger().warning("config.yml: first-join item '" + spec + "' is not a valid item.");
                continue;
            }
            for (ItemStack leftover : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        for (String command : getConfig().getStringList("first-join.commands")) {
            runConsole(command, player);
        }
    }

    public void handleQuit(Player player) {
        permissions.remove(player);
        sidebar.forget(player);
        mines.clearSelection(player);
        sell.invalidate(player);
        if (mineListener != null) {
            mineListener.forget(player);
        }
    }

    // ------------------------------------------------------------------ helpers used across the plugin

    /** The stored data of an online player. */
    public PlayerData data(Player player) {
        return store.getOrCreate(player.getUniqueId(), player.getName());
    }

    /** Finds a player by name: online players first, then anyone who has joined before. Null if unknown. */
    public PlayerData findPlayer(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return data(online);
        }
        return store.find(name);
    }

    /** Money as short text with the currency symbol, e.g. $1.5M. */
    public String money(double amount) {
        return currencySymbol + Numbers.format(amount);
    }

    public String currencySymbol() {
        return currencySymbol;
    }

    /** Sends an admin/info line with the plugin prefix. */
    public void tell(CommandSender to, String message) {
        to.sendMessage(messages.prefix() + Text.color(message));
    }

    /** Runs a console command, replacing %player% and any extra name/value pairs. */
    public void runConsole(String command, Player player, String... replacements) {
        String line = command.replace("%player%", player.getName());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            line = line.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isBlank()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        } catch (Exception ex) {
            getLogger().warning("Reward command failed: '" + line + "': " + ex);
        }
    }

    public void teleportToMine(Player player, Mine mine) {
        if (mines.teleport(player, mine)) {
            messages.send(player, "mine-teleport", "mine", mine.name());
        } else {
            messages.send(player, "mine-not-found", "mine", mine.name());
        }
    }

    // ------------------------------------------------------------------ accessors

    public Messages messages() {
        return messages;
    }

    public PlayerStore store() {
        return store;
    }

    public EconomyService economy() {
        return economy;
    }

    public RankManager ranks() {
        return ranks;
    }

    public PrestigeManager prestige() {
        return prestige;
    }

    public MineManager mines() {
        return mines;
    }

    public SellManager sell() {
        return sell;
    }

    public PermissionManager permissions() {
        return permissions;
    }

    public Placeholders placeholders() {
        return placeholders;
    }

    public SidebarManager sidebar() {
        return sidebar;
    }

    public Menus menus() {
        return menus;
    }

    public boolean vaultPresent() {
        return vaultPresent;
    }

    public boolean papiPresent() {
        return papiPresent;
    }
}
