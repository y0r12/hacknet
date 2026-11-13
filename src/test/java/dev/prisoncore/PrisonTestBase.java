package dev.prisoncore;

import dev.prisoncore.data.PlayerData;
import dev.prisoncore.mine.Mine;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Starts a simulated server with PrisonCore loaded before every test. */
abstract class PrisonTestBase {

    protected ServerMock server;
    protected WorldMock world;
    protected PrisonCore plugin;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("prison");
        beforeLoad();
        plugin = loadPlugin();
        tick(3);
    }

    /** Hook for tests that need other plugins present before PrisonCore enables. */
    protected void beforeLoad() {
    }

    protected PrisonCore loadPlugin() {
        return MockBukkit.load(PrisonCore.class);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    protected void tick(int ticks) {
        server.getScheduler().performTicks(ticks);
    }

    /** A normal player who has just joined, with their join messages cleared. */
    protected PlayerMock join(String name) {
        PlayerMock player = server.addPlayer(name);
        messages(player);
        return player;
    }

    protected PlayerMock admin(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(true);
        messages(player);
        return player;
    }

    protected PlayerData data(PlayerMock player) {
        return plugin.data(player);
    }

    protected double balance(PlayerMock player) {
        return plugin.economy().balance(player.getUniqueId());
    }

    protected void setBalance(PlayerMock player, double amount) {
        plugin.economy().set(player.getUniqueId(), amount);
    }

    /** Everything the player has been told since the last call, without colour codes. */
    protected List<String> messages(PlayerMock player) {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = player.nextMessage()) != null) {
            lines.add(ChatColor.stripColor(line));
        }
        return lines;
    }

    protected String allMessages(PlayerMock player) {
        return String.join("\n", messages(player));
    }

    /** Creates a mine the way an admin would: stand at two corners, then /prison mine create. */
    protected Mine createMine(PlayerMock admin, String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        admin.teleport(new Location(world, x1 + 0.5, y1, z1 + 0.5));
        admin.performCommand("prison mine pos1");
        admin.teleport(new Location(world, x2 + 0.5, y2, z2 + 0.5));
        admin.performCommand("prison mine pos2");
        admin.teleport(new Location(world, x1 + 0.5, Math.max(y1, y2) + 1, z1 + 0.5));
        admin.performCommand("prison mine create " + name);
        tick(5);
        messages(admin);
        return plugin.mines().get(name);
    }

    /** Edits one of the plugin's YAML files on disk and reloads the plugin. */
    protected void editFile(String fileName, Consumer<YamlConfiguration> edit) {
        File file = new File(plugin.getDataFolder(), fileName);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        edit.accept(config);
        try {
            config.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        plugin.reload();
    }
}
