package dev.prisoncore.command;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /balance, /pay, /baltop and the admin /eco command. */
public final class EconomyCommands implements TabExecutor {

    private static final int PAGE_SIZE = 10;

    private final PrisonCore plugin;

    public EconomyCommands(PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "baltop" -> balTop(sender, args);
            case "eco" -> eco(sender, label, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length > 0 && sender.hasPermission("prisoncore.balance.others")) {
            PlayerData target = plugin.findPlayer(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[0]);
                return;
            }
            plugin.messages().send(sender, "balance-other", "player", target.name(),
                    "balance", plugin.money(plugin.economy().balance(target.uuid())),
                    "balance_full", plugin.currencySymbol() + Numbers.full(plugin.economy().balance(target.uuid())));
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        double balance = plugin.economy().balance(player.getUniqueId());
        plugin.messages().send(sender, "balance-self", "balance", plugin.money(balance),
                "balance_full", plugin.currencySymbol() + Numbers.full(balance));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "pay-usage");
            return;
        }
        PlayerData target = plugin.findPlayer(args[0]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[0]);
            return;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            plugin.messages().send(sender, "pay-self");
            return;
        }
        double amount = Numbers.parse(args[1]);
        if (amount <= 0) {
            plugin.messages().send(sender, "invalid-number", "input", args[1]);
            return;
        }
        double minimum = plugin.getConfig().getDouble("economy.minimum-pay", 1);
        if (amount < minimum) {
            plugin.messages().send(sender, "pay-minimum", "amount", plugin.money(minimum));
            return;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), amount)) {
            plugin.messages().send(sender, "pay-not-enough");
            return;
        }
        plugin.economy().deposit(target.uuid(), amount);
        plugin.messages().send(sender, "pay-sent", "player", target.name(), "amount", plugin.money(amount));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            plugin.messages().send(online, "pay-received", "player", player.getName(), "amount", plugin.money(amount));
            plugin.sidebar().update(online);
        }
        plugin.sidebar().update(player);
    }

    private void balTop(CommandSender sender, String[] args) {
        if (!plugin.economy().internal()) {
            plugin.messages().send(sender, "baltop-external");
            return;
        }
        List<PlayerData> sorted = new ArrayList<>(plugin.store().all());
        sorted.removeIf(data -> data.balance() <= 0);
        sorted.sort(Comparator.comparingDouble((PlayerData data) -> data.balance()).reversed());
        int pages = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        long requested = args.length > 0 ? Numbers.parseLong(args[0]) : 1;
        int shown = (int) Math.max(1, Math.min(requested, pages));
        plugin.messages().send(sender, "baltop-header", "page", shown, "pages", pages);
        if (sorted.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
        }
        for (int i = (shown - 1) * PAGE_SIZE; i < Math.min(sorted.size(), shown * PAGE_SIZE); i++) {
            PlayerData data = sorted.get(i);
            plugin.messages().send(sender, "baltop-line", "position", i + 1, "player", data.name(),
                    "balance", plugin.money(data.balance()));
        }
    }

    private void eco(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            plugin.tell(sender, "&cUsage: /" + label + " <give|take|set> <player> <amount>");
            return;
        }
        PlayerData target = plugin.findPlayer(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        double amount = Numbers.parse(args[2]);
        if (amount < 0) {
            plugin.messages().send(sender, "invalid-number", "input", args[2]);
            return;
        }
        UUID id = target.uuid();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give", "add" -> plugin.economy().deposit(id, amount);
            case "take", "remove" -> plugin.economy().set(id, Math.max(0, plugin.economy().balance(id) - amount));
            case "set" -> plugin.economy().set(id, amount);
            default -> {
                plugin.tell(sender, "&cUsage: /" + label + " <give|take|set> <player> <amount>");
                return;
            }
        }
        plugin.tell(sender, "&a" + target.name() + "'s balance is now &6"
                + plugin.money(plugin.economy().balance(id)) + "&a.");
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            plugin.sidebar().update(online);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eco")) {
            if (args.length == 1) {
                return Completions.filter(args[0], List.of("give", "take", "set"));
            }
            if (args.length == 2) {
                return Completions.players(args[1]);
            }
            return List.of();
        }
        if ((name.equals("pay") || name.equals("balance")) && args.length == 1) {
            return Completions.players(args[0]);
        }
        return List.of();
    }
}
