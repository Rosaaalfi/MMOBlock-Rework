package me.chyxelmc.mmoblock.nmsloader.ecs;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple ECS EntityManager. Entities are identified by UUID and components are
 * stored in a per-entity component map.
 */
public final class EntityManager {

    private final Map<UUID, Map<Class<? extends Component>, Component>> entities = new ConcurrentHashMap<>();

    public UUID createEntity() {
        final UUID id = UUID.randomUUID();
        entities.put(id, new ConcurrentHashMap<>());
        return id;
    }

    public boolean removeEntity(final UUID id) {
        return entities.remove(id) != null;
    }

    public <T extends Component> void addComponent(final UUID id, final T component) {
        final Map<Class<? extends Component>, Component> comps = entities.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        comps.put(component.getClass(), component);
    }

    public <T extends Component> T getComponent(final UUID id, final Class<T> componentClass) {
        final Map<Class<? extends Component>, Component> comps = entities.get(id);
        if (comps == null) return null;
        @SuppressWarnings("unchecked")
        final T comp = (T) comps.get(componentClass);
        return comp;
    }

    public <T extends Component> boolean removeComponent(final UUID id, final Class<T> componentClass) {
        final Map<Class<? extends Component>, Component> comps = entities.get(id);
        if (comps == null) return false;
        return comps.remove(componentClass) != null;
    }

    /**
     * Return entities that have all provided component types.
     */
    @SafeVarargs
    public final List<UUID> getEntitiesWith(final Class<? extends Component>... componentTypes) {
        final List<UUID> out = new ArrayList<>();
        for (final Map.Entry<UUID, Map<Class<? extends Component>, Component>> entry : entities.entrySet()) {
            final Map<Class<? extends Component>, Component> comps = entry.getValue();
            boolean ok = true;
            for (final Class<? extends Component> type : componentTypes) {
                if (!comps.containsKey(type)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(entry.getKey());
        }
        return out;
    }

    public Set<UUID> allEntities() {
        return Collections.unmodifiableSet(entities.keySet());
    }
}

