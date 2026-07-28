package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.drop.DropHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link DropHandlerRegistry}.
 */
public final class DropHandlerRegistryImpl implements DropHandlerRegistry {

    private final Map<String, DropHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull final String id, @NotNull final DropHandler handler) {
        final DropHandler previous = this.handlers.putIfAbsent(id, handler);
        if (previous != null) {
            throw new IllegalArgumentException(
                "Drop handler '" + id + "' is already registered. " +
                "Use unregister() first before re-registering."
            );
        }
    }

    @Override
    public void unregister(@NotNull final String id) {
        this.handlers.remove(id);
    }

    @Override
    @Nullable
    public DropHandler getHandler(@NotNull final String id) {
        return this.handlers.get(id);
    }

    @Override
    public boolean isRegistered(@NotNull final String id) {
        return this.handlers.containsKey(id);
    }

    @Override
    @NotNull
    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(this.handlers.keySet());
    }
}
