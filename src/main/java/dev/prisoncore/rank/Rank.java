package dev.prisoncore.rank;

import java.util.List;

/**
 * One step on the rank ladder.
 *
 * @param name        rank name, e.g. "A"
 * @param index       position on the ladder, 0 = starting rank
 * @param cost        base price to rank up INTO this rank (before the prestige increase)
 * @param prefix      coloured chat/scoreboard prefix
 * @param permissions extra permission nodes granted while the player holds this rank or a higher one
 * @param commands    console commands run when a player reaches this rank
 */
public record Rank(String name, int index, double cost, String prefix, List<String> permissions,
                   List<String> commands) {
}
