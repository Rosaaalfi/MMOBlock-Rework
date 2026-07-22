package me.chyxelmc.mmoblock.ecs;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages system registration and ticking.
 *
 * On each tick, queued external commands are drained first, then registered
 * systems run in order. Systems may mutate ECS state directly because they
 * execute inside the controlled tick loop.
 */
public final class SystemManager {

    private final List<SystemBase> systems = new CopyOnWriteArrayList<>();

    /**
     * Register a system to be ticked each cycle.
     */
    public void register(final SystemBase system) {
        if (!systems.contains(system)) {
            systems.add(system);
        }
    }

    /**
     * Unregister a previously registered system.
     */
    public void unregister(final SystemBase system) {
        systems.remove(system);
    }

    /**
     * Tick all registered systems and execute their collected operations.
     *
     * @param entityManager the entity manager to query and mutate
     * @param tick          the current tick number
     */
    public void tick(final EntityManager entityManager, final long tick) {
        entityManager.drainCommands();
        for (final SystemBase system : systems) {
            system.tick(entityManager, tick);
        }
    }

    /**
     * Get a registered system by its type.
     */
    @SuppressWarnings("unchecked")
    public <T extends SystemBase> T getSystem(final Class<T> type) {
        for (final SystemBase s : systems) {
            if (type.isInstance(s)) return (T) s;
        }
        return null;
    }
}
