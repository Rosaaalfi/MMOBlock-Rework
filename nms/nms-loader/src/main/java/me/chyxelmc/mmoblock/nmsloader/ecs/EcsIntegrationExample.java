package me.chyxelmc.mmoblock.nmsloader.ecs;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.systems.HologramSystem;
import me.chyxelmc.mmoblock.nmsloader.ecs.systems.InteractionRemoveSystem;
import me.chyxelmc.mmoblock.nmsloader.ecs.systems.InteractionSpawnSystem;

import java.util.UUID;

/**
 * Small example showing how to wire up the ECS pieces with an NMS adapter.
 * This is intended as documentation / example usage and not a runtime component.
 */
public final class EcsIntegrationExample {

    private EcsIntegrationExample() {}

    /**
     * Create and wire managers/systems and return the SystemManager. The caller
     * is responsible for ticking the returned SystemManager regularly (e.g. each
     * server tick).
     */
    public static SystemManager createForAdapter(final NmsAdapter adapter, final EntityManager outEntityManager) {
        return createForAdapter(adapter, outEntityManager, null);
    }

    public static SystemManager createForAdapter(
            final NmsAdapter adapter,
            final EntityManager outEntityManager,
            final java.util.function.BiConsumer<java.util.UUID, java.util.UUID> onInteractionSpawned
    ) {
        final SystemManager systemManager = new SystemManager();
        final InteractionSpawnSystem interactionSystem = new InteractionSpawnSystem(adapter, onInteractionSpawned);
        final InteractionRemoveSystem removeSystem = new InteractionRemoveSystem(adapter);
        final HologramSystem hologramSystem = new HologramSystem(adapter);
        systemManager.register(interactionSystem);
        systemManager.register(removeSystem);
        systemManager.register(hologramSystem);
        return systemManager;
    }

    // helper to create an EntityManager pre-filled (example)
    public static EntityManager createEntityManager() {
        return new EntityManager();
    }

    /**
     * Perform cleanup of any spawned NMS entities / packet holograms for
     * entities currently present in the provided EntityManager. This is
     * intended to be called on plugin shutdown to avoid leaving orphaned
     * NMS entities or holograms.
     */
    public static void cleanupAll(final EntityManager entityManager, final NmsAdapter adapter) {
        for (final java.util.UUID id : entityManager.allEntities()) {
            final me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent inter =
                    entityManager.getComponent(id, me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent.class);
            final me.chyxelmc.mmoblock.nmsloader.ecs.components.PositionComponent pos =
                    entityManager.getComponent(id, me.chyxelmc.mmoblock.nmsloader.ecs.components.PositionComponent.class);
            if (inter != null && inter.spawnedInteraction() != null && pos != null) {
                final org.bukkit.World world = pos.location().getWorld();
                if (world != null) {
                    try {
                        adapter.removeInteraction(world, inter.spawnedInteraction());
                    } catch (final RuntimeException ignored) {
                    }
                }
            }

            final me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent holo =
                    entityManager.getComponent(id, me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent.class);
            if (holo != null && holo.baseLocation().getWorld() != null) {
                final org.bukkit.World world = holo.baseLocation().getWorld();
                for (final org.bukkit.entity.Player player : world.getPlayers()) {
                    try {
                        adapter.removePacketHologram(player, holo.hologramUniqueId());
                    } catch (final RuntimeException ignored) {
                    }
                }
            }
        }
    }
}

