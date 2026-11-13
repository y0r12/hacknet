package dev.prisoncore;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vault integration: sharing the built-in economy, and using somebody else's. */
class VaultTest {

    /** With Vault installed, other plugins can read and change PrisonCore balances. */
    @Nested
    class SharingTheBuiltInEconomy extends PrisonTestBase {

        @Override
        protected void beforeLoad() {
            MockBukkit.createMockPlugin("Vault");
        }

        @Test
        void otherPluginsSeePrisonCoreMoney() {
            assertTrue(plugin.vaultPresent());
            RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
            assertNotNull(registration, "PrisonCore should register an economy with Vault");
            assertSame(plugin, registration.getPlugin());
            Economy vault = registration.getProvider();
            assertEquals("PrisonCore", vault.getName());

            PlayerMock player = join("Steve");
            setBalance(player, 100);
            assertEquals(100, vault.getBalance(player));
            assertEquals(100, vault.getBalance("steve"));
            assertTrue(vault.has(player, 100));
            assertFalse(vault.has(player, 100.01));

            assertTrue(vault.depositPlayer(player, 50).transactionSuccess());
            assertEquals(150, balance(player));

            EconomyResponse tooMuch = vault.withdrawPlayer(player, 500);
            assertFalse(tooMuch.transactionSuccess());
            assertEquals(150, balance(player), "a failed withdrawal must not take anything");

            EconomyResponse fine = vault.withdrawPlayer(player, 30);
            assertTrue(fine.transactionSuccess());
            assertEquals(120, fine.balance);
            assertEquals(120, balance(player));

            assertFalse(vault.withdrawPlayer(player, -5).transactionSuccess());
            assertFalse(vault.depositPlayer(player, -5).transactionSuccess());
            assertEquals(120, balance(player));
            assertEquals("$1,234.5", vault.format(1234.5));
            assertFalse(vault.hasBankSupport());
        }
    }

    /** With economy.provider: vault, PrisonCore spends and pays another plugin's money. */
    @Nested
    class UsingAnotherEconomy extends PrisonTestBase {

        private final Map<UUID, Double> bank = new HashMap<>();

        @Override
        protected void beforeLoad() {
            MockBukkit.createMockPlugin("Vault");
            Plugin other = MockBukkit.createMockPlugin("OtherEconomy");
            server.getServicesManager().register(Economy.class, fakeEconomy(), other, ServicePriority.Normal);
        }

        @Override
        protected PrisonCore loadPlugin() {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    PrisonCore.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
            config.set("economy.provider", "vault");
            return MockBukkit.loadWithConfig(PrisonCore.class, config);
        }

        /** A tiny in-memory economy standing in for EssentialsX or similar. */
        private Economy fakeEconomy() {
            return (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getName":
                                return "OtherEconomy";
                            case "isEnabled":
                                return true;
                            case "getBalance":
                                return bank.getOrDefault(((OfflinePlayer) args[0]).getUniqueId(), 0.0);
                            case "depositPlayer": {
                                UUID id = ((OfflinePlayer) args[0]).getUniqueId();
                                bank.merge(id, (Double) args[1], Double::sum);
                                return new EconomyResponse((Double) args[1], bank.get(id),
                                        EconomyResponse.ResponseType.SUCCESS, null);
                            }
                            case "withdrawPlayer": {
                                UUID id = ((OfflinePlayer) args[0]).getUniqueId();
                                double current = bank.getOrDefault(id, 0.0);
                                double amount = (Double) args[1];
                                if (current < amount) {
                                    return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "poor");
                                }
                                bank.put(id, current - amount);
                                return new EconomyResponse(amount, current - amount,
                                        EconomyResponse.ResponseType.SUCCESS, null);
                            }
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            case "toString":
                                return "FakeEconomy";
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }

        @Test
        void rankupsAndSalesUseTheOtherPluginsMoney() {
            assertFalse(plugin.economy().internal());
            assertEquals("OtherEconomy (via Vault)", plugin.economy().providerName());
            assertEquals(1, server.getServicesManager().getRegistrations(Economy.class).size(),
                    "PrisonCore must not register a second economy");

            PlayerMock player = join("Steve");
            bank.put(player.getUniqueId(), 3000.0);
            assertEquals(3000, balance(player));

            player.performCommand("rankup");
            assertEquals("B", plugin.ranks().of(data(player)).name());
            assertEquals(500.0, bank.get(player.getUniqueId()));

            player.getInventory().clear();
            player.getInventory().addItem(new ItemStack(Material.COAL, 10));
            player.performCommand("sellall");
            assertEquals(570.0, bank.get(player.getUniqueId()));
            assertEquals(0, data(player).balance(), "PrisonCore's own balance stays unused");

            player.performCommand("baltop");
            assertTrue(allMessages(player).contains("handled by another plugin"));
        }

        @Test
        void prestigeWipesTheOtherPluginsBalance() {
            PlayerMock player = join("Steve");
            data(player).rank("Z");
            bank.put(player.getUniqueId(), 80_000_000.0);
            player.performCommand("prestige");
            assertEquals(1, data(player).prestige());
            assertEquals(50_000.0, bank.get(player.getUniqueId()), 1e-6);
        }
    }
}
