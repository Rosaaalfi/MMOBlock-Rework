package me.chyxelmc.mmoblock.nmsloader.ecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple system manager that ticks registered systems.
 */
public final class SystemManager {

    private final List<SystemBase> systems = new ArrayList<>();

    public void register(final SystemBase system) {
        if (!systems.contains(system)) systems.add(system);
    }

    public void unregister(final SystemBase system) {
        systems.remove(system);
    }

    public void tick(final EntityManager entityManager, final long tick) {
        for (final SystemBase s : systems) {
            s.tick(entityManager, tick);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends SystemBase> T getSystem(final Class<T> type) {
        for (final SystemBase s : systems) {
            if (type.isInstance(s)) return (T) s;
        }
        return null;
    }
}

