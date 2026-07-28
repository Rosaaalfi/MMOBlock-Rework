package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.model.ModelRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Registry for custom block model renderers.
 *
 * <p>Third-party addon plugins can register custom model renderers that handle
 * new {@code modelType} sections in block definitions.</p>
 */
public interface ModelRendererRegistry {

    /**
     * Register a custom model renderer.
     *
     * @param type     the model type identifier (e.g., {@code "myplugin:custom_model"})
     * @param renderer the model renderer implementation
     * @throws IllegalArgumentException if the type is already registered
     */
    void register(@NotNull String type, @NotNull ModelRenderer renderer);

    /**
     * Unregister a previously registered model renderer.
     *
     * @param type the model type to unregister
     */
    void unregister(@NotNull String type);

    /**
     * Get a registered model renderer by its type.
     *
     * @param type the model type
     * @return the renderer, or null if not registered
     */
    @Nullable
    ModelRenderer getRenderer(@NotNull String type);

    /**
     * Check if a model type is registered.
     *
     * @param type the model type
     * @return true if registered
     */
    boolean isRegistered(@NotNull String type);

    /**
     * Get all registered model type identifiers.
     *
     * @return an unmodifiable set of model types
     */
    @NotNull
    Set<String> getRegisteredTypes();
}
