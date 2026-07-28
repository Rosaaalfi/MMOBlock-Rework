package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.condition.ConditionEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Registry for custom condition evaluators.
 *
 * <p>Third-party addon plugins can register custom condition types that are evaluated
 * when players attempt to interact with blocks.</p>
 */
public interface ConditionEvaluatorRegistry {

    /**
     * Register a custom condition evaluator.
     *
     * @param type      the condition type identifier (e.g., {@code "has_quest"}, {@code "has_permission"})
     * @param evaluator the condition evaluator implementation
     * @throws IllegalArgumentException if the type is already registered
     */
    void register(@NotNull String type, @NotNull ConditionEvaluator evaluator);

    /**
     * Unregister a previously registered condition evaluator.
     *
     * @param type the condition type to unregister
     */
    void unregister(@NotNull String type);

    /**
     * Get a registered condition evaluator by its type.
     *
     * @param type the condition type
     * @return the evaluator, or null if not registered
     */
    @Nullable
    ConditionEvaluator getEvaluator(@NotNull String type);

    /**
     * Check if a condition type is registered.
     *
     * @param type the condition type
     * @return true if registered
     */
    boolean isRegistered(@NotNull String type);

    /**
     * Get all registered condition type identifiers.
     *
     * @return an unmodifiable set of condition types
     */
    @NotNull
    Set<String> getRegisteredTypes();
}
