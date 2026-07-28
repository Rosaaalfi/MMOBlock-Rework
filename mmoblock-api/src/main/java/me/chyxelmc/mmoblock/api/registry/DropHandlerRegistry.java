package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.drop.DropHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Registry for custom drop handlers.
 *
 * <p>Third-party addon plugins can register custom drop handlers here to handle
 * {@link me.chyxelmc.mmoblock.api.model.DropType#CUSTOM} drop entries.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MMOBlockApi api = MMOBlockApi.get();
 * if (api != null) {
 *     api.getDropHandlerRegistry()
 *         .register("myplugin:special_drop", MyDropHandler::new);
 * }
 * }</pre>
 */
public interface DropHandlerRegistry {

    /**
     * Register a custom drop handler.
     *
     * @param id      the unique handler ID (should be namespaced, e.g. {@code "yourplugin:drop_id"})
     * @param handler the drop handler implementation
     * @throws IllegalArgumentException if the id is already registered
     */
    void register(@NotNull String id, @NotNull DropHandler handler);

    /**
     * Unregister a previously registered drop handler.
     *
     * @param id the handler ID to unregister
     */
    void unregister(@NotNull String id);

    /**
     * Get a registered drop handler by its ID.
     *
     * @param id the handler ID
     * @return the drop handler, or null if not registered
     */
    @Nullable
    DropHandler getHandler(@NotNull String id);

    /**
     * Check if a handler ID is registered.
     *
     * @param id the handler ID
     * @return true if registered
     */
    boolean isRegistered(@NotNull String id);

    /**
     * Get all registered handler IDs.
     *
     * @return an unmodifiable set of handler IDs
     */
    @NotNull
    Set<String> getRegisteredIds();
}
