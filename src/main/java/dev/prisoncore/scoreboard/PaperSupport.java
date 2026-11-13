package dev.prisoncore.scoreboard;

import org.bukkit.scoreboard.Objective;

/** Paper-only extras, kept in their own class so the plugin still runs on plain Spigot. */
final class PaperSupport {

    private PaperSupport() {
    }

    /** Hides the red score numbers on the right of the sidebar. Does nothing where unsupported. */
    static void hideNumbers(Objective objective) {
        try {
            objective.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
        } catch (Throwable ignored) {
            // Not running on Paper; the numbers simply stay visible.
        }
    }
}
