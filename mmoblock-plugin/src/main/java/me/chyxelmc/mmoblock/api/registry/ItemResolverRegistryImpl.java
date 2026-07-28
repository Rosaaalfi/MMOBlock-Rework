package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.item.ItemResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ItemResolverRegistry}.
 */
public final class ItemResolverRegistryImpl implements ItemResolverRegistry {

    private final Map<String, ItemResolver> resolvers = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull final ItemResolver resolver) {
        final String namespace = resolver.getNamespace();
        final ItemResolver previous = this.resolvers.putIfAbsent(namespace, resolver);
        if (previous != null) {
            throw new IllegalArgumentException(
                "Item resolver '" + namespace + "' is already registered."
            );
        }
    }

    @Override
    public void unregister(@NotNull final String namespace) {
        this.resolvers.remove(namespace);
    }

    @Override
    @Nullable
    public ItemResolver getResolver(@NotNull final String namespace) {
        return this.resolvers.get(namespace);
    }

    @Override
    public boolean isRegistered(@NotNull final String namespace) {
        return this.resolvers.containsKey(namespace);
    }

    @Override
    @NotNull
    public Set<String> getRegisteredNamespaces() {
        return Collections.unmodifiableSet(this.resolvers.keySet());
    }
}
