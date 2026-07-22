package me.chyxelmc.mmoblock.ecs;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple ECS EntityManager using a hybrid command-pattern.
 *
 * External OOP/Bukkit code submits {@link EcsCommand} instances through
 * {@link #submit(EcsCommand)}. The {@link SystemManager} drains those commands
 * on the ECS tick before systems run. Systems that are already inside the tick
 * loop may mutate components directly through this manager for performance.
 *
 * Entities are identified by UUID and components are stored in a
 * per-entity component map.
 */
public final class EntityManager {

    private final Map<UUID, Map<Class<? extends Component>, Component>> entities = new ConcurrentHashMap<>();
    private final Queue<EcsCommand> pendingCommands = new ConcurrentLinkedQueue<>();

    public UUID createEntity() {
        final UUID id = UUID.randomUUID();
        addEntity(id);
        return id;
    }

    public void submit(final EcsCommand command) {
        if (command != null) {
            this.pendingCommands.add(command);
        }
    }

    void drainCommands() {
        EcsCommand command;
        while ((command = this.pendingCommands.poll()) != null) {
            command.execute(this);
        }
    }

    // ---- Direct writes are allowed inside ECS systems and command execution ----

    /**
     * Register a pre-generated entity UUID.
     *
     * @apiNote External OOP/Bukkit callers should prefer {@link #submit(EcsCommand)}.
     */
    public void addEntity(final UUID id) {
        entities.put(id, new ConcurrentHashMap<>());
    }

    /**
     * Remove an entity and all its components.
     *
     * @apiNote External OOP/Bukkit callers should prefer {@link #submit(EcsCommand)}.
     */
    public void removeEntity(final UUID id) {
        entities.remove(id);
    }

    /**
     * Add a component to an entity.
     *
     * @apiNote External OOP/Bukkit callers should prefer {@link #submit(EcsCommand)}.
     */
    public <T extends Component> void addComponent(final UUID id, final T component) {
        final Map<Class<? extends Component>, Component> comps = entities.computeIfAbsent(
                id, k -> new ConcurrentHashMap<>()
        );
        comps.put(component.getClass(), component);
    }

    /**
     * Remove a component of the specified type from an entity.
     *
     * @apiNote External OOP/Bukkit callers should prefer {@link #submit(EcsCommand)}.
     */
    public <T extends Component> void removeComponent(final UUID id, final Class<T> componentClass) {
        final Map<Class<? extends Component>, Component> comps = entities.get(id);
        if (comps != null) {
            comps.remove(componentClass);
        }
    }

    // ---- Read-only queries (safe for systems to call during tick) ----

    /**
     * Get a component of the specified type from an entity.
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> T getComponent(final UUID id, final Class<T> componentClass) {
        final Map<Class<? extends Component>, Component> comps = entities.get(id);
        if (comps == null) return null;
        return (T) comps.get(componentClass);
    }

    /**
     * Return entities that have ALL the provided component types.
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

    /**
     * Returns an unmodifiable set of all entity UUIDs.
     */
    public Set<UUID> allEntities() {
        return Collections.unmodifiableSet(entities.keySet());
    }

    /**
     * Returns true if the entity exists.
     */
    public boolean hasEntity(final UUID id) {
        return entities.containsKey(id);
    }
}
