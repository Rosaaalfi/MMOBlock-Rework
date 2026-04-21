package me.chyxelmc.mmoblock.nmsloader.ecs.systems;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.SystemBase;
import java.util.function.BiConsumer;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.PositionComponent;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

/**
 * System that spawns NMS interactions for entities that have both Position and
 * Interaction components. It will attempt to spawn on first tick where
 * spawnedInteraction is null.
 */
public final class InteractionSpawnSystem extends SystemBase {

    private final NmsAdapter nmsAdapter;
    // Optional callback invoked when an interaction is successfully spawned by this system.
    private final BiConsumer<java.util.UUID, java.util.UUID> onSpawn;

    public InteractionSpawnSystem(final NmsAdapter nmsAdapter) {
        this(nmsAdapter, null);
    }

    public InteractionSpawnSystem(final NmsAdapter nmsAdapter, final BiConsumer<java.util.UUID, java.util.UUID> onSpawn) {
        super("InteractionSpawnSystem");
        this.nmsAdapter = nmsAdapter;
        this.onSpawn = onSpawn;
    }

    @Override
    public void tick(final EntityManager entityManager, final long tick) {
        final List<UUID> candidates = entityManager.getEntitiesWith(PositionComponent.class, InteractionComponent.class);
        for (final UUID id : candidates) {
            final PositionComponent pos = entityManager.getComponent(id, PositionComponent.class);
            final InteractionComponent inter = entityManager.getComponent(id, InteractionComponent.class);
            if (pos == null || inter == null) continue;

            // If already spawned, skip
            if (inter.spawnedInteraction() != null) continue;

            final org.bukkit.Location location = pos.location();
            final World world = location.getWorld();
            if (world == null) continue;

            final NmsAdapter.SpawnResult result = nmsAdapter.spawnInteraction(world, location, inter.width(), inter.height(), inter.uniqueIdKey(), inter.blockUniqueId());
            if (result.success()) {
                inter.setSpawnedInteraction(result.interactionUniqueId());
                if (this.onSpawn != null) {
                    try {
                        this.onSpawn.accept(inter.blockUniqueId(), result.interactionUniqueId());
                    } catch (final Throwable ignored) {
                    }
                }
            }
        }
    }
}

