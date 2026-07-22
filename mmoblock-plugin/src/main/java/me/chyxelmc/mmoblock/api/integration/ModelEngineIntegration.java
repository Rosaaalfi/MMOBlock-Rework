package me.chyxelmc.mmoblock.api.integration;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Entity;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

/**
 * Integration layer for <a href="https://git.lumine.io/mythiccraft/model-engine-4">ModelEngine R4.1.0+</a>.
 * <p>
 * Uses direct ModelEngine API references guarded by a static availability check.
 * If ModelEngine is not installed the methods are no-ops and the class loads safely
 * because the JVM resolves class references lazily.
 * <p>
 * Usage in block config YAML:
 * <pre>{@code
 * modelType:
 *   modelEngine:
 *     enabled: true
 *     model: "iron_crystal:1.0"
 *     onClick:
 *       name: "example_onClick_animation"
 *       lerpin: 0.1
 *       lerpout: 0.1
 *       speed: 1.0
 *     onDead:
 *       name: "example_onDead_animation"
 *       lerpin: 0.1
 *       lerpout: 0.1
 *       speed: 1.0
 * }</pre>
 */
public final class ModelEngineIntegration {

    private static final boolean AVAILABLE;
    private static final String ERR_ENTITY = "entity";

    static {
        boolean available = false;
        try {
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            available = true;
        } catch (final ClassNotFoundException ignored) {
            // ModelEngine not installed
        }
        AVAILABLE = available;
    }

    private ModelEngineIntegration() {
    }

    // -------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------

    /**
     * @return {@code true} if ModelEngine is installed and its API classes are resolvable
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Spawn a ModelEngine model on a {@link Entity} with the given scale.
     * <p>
     * The config format is {@code <modelId>:<size>} — this method receives
     * the parsed model id and size separately.
     * <p>
     * Equivalent to:
     * <pre>{@code
     * ModelEngineAPI.getOrCreateModeledEntity(entity)
     *     .addModel(ModelEngineAPI.createActiveModel(modelId), false);
     * }</pre>
     *
     * @param entity  the Bukkit entity to attach the model to
     * @param modelId the model blueprint id (e.g. {@code "iron_crystal"})
     * @param size    model scale multiplier (1.0 = default). If &le; 0 or exactly 1.0 the scale is not changed.
     */
    public static void showModel(final Entity entity, final String modelId, final double size) {
        if (!AVAILABLE) return;
        Objects.requireNonNull(entity, ERR_ENTITY);
        Objects.requireNonNull(modelId, "modelId");

        final ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(entity);
        final ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
        // Must attach the model to the entity BEFORE setting properties — some
        // ActiveModel implementations are not fully initialised until they are
        // registered with a ModeledEntity.
        modeledEntity.addModel(activeModel, false);
        // Apply configuration after the model is safely attached.
        if (size > 0.0D && Double.compare(size, 1.0D) != 0) {
            activeModel.setScale(size);
        }
        activeModel.setOnFire(false);
        activeModel.setRenderFire(false);
        activeModel.setGlowing(false);
    }

    /**
     * Spawn a ModelEngine model on a {@link Entity} with default scale (1.0).
     *
     * @param entity  the Bukkit entity to attach the model to
     * @param modelId the model blueprint id (e.g. {@code "iron_crystal"})
     */
    public static void showModel(final Entity entity, final String modelId) {
        showModel(entity, modelId, 1.0D);
    }

    /**
     * Remove <b>all</b> ModelEngine models from an entity and destroy its
     * {@code ModeledEntity} wrapper.
     *
     * @param entity the Bukkit entity to clear
     */
    public static void removeModel(final Entity entity) {
        if (!AVAILABLE) return;
        Objects.requireNonNull(entity, ERR_ENTITY);

        final ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(entity);
        modeledEntity.destroy();
    }

    /**
     * Play a named animation on the model attached to an entity.
     * <p>
     * Equivalent to:
     * <pre>{@code
     * ModelEngineAPI.getOrCreateModeledEntity(entity)
     *     .getModel(modelId)
     *     .ifPresent(model -> model.getAnimationHandler()
     *         .playAnimation(animationName, lerpIn, lerpOut, speed, true));
     * }</pre>
     *
     * @param entity        the Bukkit entity whose model should animate
     * @param modelId       the model blueprint id
     * @param animationName the animation id
     * @param lerpIn        lerp-in duration in seconds
     * @param lerpOut       lerp-out duration in seconds
     * @param speed         speed multiplier (1.0 = default)
     */
    public static void playAnimation(final Entity entity, final String modelId,
                                     final String animationName,
                                     final double lerpIn, final double lerpOut,
                                     final double speed) {
        if (!AVAILABLE) return;
        Objects.requireNonNull(entity, ERR_ENTITY);
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(animationName, "animationName");

        final ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(entity);
        final Optional<ActiveModel> modelOpt = modeledEntity.getModel(modelId);
        modelOpt.ifPresent(activeModel ->
                activeModel.getAnimationHandler()
                        .playAnimation(animationName, lerpIn, lerpOut, speed, true)
        );
    }
}
