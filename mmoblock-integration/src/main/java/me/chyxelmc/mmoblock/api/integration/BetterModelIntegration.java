package me.chyxelmc.mmoblock.api.integration;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Integration layer for <a href="https://github.com/toxicity188/BetterModel">BetterModel v3.x</a>.
 * <p>
 * Uses direct BetterModel API references guarded by a static availability check.
 * If BetterModel is not installed the methods are no-ops and the class loads safely
 * because the JVM resolves class references lazily.
 * </p>
 * <p>
 * Prefers attaching an {@link kr.toxicity.model.api.tracker.EntityTracker} to the block's
 * {@link org.bukkit.entity.Interaction} entity when valid, falling back to a
 * {@link kr.toxicity.model.api.tracker.DummyTracker} at the centered block location if entity is unavailable.
 * </p>
 * <p>
 * Trackers are indexed by the MMOBlock block {@link UUID} so they can be looked up
 * for animation and cleanup.
 * </p>
 * <p>
 * Usage in block config YAML:
 * <pre>{@code
 * modelType:
 *   betterModel:
 *     enabled: true
 *     model: "exampleEntity"
 *     size: 1.0
 *     onSpawn:
 *       name: "idle"
 *     onClick:
 *       name: "example_onClick_animation"
 * }</pre>
 * </p>
 */
public final class BetterModelIntegration {

    private static final boolean AVAILABLE;

    /** Maps MMOBlock block UUID → open Tracker instance (EntityTracker or DummyTracker).
     *  Type is {@code Object} to avoid forcing the JVM to resolve BetterModel API types
     *  at class-load time when BetterModel is not installed. */
    private static final Map<UUID, Object> ACTIVE_TRACKERS = new ConcurrentHashMap<>();

    static {
        boolean available = false;
        try {
            Class.forName("kr.toxicity.model.api.BetterModel");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // BetterModel not installed or incompatible
        }
        AVAILABLE = available;
    }

    private BetterModelIntegration() {
    }

    // -------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------

    /**
     * @return {@code true} if BetterModel is installed and its API classes are resolvable
     */
    public static boolean isAvailable() {
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isBetterModelAvailable();
        }
        return true;
    }

    /**
     * Helper to convert facing direction string ("north", "south", "east", "west") to yaw angle.
     *
     * @param facing the facing direction name
     * @return yaw in degrees
     */
    public static float facingToYaw(final String facing) {
        if (facing == null || facing.isBlank()) return 0.0f;
        return switch (facing.toLowerCase(Locale.ROOT)) {
            case "south" -> 0.0f;
            case "west" -> 90.0f;
            case "north" -> 180.0f;
            case "east" -> 270.0f;
            default -> 0.0f;
        };
    }

    /**
     * Spawn a BetterModel model on a Bukkit {@link Entity} or at a {@link Location}
     * and index it under {@code blockUuid}.
     *
     * @param entity    optional Bukkit entity (e.g. Interaction entity) to attach to
     * @param location  fallback world location to spawn the model at if entity is null/invalid
     * @param modelId   the model blueprint id (e.g. {@code "exampleEntity"})
     * @param blockUuid the MMOBlock block unique id used for later lookups
     * @param size      model scale multiplier (1.0 = default)
     * @return {@code true} if the model was found and spawned, {@code false} if
     *         BetterModel is unavailable, the model id is unknown, or an error occurred
     */
    public static boolean showModel(final Entity entity, final Location location, final String modelId, final UUID blockUuid, final double size) {
        if (!AVAILABLE) return false;
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(blockUuid, "blockUuid");

        // Close any previous tracker for this block (e.g. after config reload)
        final Object previous = ACTIVE_TRACKERS.remove(blockUuid);
        if (previous != null) {
            try {
                ((kr.toxicity.model.api.tracker.Tracker) previous).close();
            } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }      }

        final var modelOpt = kr.toxicity.model.api.BetterModel.model(modelId);
        if (modelOpt.isEmpty()) {
            MMOBlockLogger.warning("[BetterModel] Model ID '" + modelId + "' not found in BetterModel registry.");
            return false;
        }

        try {
            final Object tracker;
            if (entity != null && entity.isValid()) {
                tracker = modelOpt.get().create(
                        kr.toxicity.model.api.bukkit.platform.BukkitAdapter.adapt(entity),
                        kr.toxicity.model.api.tracker.TrackerModifier.DEFAULT,
                        t -> {
                            if (size > 0.0D && Double.compare(size, 1.0D) != 0) {
                                t.scaler(kr.toxicity.model.api.tracker.ModelScaler.value((float) size));
                            }
                        }
                );
            } else {
                Objects.requireNonNull(location, "location");
                tracker = modelOpt.get().create(
                        kr.toxicity.model.api.bukkit.platform.BukkitAdapter.adapt(location),
                        kr.toxicity.model.api.tracker.TrackerModifier.DEFAULT,
                        t -> {
                            if (size > 0.0D && Double.compare(size, 1.0D) != 0) {
                                t.scaler(kr.toxicity.model.api.tracker.ModelScaler.value((float) size));
                            }
                        }
                );
            }
            ACTIVE_TRACKERS.put(blockUuid, tracker);
            return true;
        } catch (final Exception ex) {
            MMOBlockLogger.warning("[BetterModel] Failed to create tracker for model '" + modelId + "' on block " + blockUuid + ": " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Fallback overload when entity is not provided.
     */
    public static boolean showModel(final Location location, final String modelId, final UUID blockUuid, final double size) {
        return showModel(null, location, modelId, blockUuid, size);
    }

    /**
     * Remove (close) the BetterModel {@link Tracker} associated with a block UUID.
     *
     * @param blockUuid the MMOBlock block unique id
     */
    public static void removeModel(final UUID blockUuid) {
        if (!AVAILABLE) return;
        if (blockUuid == null) return;

        final Object tracker = ACTIVE_TRACKERS.remove(blockUuid);
        if (tracker != null) {
            try {
                ((kr.toxicity.model.api.tracker.Tracker) tracker).close();
            } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }      }
    }

    /**
     * Play a named animation on the model associated with a block UUID.
     *
     * @param blockUuid     the MMOBlock block unique id
     * @param animationName the animation name
     * @return {@code true} if the animation was played, {@code false} if
     *         BetterModel is unavailable, the block has no active tracker,
     *         or the animation name does not exist on the loaded model
     */
    public static boolean playAnimation(final UUID blockUuid, final String animationName) {
        if (!AVAILABLE) return false;
        if (blockUuid == null || animationName == null || animationName.isBlank()) return false;

        final Object tracker = ACTIVE_TRACKERS.get(blockUuid);
        if (tracker == null) {
            return false;
        }
        try {
            return ((kr.toxicity.model.api.tracker.Tracker) tracker).animate(animationName, kr.toxicity.model.api.animation.AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        } catch (final Exception ex) {
            MMOBlockLogger.warning("[BetterModel] Exception playing animation '" + animationName + "' for block " + blockUuid + ": " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Close and remove every tracked BetterModel model. Intended for plugin
     * shutdown so no orphaned display entities remain in the world.
     */
    public static void removeAll() {
        if (!AVAILABLE) return;
        for (final Object tracker : ACTIVE_TRACKERS.values()) {
            try {
                ((kr.toxicity.model.api.tracker.Tracker) tracker).close();
            } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }      }
        ACTIVE_TRACKERS.clear();
    }

    /**
     * Returns the number of currently active BetterModel trackers managed by
     * this integration layer. Useful for debugging or metrics.
     */
    public static int activeTrackerCount() {
        return ACTIVE_TRACKERS.size();
    }
}
