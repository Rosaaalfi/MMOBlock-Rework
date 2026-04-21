package me.chyxelmc.mmoblock.nmsloader.ecs.systems;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.SystemBase;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.LifespanComponent;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.PositionComponent;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

/**
 * System that removes spawned NMS interactions when the entity's lifespan
 * expires (if a LifespanComponent is present). It will also remove any
 * associated packet holograms if present.
 */
public final class InteractionRemoveSystem extends SystemBase {

    private final NmsAdapter adapter;

    public InteractionRemoveSystem(final NmsAdapter adapter) {
        super("InteractionRemoveSystem");
        this.adapter = adapter;
    }

    @Override
    public void tick(final EntityManager entityManager, final long tick) {
        final List<UUID> candidates = entityManager.getEntitiesWith(InteractionComponent.class, LifespanComponent.class);
        for (final UUID id : candidates) {
            final InteractionComponent inter = entityManager.getComponent(id, InteractionComponent.class);
            final LifespanComponent life = entityManager.getComponent(id, LifespanComponent.class);
            final PositionComponent pos = entityManager.getComponent(id, PositionComponent.class);
            if (inter == null || life == null) continue;

            // decrement lifespan
            life.decrement();

            if (!life.expired()) continue;

            // lifespan expired: remove spawned interaction if present
            final UUID spawned = inter.spawnedInteraction();
            if (spawned != null) {
                try {
                    if (pos != null && pos.location().getWorld() != null) {
                        final World world = pos.location().getWorld();
                        adapter.removeInteraction(world, spawned);
                    } else {
                        // World unknown: still attempt removal via default world? skip
                        // The adapter API requires a World, so if not available we skip.
                    }
                } catch (final RuntimeException ex) {
                    // swallow to avoid stopping tick loop
                }
                inter.setSpawnedInteraction(null);
            }

            // remove any hologram components attached to this entity
            final HologramComponent hologram = entityManager.getComponent(id, HologramComponent.class);
            if (hologram != null) {
                hologram.markRemoved();
            }

            // finally remove the entity entirely from the ECS
            entityManager.removeEntity(id);
        }
    }
}

