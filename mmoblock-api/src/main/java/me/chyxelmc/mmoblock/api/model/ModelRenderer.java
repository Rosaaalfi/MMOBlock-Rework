package me.chyxelmc.mmoblock.api.model;

import me.chyxelmc.mmoblock.api.model.ModelContext;
import org.jetbrains.annotations.NotNull;

/**
 * Renders a custom block model type.
 *
 * <p>Third-party addon plugins can register custom model renderers to add new
 * visual block types beyond the built-in BDEngine, Schematic, ModelEngine,
 * BetterModel, and Block types.</p>
 *
 * <p>Implementations should handle the full lifecycle:
 * <ul>
 *   <li>{@link #spawn(ModelContext)} — create and show the model</li>
 *   <li>{@link #despawn(ModelContext)} — remove the model</li>
 *   <li>{@link #animate(ModelContext, String)} — play an animation</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MMOBlockApi.get().getModelRendererRegistry()
 *     .register("myplugin:custom_model", new MyCustomModelRenderer());
 * }</pre>
 *
 * <p>Config example:</p>
 * <pre>{@code
 * modelType:
 *   custom:
 *     type: "myplugin:custom_model"
 *     config:
 *       model_id: "my_crystal"
 *       scale: 1.5
 *       color: "blue"
 * }</pre>
 */
public interface ModelRenderer {

    /**
     * Spawn (or show) the model at the given context.
     *
     * @param context the spawn context
     */
    void spawn(@NotNull ModelContext context);

    /**
     * Despawn (or hide) the model.
     *
     * @param context the despawn context
     */
    void despawn(@NotNull ModelContext context);

    /**
     * Play an animation on the model.
     *
     * @param context the animation context
     * @param animationName the name of the animation (e.g., "onClick", "onSpawn")
     */
    default void animate(@NotNull ModelContext context, @NotNull String animationName) {
        // no-op by default
    }

    /**
     * Whether this renderer supports per-player rendering.
     * If true, the model can be shown/hidden per individual player.
     *
     * @return true if per-player rendering is supported
     */
    default boolean supportsPerPlayer() {
        return false;
    }
}
