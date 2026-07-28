package me.chyxelmc.mmoblock.api.condition;

import me.chyxelmc.mmoblock.api.model.ConditionDefinition;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Evaluates custom condition types for block interaction eligibility.
 *
 * <p>Third-party addon plugins can register custom condition evaluators to add
 * new condition types beyond the built-in placeholder-based conditions.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MMOBlockApi.get().getConditionEvaluatorRegistry()
 *     .register("has_quest", (player, definition) -> {
 *         QuestPlugin questPlugin = (QuestPlugin) Bukkit.getPluginManager().getPlugin("QuestPlugin");
 *         return questPlugin.hasActiveQuest(player, definition.value());
 *     });
 * }</pre>
 *
 * <p>Config example:</p>
 * <pre>{@code
 * conditions:
 *   - condition: 1
 *     type: "has_quest"
 *     value: "daily_mining"
 *     placeholderText:
 *       require: "&a✔ Quest active"
 *       notMet: "&c✘ Quest required: daily_mining"
 * }</pre>
 */
@FunctionalInterface
public interface ConditionEvaluator {

    /**
     * Evaluate whether the condition is met for the given player.
     *
     * @param player     the player attempting to interact with the block
     * @param definition the condition definition from the block config
     * @return {@code true} if the condition is satisfied, {@code false} otherwise
     */
    boolean evaluate(@NotNull Player player, @NotNull ConditionDefinition definition);
}
