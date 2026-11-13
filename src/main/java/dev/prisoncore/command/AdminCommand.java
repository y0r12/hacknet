package dev.prisoncore.command;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import dev.prisoncore.mine.MineManager;
import dev.prisoncore.rank.Rank;
import dev.prisoncore.rank.RankManager;
import dev.prisoncore.util.Numbers;
import dev.prisoncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** /prison - everything an admin needs to set the server up and manage players. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> ROOT = List.of("help", "setup", "reload", "wand", "mine", "rank", "player",
            "price");
    private static final List<String> MINE = List.of("create", "delete", "redefine", "pos1", "pos2", "setspawn", "tp",
            "reset", "list", "info", "addblock", "removeblock", "clearblocks", "preset", "settimer", "setthreshold",
            "setrank", "setprestige");
    private static final List<String> RANK = List.of("list", "create", "delete", "setcost", "setprefix", "autocost");
    private static final List<String> PLAYER = List.of("info", "setrank", "setprestige", "addprestige",
            "setmultiplier", "addmultiplier", "reset");
    private static final List<String> PRICE = List.of("set", "remove", "list");

    private static final String[] HELP = {
            "&6&lPrisonCore admin commands",
            "&e/prison setup &7- checklist showing what is left to set up",
            "&e/prison reload &7- reload every config file",
            "&e/prison wand &7- get the mine selection wand",
            "&6Mines",
            "&e/prison mine pos1 &7/ &epos2 &7- set a corner where you stand (or use the wand)",
            "&e/prison mine create <name> &7- make a mine from your selection",
            "&e/prison mine redefine <name> &7- move a mine to your selection",
            "&e/prison mine setspawn <name> &7- set where players arrive",
            "&e/prison mine addblock <name> <block> <weight> &7- add or change a block",
            "&e/prison mine removeblock <name> <block> &7/ &eclearblocks <name>",
            "&e/prison mine preset <name> <rank> &7- copy a block mix from config.yml",
            "&e/prison mine settimer <name> <seconds|off|default> &7- timed reset",
            "&e/prison mine setthreshold <name> <percent|off|default> &7- reset when mined out",
            "&e/prison mine setrank <name> <rank|none> &7- rank needed to use it",
            "&e/prison mine setprestige <name> <number> &7- prestige needed to use it",
            "&e/prison mine reset <name|all> &7| &etp <name> &7| &einfo <name> &7| &elist &7| &edelete <name>",
            "&6Ranks",
            "&e/prison rank list &7- every rank and its price",
            "&e/prison rank setcost <rank> <cost> &7- change one price",
            "&e/prison rank autocost <firstCost> <multiplier> &7- re-price the whole ladder",
            "&e/prison rank setprefix <rank> <prefix> &7- change the chat/scoreboard tag",
            "&e/prison rank create <name> <cost> [prefix] &7- add a rank at the end",
            "&e/prison rank delete <rank>",
            "&6Players",
            "&e/prison player info <player>",
            "&e/prison player setrank <player> <rank>",
            "&e/prison player setprestige <player> <number> &7| &eaddprestige <player> <number>",
            "&e/prison player setmultiplier <player> <bonus> &7| &eaddmultiplier <player> <bonus>",
            "&e/prison player reset <player> &7- back to the first rank, prestige 0",
            "&6Sell prices",
            "&e/prison price set <item|hand> <price> &7| &eremove <item|hand> &7| &elist [page]",
            "&6Money",
            "&e/eco <give|take|set> <player> <amount>",
    };

    private final PrisonCore plugin;

    public AdminCommand(PrisonCore plugin) {
        this.plugin = plugin;
    }

    private void tell(CommandSender sender, String message) {
        plugin.tell(sender, message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, 1);
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> help(sender, rest.length > 0 ? (int) Math.max(1, Numbers.parseLong(rest[0])) : 1);
            case "setup", "status" -> setup(sender);
            case "reload" -> {
                plugin.reload();
                tell(sender, "&aReloaded config.yml, ranks.yml, prestige.yml, prices.yml, mines.yml and messages.yml.");
            }
            case "wand" -> {
                if (sender instanceof Player player) {
                    player.getInventory().addItem(plugin.mines().wand());
                    tell(sender, "&aHere is the mine wand. &7Left-click a block for corner 1, right-click for corner 2, "
                            + "then &f/prison mine create <name>&7.");
                } else {
                    plugin.messages().send(sender, "players-only");
                }
            }
            case "mine", "mines" -> mine(sender, rest);
            case "rank", "ranks" -> rank(sender, rest);
            case "player", "players" -> player(sender, rest);
            case "price", "prices" -> price(sender, rest);
            default -> tell(sender, "&cUnknown sub-command. Try &f/prison help&c.");
        }
        return true;
    }

    private void help(CommandSender sender, int page) {
        int perPage = 12;
        int pages = (HELP.length + perPage - 1) / perPage;
        int shown = Math.min(page, pages);
        for (int i = (shown - 1) * perPage; i < Math.min(HELP.length, shown * perPage); i++) {
            sender.sendMessage(Text.color(HELP[i]));
        }
        sender.sendMessage(Text.color("&7Page " + shown + "/" + pages
                + (shown < pages ? " - &f/prison help " + (shown + 1) + " &7for more" : "")));
    }

    // ------------------------------------------------------------------ setup checklist

    private void setup(CommandSender sender) {
        List<Rank> ranks = plugin.ranks().all();
        List<String> missing = new ArrayList<>();
        int linked = 0;
        for (Rank rank : ranks) {
            boolean found = false;
            for (Mine mine : plugin.mines().all()) {
                if (rank.name().equalsIgnoreCase(mine.requiredRank())) {
                    found = true;
                    break;
                }
            }
            if (found) {
                linked++;
            } else {
                missing.add(rank.name());
            }
        }
        sender.sendMessage(Text.color("&6&lPrisonCore setup checklist"));
        sender.sendMessage(Text.color(check(true) + "Ranks: &f" + ranks.size() + " &7(" + ranks.get(0).name() + " to "
                + ranks.get(ranks.size() - 1).name() + ") &8- edit ranks.yml or /prison rank"));
        sender.sendMessage(Text.color(check(missing.isEmpty()) + "Mines: &f" + linked + "/" + ranks.size()
                + " &7ranks have a mine" + (missing.isEmpty() ? "" : " &8- missing: &f" + shorten(missing))));
        sender.sendMessage(Text.color(check(!plugin.sell().prices().isEmpty()) + "Sell prices: &f"
                + plugin.sell().prices().size() + " &7items &8- edit prices.yml or /prison price"));
        sender.sendMessage(Text.color(check(plugin.prestige().enabled()) + "Prestige: &f"
                + (plugin.prestige().enabled() ? "enabled" : "disabled") + "&7, &f"
                + plugin.prestige().milestones().size() + " &7milestone prizes &8- edit prestige.yml"));
        sender.sendMessage(Text.color(check(true) + "Economy: &f" + plugin.economy().providerName()
                + " &7| Vault: &f" + (plugin.vaultPresent() ? "hooked" : "not installed (optional)")));
        sender.sendMessage(Text.color(check(true) + "Scoreboard: &f"
                + (plugin.sidebar().enabled() ? "enabled" : "disabled") + " &7| PlaceholderAPI: &f"
                + (plugin.papiPresent() ? "hooked" : "not installed (optional)")));
        if (!missing.isEmpty()) {
            sender.sendMessage(Text.color("&eTo make a mine: &f/prison wand&e, click the two corners, stand where "
                    + "players should arrive, then &f/prison mine create " + missing.get(0)));
        } else {
            sender.sendMessage(Text.color("&aEverything is set up. Players use &f/mines&a, &f/sellall&a, "
                    + "&f/rankup &aand &f/prestige&a."));
        }
    }

    private static String check(boolean ok) {
        return ok ? "&a✔ &7" : "&c✘ &7";
    }

    private static String shorten(List<String> names) {
        if (names.size() <= 12) {
            return String.join(", ", names);
        }
        return String.join(", ", names.subList(0, 12)) + ", ... (" + (names.size() - 12) + " more)";
    }

    // ------------------------------------------------------------------ mines

    private Mine requireMine(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            tell(sender, "&cPlease give a mine name.");
            return null;
        }
        Mine mine = plugin.mines().get(args[index]);
        if (mine == null) {
            tell(sender, "&cThere is no mine called &f" + args[index] + "&c. See &f/prison mine list&c.");
        }
        return mine;
    }

    private MineManager.Selection requireSelection(Player player) {
        MineManager.Selection selection = plugin.mines().selection(player);
        if (!selection.complete()) {
            tell(player, "&cSelect both corners first: use &f/prison wand &cor stand at each corner and run "
                    + "&f/prison mine pos1 &cand &f/prison mine pos2&c.");
            return null;
        }
        long max = plugin.getConfig().getLong("mines.max-volume", 2_000_000L);
        if (selection.volume() > max) {
            tell(player, "&cThat selection is " + Numbers.whole(selection.volume()) + " blocks. The limit is "
                    + Numbers.whole(max) + " (mines.max-volume in config.yml).");
            return null;
        }
        return selection;
    }

    private void mine(CommandSender sender, String[] args) {
        if (args.length == 0) {
            tell(sender, "&cUsage: /prison mine <" + String.join("|", MINE) + ">");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (plugin.mines().all().isEmpty()) {
                    tell(sender, "&eNo mines yet. Run &f/prison setup &eto see how to make one.");
                    return;
                }
                tell(sender, "&6Mines (" + plugin.mines().all().size() + "):");
                for (Mine mine : plugin.mines().all()) {
                    sender.sendMessage(Text.color(" &e" + mine.name() + " &7- " + Numbers.whole(mine.volume())
                            + " blocks, " + (int) Math.round(mine.remainingPercent()) + "% left, needs &f"
                            + plugin.mines().requirementText(mine)));
                }
                return;
            }
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                boolean first = sub.equals("pos1");
                MineManager.Selection selection = plugin.mines().select(player, player.getLocation().getBlock(), first);
                tell(sender, "&aCorner " + (first ? "1" : "2") + " set to where you are standing"
                        + (selection.complete() ? " &7(" + Numbers.whole(selection.volume()) + " blocks selected)" : "")
                        + "&a.");
                return;
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                if (args.length < 2) {
                    tell(sender, "&cUsage: /prison mine create <name>");
                    return;
                }
                String name = args[1];
                if (!MineManager.validName(name)) {
                    tell(sender, "&cMine names may only use letters, numbers, _ and - (up to 24 characters).");
                    return;
                }
                if (plugin.mines().get(name) != null) {
                    tell(sender, "&cA mine called &f" + name + " &calready exists. Use &f/prison mine redefine "
                            + name + " &cto move it.");
                    return;
                }
                MineManager.Selection selection = requireSelection(player);
                if (selection == null) {
                    return;
                }
                Mine mine = plugin.mines().create(name, selection, player.getLocation());
                plugin.mines().clearSelection(player);
                plugin.mines().reset(mine);
                tell(sender, "&aCreated mine &f" + mine.name() + " &a(" + Numbers.whole(mine.volume())
                        + " blocks) and filled it. " + (mine.hasSpawn()
                        ? "Players arrive where you are standing now."
                        : "&ePlayers arrive on top of the mine because you were standing inside it. Stand where "
                        + "they should arrive and run &f/prison mine setspawn " + mine.name() + "&e."));
                if (mine.requiredRank() != null) {
                    tell(sender, "&7It is linked to rank &f" + mine.requiredRank()
                            + "&7: players of that rank or higher can use it.");
                } else {
                    tell(sender, "&eThis mine is not linked to a rank. Open it up with &f/prison mine setrank "
                            + mine.name() + " <rank> &eor give players the permission &f" + mine.permission() + "&e.");
                }
                return;
            }
            default -> {
            }
        }

        if (!MINE.contains(sub)) {
            tell(sender, "&cUsage: /prison mine <" + String.join("|", MINE) + ">");
            return;
        }

        // Everything below works on an existing mine.
        Mine mine = sub.equals("reset") && args.length > 1 && args[1].equalsIgnoreCase("all")
                ? null : requireMine(sender, args, 1);
        if (mine == null && !(sub.equals("reset") && args.length > 1 && args[1].equalsIgnoreCase("all"))) {
            return;
        }
        switch (sub) {
            case "reset" -> {
                if (mine == null) {
                    plugin.mines().resetAll();
                    tell(sender, "&aResetting all " + plugin.mines().all().size() + " mines.");
                } else if (plugin.mines().reset(mine)) {
                    tell(sender, "&aResetting mine &f" + mine.name() + "&a.");
                } else {
                    tell(sender, "&cCould not reset &f" + mine.name()
                            + "&c: it is already resetting, has no blocks, or its world is not loaded.");
                }
            }
            case "delete" -> {
                plugin.mines().delete(mine);
                tell(sender, "&aDeleted mine &f" + mine.name() + "&a. &7The blocks in the world were left as they are.");
            }
            case "redefine" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                if (mine.resetting()) {
                    tell(sender, "&eMine &f" + mine.name() + " &eis refilling right now. Try again in a moment.");
                    return;
                }
                MineManager.Selection selection = requireSelection(player);
                if (selection == null) {
                    return;
                }
                plugin.mines().redefine(mine, selection);
                plugin.mines().clearSelection(player);
                plugin.mines().reset(mine);
                tell(sender, "&aMine &f" + mine.name() + " &anow covers your selection ("
                        + Numbers.whole(mine.volume()) + " blocks).");
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                if (mine.contains(player.getLocation())) {
                    tell(sender, "&cYou are standing inside the mine. Players would be buried when it refills - "
                            + "stand next to it or on top of it instead.");
                    return;
                }
                mine.spawn(player.getLocation());
                plugin.mines().save();
                tell(sender, "&aPlayers will now arrive here when they go to mine &f" + mine.name() + "&a.");
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "players-only");
                    return;
                }
                plugin.teleportToMine(player, mine);
            }
            case "info" -> mineInfo(sender, mine);
            case "addblock" -> {
                if (args.length < 4) {
                    tell(sender, "&cUsage: /prison mine addblock <name> <block> <weight>");
                    return;
                }
                Material material = Material.matchMaterial(args[2]);
                if (material == null || !material.isBlock() || material.isAir()) {
                    tell(sender, "&f" + args[2] + " &cis not a block.");
                    return;
                }
                double weight = Numbers.parse(args[3]);
                if (weight <= 0) {
                    plugin.messages().send(sender, "invalid-number", "input", args[3]);
                    return;
                }
                mine.blocks().put(material, weight);
                plugin.mines().save();
                tell(sender, "&aMine &f" + mine.name() + " &anow has &f" + Text.pretty(material.name()) + " &aat "
                        + Numbers.format(weight * 100 / mine.totalWeight()) + "%. &7Run &f/prison mine reset "
                        + mine.name() + " &7to see it.");
            }
            case "removeblock" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison mine removeblock <name> <block>");
                    return;
                }
                Material material = Material.matchMaterial(args[2]);
                if (material == null || mine.blocks().remove(material) == null) {
                    tell(sender, "&cMine &f" + mine.name() + " &cdoes not contain &f" + args[2] + "&c.");
                    return;
                }
                plugin.mines().save();
                tell(sender, "&aRemoved &f" + Text.pretty(material.name()) + " &afrom mine &f" + mine.name() + "&a.");
            }
            case "clearblocks" -> {
                mine.blocks().clear();
                plugin.mines().save();
                tell(sender, "&aCleared the block list of mine &f" + mine.name()
                        + "&a. Add blocks with &f/prison mine addblock&a.");
            }
            case "preset" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison mine preset <name> <rank>");
                    return;
                }
                if (plugin.mines().applyPreset(mine, args[2])) {
                    plugin.mines().save();
                    tell(sender, "&aMine &f" + mine.name() + " &anow uses the &f" + args[2].toUpperCase(Locale.ROOT)
                            + " &ablock mix. &7Run &f/prison mine reset " + mine.name() + " &7to see it.");
                } else {
                    plugin.mines().save();
                    tell(sender, "&eThere is no preset called &f" + args[2] + " &ein config.yml (mines.presets), "
                            + "so the default blocks were used.");
                }
            }
            case "settimer" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison mine settimer <name> <seconds|off|default>");
                    return;
                }
                long seconds = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "off", "none", "never" -> 0;
                    case "default" -> -1;
                    default -> Numbers.parseLong(args[2]) < 0 ? -2 : Numbers.parseLong(args[2]);
                };
                if (seconds == -2 || seconds > 604_800) {
                    plugin.messages().send(sender, "invalid-number", "input", args[2]);
                    return;
                }
                mine.resetInterval((int) seconds);
                plugin.mines().restartTimer(mine);
                plugin.mines().save();
                int effective = plugin.mines().interval(mine);
                tell(sender, "&aMine &f" + mine.name() + " &a" + (effective > 0
                        ? "now resets every " + Numbers.time(effective) + " (when it has been mined)."
                        : "no longer resets on a timer."));
            }
            case "setthreshold" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison mine setthreshold <name> <percent|off|default>");
                    return;
                }
                double percent = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "off", "none", "never" -> 0;
                    case "default" -> -1;
                    default -> Numbers.parse(args[2].replace("%", "")) < 0 ? -2 : Numbers.parse(args[2].replace("%", ""));
                };
                if (percent == -2 || percent > 100) {
                    plugin.messages().send(sender, "invalid-number", "input", args[2]);
                    return;
                }
                mine.resetThreshold(percent);
                plugin.mines().save();
                double effective = plugin.mines().threshold(mine);
                tell(sender, "&aMine &f" + mine.name() + " &a" + (effective > 0
                        ? "now resets once " + Numbers.format(effective) + "% of it has been mined."
                        : "no longer resets based on how much has been mined."));
            }
            case "setrank" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison mine setrank <name> <rank|none>");
                    return;
                }
                if (args[2].equalsIgnoreCase("none")) {
                    mine.requiredRank(null);
                } else {
                    Rank rank = plugin.ranks().get(args[2]);
                    if (rank == null) {
                        tell(sender, "&cThere is no rank called &f" + args[2] + "&c.");
                        return;
                    }
                    mine.requiredRank(rank.name());
                }
                plugin.mines().save();
                plugin.permissions().refreshAll();
                tell(sender, "&aMine &f" + mine.name() + " &anow needs: &f" + plugin.mines().requirementText(mine));
            }
            case "setprestige" -> {
                long prestige = args.length < 3 ? -1 : Numbers.parseLong(args[2]);
                if (prestige < 0) {
                    tell(sender, "&cUsage: /prison mine setprestige <name> <number> &7(0 = no prestige needed)");
                    return;
                }
                mine.requiredPrestige(prestige);
                plugin.mines().save();
                plugin.permissions().refreshAll();
                tell(sender, "&aMine &f" + mine.name() + " &anow needs: &f" + plugin.mines().requirementText(mine));
            }
            default -> tell(sender, "&cUsage: /prison mine <" + String.join("|", MINE) + ">");
        }
    }

    private void mineInfo(CommandSender sender, Mine mine) {
        tell(sender, "&6Mine " + mine.name());
        sender.sendMessage(Text.color(" &7World: &f" + mine.world() + " &7from &f" + mine.minX() + ", " + mine.minY()
                + ", " + mine.minZ() + " &7to &f" + mine.maxX() + ", " + mine.maxY() + ", " + mine.maxZ()));
        sender.sendMessage(Text.color(" &7Size: &f" + mine.sizeX() + "x" + mine.sizeY() + "x" + mine.sizeZ() + " &7= &f"
                + Numbers.whole(mine.volume()) + " &7blocks, &f" + (int) Math.round(mine.remainingPercent())
                + "% &7left"));
        sender.sendMessage(Text.color(" &7Needs: &f" + plugin.mines().requirementText(mine)
                + " &7| Spawn: &f" + (mine.hasSpawn() ? "set" : "top of the mine (default)")));
        int interval = plugin.mines().interval(mine);
        double threshold = plugin.mines().threshold(mine);
        sender.sendMessage(Text.color(" &7Resets: &f" + (interval > 0 ? "every " + Numbers.time(interval)
                + " (next in " + Numbers.time(mine.secondsLeft()) + ")" : "no timer") + "&7, &f"
                + (threshold > 0 ? "at " + Numbers.format(threshold) + "% mined" : "no threshold")));
        double total = mine.totalWeight();
        if (mine.blocks().isEmpty()) {
            sender.sendMessage(Text.color(" &cNo blocks set! Use /prison mine addblock " + mine.name()
                    + " <block> <weight>"));
        }
        for (Map.Entry<Material, Double> block : mine.blocks().entrySet()) {
            sender.sendMessage(Text.color(" &8- &f" + Text.pretty(block.getKey().name()) + " &7"
                    + Numbers.format(block.getValue() * 100 / total) + "% &8(sells for "
                    + describePrice(block.getKey()) + ")"));
        }
    }

    private String describePrice(Material block) {
        double price = plugin.sell().price(block);
        return price > 0 ? plugin.money(price) : "its drops";
    }

    // ------------------------------------------------------------------ ranks

    private void rank(CommandSender sender, String[] args) {
        if (args.length == 0) {
            tell(sender, "&cUsage: /prison rank <" + String.join("|", RANK) + ">");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                tell(sender, "&6Ranks (" + plugin.ranks().all().size() + "), total &f"
                        + plugin.money(plugin.ranks().totalCost(0)) + " &6to go from first to last:");
                for (Rank rank : plugin.ranks().all()) {
                    sender.sendMessage(Text.color(" &7" + (rank.index() + 1) + ". " + rank.prefix() + " &7- "
                            + (rank.index() == 0 ? "starting rank" : "&f" + plugin.money(rank.cost()))));
                }
            }
            case "setcost" -> {
                Rank rank = args.length > 1 ? plugin.ranks().get(args[1]) : null;
                double cost = args.length > 2 ? Numbers.parse(args[2]) : -1;
                if (rank == null || cost < 0) {
                    tell(sender, "&cUsage: /prison rank setcost <rank> <cost> &7(for example 25000 or 25k)");
                    return;
                }
                plugin.ranks().setCost(rank, cost);
                tell(sender, "&aRank &f" + rank.name() + " &anow costs &f" + plugin.money(cost) + "&a.");
            }
            case "setprefix" -> {
                Rank rank = args.length > 1 ? plugin.ranks().get(args[1]) : null;
                if (rank == null || args.length < 3) {
                    tell(sender, "&cUsage: /prison rank setprefix <rank> <prefix> &7(colour codes like &a are allowed)");
                    return;
                }
                String prefix = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.ranks().setPrefix(rank, prefix);
                tell(sender, "&aRank &f" + rank.name() + " &anow shows as " + prefix + "&a.");
            }
            case "autocost" -> {
                double first = args.length > 1 ? Numbers.parse(args[1]) : -1;
                double multiplier = args.length > 2 ? Numbers.parse(args[2]) : -1;
                if (first <= 0 || multiplier < 1 || multiplier > 100) {
                    tell(sender, "&cUsage: /prison rank autocost <firstCost> <multiplier> &7(for example "
                            + "&f/prison rank autocost 2500 1.5&7: the second rank costs 2,500 and each rank after "
                            + "costs 1.5x the one before)");
                    return;
                }
                plugin.ranks().autoCost(first, multiplier);
                Rank last = plugin.ranks().last();
                tell(sender, "&aRe-priced " + plugin.ranks().all().size() + " ranks. The last rank (&f" + last.name()
                        + "&a) now costs &f" + plugin.money(last.cost()) + " &aand the full ladder &f"
                        + plugin.money(plugin.ranks().totalCost(0)) + "&a.");
            }
            case "create" -> {
                double cost = args.length > 2 ? Numbers.parse(args[2]) : -1;
                if (args.length < 3 || cost < 0) {
                    tell(sender, "&cUsage: /prison rank create <name> <cost> [prefix]");
                    return;
                }
                if (!RankManager.validName(args[1])) {
                    tell(sender, "&cRank names may only use letters, numbers, _ and - (up to 16 characters).");
                    return;
                }
                if (plugin.ranks().get(args[1]) != null) {
                    tell(sender, "&cA rank called &f" + args[1] + " &calready exists.");
                    return;
                }
                String prefix = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
                plugin.ranks().create(args[1], cost, prefix);
                tell(sender, "&aAdded rank &f" + args[1] + " &aat the end of the ladder for &f" + plugin.money(cost)
                        + "&a. &7To change the order, edit ranks.yml and run /prison reload.");
            }
            case "delete" -> {
                Rank rank = args.length > 1 ? plugin.ranks().get(args[1]) : null;
                if (rank == null) {
                    tell(sender, "&cUsage: /prison rank delete <rank>");
                    return;
                }
                if (plugin.ranks().all().size() <= 1) {
                    tell(sender, "&cYou cannot delete the only rank.");
                    return;
                }
                plugin.ranks().delete(rank);
                tell(sender, "&aDeleted rank &f" + rank.name() + "&a. &7Players who had it are treated as the first "
                        + "rank until they rank up again.");
            }
            default -> tell(sender, "&cUsage: /prison rank <" + String.join("|", RANK) + ">");
        }
    }

    // ------------------------------------------------------------------ players

    private void player(CommandSender sender, String[] args) {
        if (args.length < 2) {
            tell(sender, "&cUsage: /prison player <" + String.join("|", PLAYER) + "> <player> [value]");
            return;
        }
        PlayerData data = plugin.findPlayer(args[1]);
        if (data == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        String value = args.length > 2 ? args[2] : null;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> {
                Rank rank = plugin.ranks().of(data);
                tell(sender, "&6" + data.name());
                sender.sendMessage(Text.color(" &7Rank: " + rank.prefix() + " &7| Prestige: &d"
                        + Numbers.whole(data.prestige())));
                sender.sendMessage(Text.color(" &7Balance: &a" + plugin.currencySymbol()
                        + Numbers.full(plugin.economy().balance(data.uuid()))));
                sender.sendMessage(Text.color(" &7Bonus sell multiplier: &b+" + Numbers.format(data.bonusMultiplier())
                        + "x &7| Blocks mined: &f" + Numbers.whole(data.blocksMined())));
                sender.sendMessage(Text.color(" &7Autosell: &f" + (data.autosell() ? "on" : "off")
                        + " &7| Scoreboard: &f" + (data.scoreboard() ? "on" : "off")));
                return;
            }
            case "setrank" -> {
                Rank rank = plugin.ranks().get(value);
                if (rank == null) {
                    tell(sender, "&cUsage: /prison player setrank <player> <rank>");
                    return;
                }
                data.rank(rank.name());
                tell(sender, "&a" + data.name() + " is now rank " + rank.prefix() + "&a.");
            }
            case "setprestige", "addprestige" -> {
                long amount = Numbers.parseLong(value);
                if (amount < 0) {
                    tell(sender, "&cUsage: /prison player " + args[0].toLowerCase(Locale.ROOT) + " <player> <number>");
                    return;
                }
                boolean add = args[0].equalsIgnoreCase("addprestige");
                data.prestige(add ? data.prestige() + amount : amount);
                tell(sender, "&a" + data.name() + " is now &dPrestige " + Numbers.whole(data.prestige())
                        + "&a. &7(Milestone prizes are only given when players prestige themselves.)");
            }
            case "setmultiplier", "addmultiplier" -> {
                double amount = Numbers.parse(value);
                if (amount < 0) {
                    tell(sender, "&cUsage: /prison player " + args[0].toLowerCase(Locale.ROOT)
                            + " <player> <bonus> &7(0.5 means +0.5x on top of the normal 1x)");
                    return;
                }
                boolean add = args[0].equalsIgnoreCase("addmultiplier");
                data.bonusMultiplier(add ? data.bonusMultiplier() + amount : amount);
                tell(sender, "&a" + data.name() + " now has a &b+" + Numbers.format(data.bonusMultiplier())
                        + "x &abonus sell multiplier.");
            }
            case "reset" -> {
                data.rank(plugin.ranks().first().name());
                data.prestige(0);
                data.bonusMultiplier(0);
                data.blocksMined(0);
                plugin.economy().set(data.uuid(), plugin.getConfig().getDouble("economy.starting-balance", 0));
                tell(sender, "&a" + data.name() + " has been reset to the first rank, prestige 0 and the starting "
                        + "balance.");
            }
            default -> {
                tell(sender, "&cUsage: /prison player <" + String.join("|", PLAYER) + "> <player> [value]");
                return;
            }
        }
        Player online = Bukkit.getPlayer(data.uuid());
        if (online != null) {
            plugin.permissions().refresh(online);
            plugin.sidebar().update(online);
        }
    }

    // ------------------------------------------------------------------ prices

    private Material material(CommandSender sender, String input) {
        if (input.equalsIgnoreCase("hand")) {
            if (sender instanceof Player player) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!held.getType().isAir()) {
                    return held.getType();
                }
            }
            tell(sender, "&cHold the item you mean in your main hand.");
            return null;
        }
        Material material = Material.matchMaterial(input);
        if (material == null || !material.isItem() || material.isAir()) {
            tell(sender, "&f" + input + " &cis not an item.");
            return null;
        }
        return material;
    }

    private void price(CommandSender sender, String[] args) {
        if (args.length == 0) {
            tell(sender, "&cUsage: /prison price <set|remove|list>");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                Map<String, Double> sorted = new TreeMap<>();
                for (Map.Entry<Material, Double> entry : plugin.sell().prices().entrySet()) {
                    sorted.put(entry.getKey().name(), entry.getValue());
                }
                List<Map.Entry<String, Double>> entries = new ArrayList<>(sorted.entrySet());
                entries.sort(Map.Entry.comparingByValue());
                int perPage = 12;
                int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
                int shown = (int) Math.max(1, Math.min(pages, args.length > 1 ? Numbers.parseLong(args[1]) : 1));
                tell(sender, "&6Sell prices &7(page " + shown + "/" + pages + ")");
                for (int i = (shown - 1) * perPage; i < Math.min(entries.size(), shown * perPage); i++) {
                    sender.sendMessage(Text.color(" &f" + Text.pretty(entries.get(i).getKey()) + " &7- &a"
                            + plugin.currencySymbol() + Numbers.full(entries.get(i).getValue())));
                }
            }
            case "set" -> {
                if (args.length < 3) {
                    tell(sender, "&cUsage: /prison price set <item|hand> <price>");
                    return;
                }
                Material material = material(sender, args[1]);
                if (material == null) {
                    return;
                }
                double price = Numbers.parse(args[2]);
                if (price <= 0) {
                    plugin.messages().send(sender, "invalid-number", "input", args[2]);
                    return;
                }
                plugin.sell().setPrice(material, price);
                tell(sender, "&f" + Text.pretty(material.name()) + " &anow sells for &f" + plugin.currencySymbol()
                        + Numbers.full(price) + " &aeach.");
            }
            case "remove" -> {
                if (args.length < 2) {
                    tell(sender, "&cUsage: /prison price remove <item|hand>");
                    return;
                }
                Material material = material(sender, args[1]);
                if (material == null) {
                    return;
                }
                plugin.sell().setPrice(material, 0);
                tell(sender, "&f" + Text.pretty(material.name()) + " &acan no longer be sold.");
            }
            default -> tell(sender, "&cUsage: /prison price <set|remove|list>");
        }
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Completions.filter(args[0], ROOT);
        }
        String group = args[0].toLowerCase(Locale.ROOT);
        String typed = args[args.length - 1];
        switch (group) {
            case "mine", "mines" -> {
                if (args.length == 2) {
                    return Completions.filter(typed, MINE);
                }
                String sub = args[1].toLowerCase(Locale.ROOT);
                if (args.length == 3 && !List.of("list", "pos1", "pos2", "create").contains(sub)) {
                    List<String> names = mineNames();
                    if (sub.equals("reset")) {
                        names.add("all");
                    }
                    return Completions.filter(typed, names);
                }
                if (args.length == 4) {
                    switch (sub) {
                        case "addblock" -> {
                            return Completions.filter(typed.toUpperCase(Locale.ROOT), blockNames());
                        }
                        case "removeblock" -> {
                            Mine mine = plugin.mines().get(args[2]);
                            List<String> names = new ArrayList<>();
                            if (mine != null) {
                                for (Material material : mine.blocks().keySet()) {
                                    names.add(material.name());
                                }
                            }
                            return Completions.filter(typed, names);
                        }
                        case "setrank" -> {
                            List<String> names = rankNames();
                            names.add("none");
                            return Completions.filter(typed, names);
                        }
                        case "preset" -> {
                            return Completions.filter(typed, rankNames());
                        }
                        case "settimer", "setthreshold" -> {
                            return Completions.filter(typed, List.of("default", "off"));
                        }
                        default -> {
                        }
                    }
                }
            }
            case "rank", "ranks" -> {
                if (args.length == 2) {
                    return Completions.filter(typed, RANK);
                }
                if (args.length == 3 && List.of("setcost", "setprefix", "delete").contains(args[1].toLowerCase(Locale.ROOT))) {
                    return Completions.filter(typed, rankNames());
                }
            }
            case "player", "players" -> {
                if (args.length == 2) {
                    return Completions.filter(typed, PLAYER);
                }
                if (args.length == 3) {
                    return Completions.players(typed);
                }
                if (args.length == 4 && args[1].equalsIgnoreCase("setrank")) {
                    return Completions.filter(typed, rankNames());
                }
            }
            case "price", "prices" -> {
                if (args.length == 2) {
                    return Completions.filter(typed, PRICE);
                }
                if (args.length == 3 && !args[1].equalsIgnoreCase("list")) {
                    List<String> names = new ArrayList<>();
                    names.add("hand");
                    if (args[1].equalsIgnoreCase("remove")) {
                        for (Material material : plugin.sell().prices().keySet()) {
                            names.add(material.name());
                        }
                    } else {
                        for (Material material : Material.values()) {
                            if (material.isItem() && !material.isLegacy()) {
                                names.add(material.name());
                            }
                        }
                    }
                    return Completions.filter(typed.equalsIgnoreCase("hand") ? typed : typed.toUpperCase(Locale.ROOT),
                            names);
                }
            }
            default -> {
            }
        }
        return List.of();
    }

    private List<String> mineNames() {
        List<String> names = new ArrayList<>();
        for (Mine mine : plugin.mines().all()) {
            names.add(mine.name());
        }
        return names;
    }

    private List<String> rankNames() {
        List<String> names = new ArrayList<>();
        for (Rank rank : plugin.ranks().all()) {
            names.add(rank.name());
        }
        return names;
    }

    private static List<String> blockNames() {
        List<String> names = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isBlock() && !material.isLegacy() && !material.isAir()) {
                names.add(material.name());
            }
        }
        return names;
    }
}
