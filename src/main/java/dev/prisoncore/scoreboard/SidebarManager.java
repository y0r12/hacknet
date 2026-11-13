package dev.prisoncore.scoreboard;

import dev.prisoncore.PrisonCore;
import dev.prisoncore.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The sidebar scoreboard. Each player gets their own board; lines only resend when their text changes. */
public final class SidebarManager {

    private static final int MAX_LINES = 15;

    private static final class Board {
        Scoreboard scoreboard;
        Objective objective;
        Team[] teams;
        String[] shown;
        String title;
    }

    private final PrisonCore plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private boolean enabled;
    private String title = "";
    private List<String> lines = List.of();
    private BukkitTask task;

    public SidebarManager(PrisonCore plugin) {
        this.plugin = plugin;
    }

    /** (Re)reads the scoreboard section of config.yml and rebuilds every board. */
    public void load() {
        shutdown();
        enabled = plugin.getConfig().getBoolean("scoreboard.enabled", true);
        title = plugin.getConfig().getString("scoreboard.title", "&6&lPRISON");
        List<String> configured = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        if (configured.size() > MAX_LINES) {
            plugin.getLogger().warning("scoreboard.lines has " + configured.size()
                    + " lines but Minecraft only shows " + MAX_LINES + ". The extra lines are ignored.");
            configured = configured.subList(0, MAX_LINES);
        }
        lines = List.copyOf(configured);
        if (!enabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            show(player);
        }
        long period = Math.max(1, plugin.getConfig().getLong("scoreboard.update-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, period, period);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Gives the player a board, unless they switched theirs off. */
    public void show(Player player) {
        if (!enabled) {
            return;
        }
        PlayerData data = plugin.data(player);
        if (!data.scoreboard() || boards.containsKey(player.getUniqueId())) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Board board = new Board();
        board.scoreboard = manager.getNewScoreboard();
        board.title = plugin.placeholders().apply(player, title);
        board.objective = board.scoreboard.registerNewObjective("prisoncore", Criteria.DUMMY, board.title);
        board.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            PaperSupport.hideNumbers(board.objective);
        } catch (Throwable ignored) {
            // Cosmetic only.
        }
        int count = lines.size();
        board.teams = new Team[count];
        board.shown = new String[count];
        ChatColor[] colors = ChatColor.values();
        for (int i = 0; i < count; i++) {
            // Each line is an invisible, unique entry; the visible text is the team prefix.
            String entry = colors[i].toString() + ChatColor.RESET;
            Team team = board.scoreboard.registerNewTeam("line" + i);
            team.addEntry(entry);
            board.objective.getScore(entry).setScore(count - i);
            board.teams[i] = team;
        }
        boards.put(player.getUniqueId(), board);
        update(player, board);
        player.setScoreboard(board.scoreboard);
    }

    public void hide(Player player) {
        Board board = boards.remove(player.getUniqueId());
        if (board == null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline() && player.getScoreboard() == board.scoreboard) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    /** Forgets a player who logged out. */
    public void forget(Player player) {
        boards.remove(player.getUniqueId());
    }

    /** Switches the player's board on or off and remembers the choice. Returns the new state. */
    public boolean toggle(Player player) {
        PlayerData data = plugin.data(player);
        boolean now = !data.scoreboard();
        data.scoreboard(now);
        if (now) {
            show(player);
        } else {
            hide(player);
        }
        return now;
    }

    public boolean showing(Player player) {
        return boards.containsKey(player.getUniqueId());
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            if (board != null) {
                update(player, board);
            }
        }
    }

    /** Refreshes one player's board right now (used after rank-ups so the change is instant). */
    public void update(Player player) {
        Board board = boards.get(player.getUniqueId());
        if (board != null) {
            update(player, board);
        }
    }

    private void update(Player player, Board board) {
        String newTitle = plugin.placeholders().apply(player, title);
        if (!newTitle.equals(board.title)) {
            board.title = newTitle;
            board.objective.setDisplayName(newTitle);
        }
        for (int i = 0; i < board.teams.length; i++) {
            String text = plugin.placeholders().apply(player, lines.get(i));
            if (text.equals(board.shown[i])) {
                continue;
            }
            board.shown[i] = text;
            try {
                board.teams[i].setPrefix(text);
            } catch (IllegalArgumentException tooLong) {
                board.teams[i].setPrefix(text.substring(0, Math.min(text.length(), 64)));
            }
        }
    }

    /** The text currently shown to a player, top to bottom. Empty if they have no board. */
    public List<String> shownLines(Player player) {
        Board board = boards.get(player.getUniqueId());
        if (board == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(board.shown.length);
        for (String line : board.shown) {
            result.add(line == null ? "" : line);
        }
        return result;
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            if (board != null && manager != null && player.getScoreboard() == board.scoreboard) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        boards.clear();
    }
}
