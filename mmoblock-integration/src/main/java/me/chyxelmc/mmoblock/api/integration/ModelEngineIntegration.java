package me.chyxelmc.mmoblock.api.integration;

import java.util.Objects;
import java.util.Optional;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import org.bukkit.entity.Entity;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
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
    private static final String AVAILABILITY_FAILURE;
    private static final String ERR_ENTITY = "entity";

    static {
        boolean available = false;
        String failure = "";
        try {
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError exception) {
            failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        AVAILABLE = available;
        AVAILABILITY_FAILURE = failure;
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
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isModelEngineAvailable();
        }
        return true;
    }

    public static String availabilityFailure() {
        if (!AVAILABLE) {
            return AVAILABILITY_FAILURE.isBlank() ? "ModelEngine API classes are unavailable" : AVAILABILITY_FAILURE;
        }
        if (DependencyChecker.isInitialized() && !DependencyChecker.isModelEngineAvailable()) {
            return "ModelEngine plugin is missing or disabled";
        }
        return "";
    }

    public record ModelApplyResult(boolean applied, String detail) {
    }

    public static ModelApplyResult applyModel(final Entity entity, final String modelId, final double size) {
        if (!isAvailable()) {
            return new ModelApplyResult(false, availabilityFailure());
        }
        Objects.requireNonNull(entity, ERR_ENTITY);
        Objects.requireNonNull(modelId, "modelId");
        final ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(modelId);
        if (blueprint == null) {
            return new ModelApplyResult(false, "blueprint was not found in the ModelEngine registry");
        }
        final ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(entity);
        if (modeledEntity == null || modeledEntity.isDestroyed()) {
            return new ModelApplyResult(false, "ModeledEntity creation failed or returned a destroyed instance");
        }
        final ActiveModel activeModel = ModelEngineAPI.createActiveModel(blueprint);
        if (activeModel == null) {
            return new ModelApplyResult(false, "ActiveModel creation returned null");
        }
        activeModel.setAutoRendererInitialization(false);
        activeModel.setMainHitbox(false);
        activeModel.setHitboxVisible(false);
        activeModel.setCanHurt(false);
        modeledEntity.setBaseEntityVisible(false);
        final Optional<ActiveModel> replacedModel = modeledEntity.addModel(activeModel, false);
        final Optional<ActiveModel> registeredModel = modeledEntity.getModel(modelId);
        if (registeredModel.isEmpty() || registeredModel.get() != activeModel) {
            activeModel.destroy();
            return new ModelApplyResult(false, "ModeledEntity rejected the ActiveModel attachment");
        }
        if (size > 0.0D && Double.compare(size, 1.0D) != 0) {
            activeModel.setScale(size);
        }
        activeModel.setOnFire(false);
        activeModel.setRenderFire(false);
        activeModel.setGlowing(false);
        activeModel.initializeRenderer();
        return new ModelApplyResult(true, "host=" + entity.getUniqueId()
                + ", modeledEntityInitialized=" + modeledEntity.isInitialized()
                + ", replacedExistingModel=" + replacedModel.isPresent());
    }

    /**
     * Spawn a ModelEngine model on a {@link Entity} with the given scale.
     * <p>
     * The config format is {@code <modelId>:<size>} — this method receives
     * the parsed model id and size separately.
     * <p>
     * Equivalent to:
     * <pre>{@code
     * ModelEngineAPI.createModeledEntity(entity)
     *     .addModel(ModelEngineAPI.createActiveModel(blueprint), false);
     * }</pre>
     *
     * @param entity  the Bukkit entity to attach the model to
     * @param modelId the model blueprint id (e.g. {@code "iron_crystal"})
     * @param size    model scale multiplier (1.0 = default). If &le; 0 or exactly 1.0 the scale is not changed.
     * @return {@code true} when the model blueprint exists and is attached
     */
    public static boolean showModel(final Entity entity, final String modelId, final double size) {
        return applyModel(entity, modelId, size).applied();
    }

    /**
     * Spawn a ModelEngine model on a {@link Entity} with default scale (1.0).
     *
     * @param entity  the Bukkit entity to attach the model to
     * @param modelId the model blueprint id (e.g. {@code "iron_crystal"})
     */
    public static boolean showModel(final Entity entity, final String modelId) {
        return showModel(entity, modelId, 1.0D);
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
    public static boolean playAnimation(final Entity entity, final String modelId,
                                     final String animationName,
                                     final double lerpIn, final double lerpOut,
                                     final double speed) {
        if (!isAvailable()) return false;
        Objects.requireNonNull(entity, ERR_ENTITY);
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(animationName, "animationName");

        final ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(entity);
        final Optional<ActiveModel> modelOpt = modeledEntity.getModel(modelId);
        if (modelOpt.isEmpty()) {
            return false;
        }
        final var animationHandler = modelOpt.get().getAnimationHandler();
        animationHandler.forceStopAllAnimations();
        return animationHandler.playAnimation(animationName, lerpIn, lerpOut, speed, true) != null;
    }
}
