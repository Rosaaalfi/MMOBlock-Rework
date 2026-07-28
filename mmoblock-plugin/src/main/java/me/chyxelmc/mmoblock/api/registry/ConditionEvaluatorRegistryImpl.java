package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.condition.ConditionEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ConditionEvaluatorRegistry}.
 */
public final class ConditionEvaluatorRegistryImpl implements ConditionEvaluatorRegistry {

    private final Map<String, ConditionEvaluator> evaluators = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull final String type, @NotNull final ConditionEvaluator evaluator) {
        final ConditionEvaluator previous = this.evaluators.putIfAbsent(type, evaluator);
        if (previous != null) {
            throw new IllegalArgumentException(
                "Condition evaluator '" + type + "' is already registered."
            );
        }
    }

    @Override
    public void unregister(@NotNull final String type) {
        this.evaluators.remove(type);
    }

    @Override
    @Nullable
    public ConditionEvaluator getEvaluator(@NotNull final String type) {
        return this.evaluators.get(type);
    }

    @Override
    public boolean isRegistered(@NotNull final String type) {
        return this.evaluators.containsKey(type);
    }

    @Override
    @NotNull
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(this.evaluators.keySet());
    }
}
