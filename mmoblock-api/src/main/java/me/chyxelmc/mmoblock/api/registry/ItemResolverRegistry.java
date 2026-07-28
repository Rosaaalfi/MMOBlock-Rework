package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.item.ItemResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Registry for custom item resolvers.
 *
 * <p>Third-party addon plugins can register item resolvers to allow their custom
 * items to be used as tools and drops in MMOBlock configurations.</p>
 */
public interface ItemResolverRegistry {

    /**
     * Register a custom item resolver.
     *
     * @param resolver the item resolver to register
     * @throws IllegalArgumentException if a resolver with the same namespace is already registered
     */
    void register(@NotNull ItemResolver resolver);

    /**
     * Unregister a previously registered item resolver by its namespace.
     *
     * @param namespace the resolver namespace to unregister
     */
    void unregister(@NotNull String namespace);

    /**
     * Get a registered item resolver by its namespace.
     *
     * @param namespace the resolver namespace
     * @return the resolver, or null if not registered
     */
    @Nullable
    ItemResolver getResolver(@NotNull String namespace);

    /**
     * Check if a namespace is registered.
     *
     * @param namespace the resolver namespace
     * @return true if registered
     */
    boolean isRegistered(@NotNull String namespace);

    /**
     * Get all registered namespaces.
     *
     * @return an unmodifiable set of namespaces
     */
    @NotNull
    Set<String> getRegisteredNamespaces();
}
