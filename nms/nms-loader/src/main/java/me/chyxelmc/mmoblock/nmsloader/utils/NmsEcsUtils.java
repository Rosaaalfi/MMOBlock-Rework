package me.chyxelmc.mmoblock.nmsloader.utils;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.PositionComponent;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.UUID;

/**
 * Utility helpers to create common ECS entity setups used by the plugin.
 */
public final class NmsEcsUtils {

    private NmsEcsUtils() {
    }

    /**
     * Create an ECS entity that will spawn an NMS interaction at the provided
     * location. The system that performs actual spawning should be registered
     * separately (for example, InteractionSpawnSystem).
     */
    public static UUID createInteractionEntity(
            final EntityManager entityManager,
            final Location location,
            final float width,
            final float height,
            final NamespacedKey uniqueIdKey,
            final UUID blockUniqueId
    ) {
        final UUID id = entityManager.createEntity();
        entityManager.addComponent(id, new PositionComponent(location));
        entityManager.addComponent(id, new InteractionComponent(width, height, uniqueIdKey, blockUniqueId));
        return id;
    }

    /**
     * Create a hologram entity that will be upserted/removed by the
     * HologramSystem. Returns the created entity id.
     */
    public static UUID createHologramEntity(
            final EntityManager entityManager,
            final UUID hologramUniqueId,
            final Location baseLocation,
            final java.util.List<NmsAdapter.HologramLine> lines
    ) {
        final UUID id = entityManager.createEntity();
        entityManager.addComponent(id, new me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent(hologramUniqueId, baseLocation, lines));
        return id;
    }

    /**
     * Convenience helper to attach a LifespanComponent to an existing entity.
     */
    public static void attachLifespan(final EntityManager entityManager, final UUID entityId, final long ticks) {
        entityManager.addComponent(entityId, new me.chyxelmc.mmoblock.nmsloader.ecs.components.LifespanComponent(ticks));
    }
}

