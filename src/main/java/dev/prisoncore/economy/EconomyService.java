package dev.prisoncore.economy;

import java.util.UUID;

/** Where player money lives: PrisonCore's own balances, or another economy plugin through Vault. */
public interface EconomyService {

    /** Human readable name shown in /prison setup. */
    String providerName();

    /** True when PrisonCore stores the balances itself. */
    boolean internal();

    double balance(UUID player);

    void deposit(UUID player, double amount);

    /** Takes money if the player can afford it. Returns false (and changes nothing) otherwise. */
    boolean withdraw(UUID player, double amount);

    void set(UUID player, double amount);

    default boolean has(UUID player, double amount) {
        return balance(player) >= amount;
    }
}
