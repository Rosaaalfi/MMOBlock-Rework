package me.chyxelmc.mmoblock.ecs;

import java.util.UUID;

/**
 * Represents a mutation operation that a System wants to perform.
 * Systems return a list of Operations from {@link SystemBase#tick(EntityManager, long)}.
 * The {@link SystemManager} collects and executes these operations after all systems have ticked,
 * decoupling logic from side effects (command-pattern).
 */
public sealed interface Operation {

    /**
     * Operation to create a new entity with the given UUID.
     */
    record CreateEntity(UUID entityId) implements Operation {}

    /**
     * Operation to remove an entity and all its components.
     */
    record RemoveEntity(UUID entityId) implements Operation {}

    /**
     * Operation to add a component to an entity.
     */
    record AddComponent(UUID entityId, Component component) implements Operation {}

    /**
     * Operation to remove a component of the specified type from an entity.
     */
    record RemoveComponent(UUID entityId, Class<? extends Component> componentType) implements Operation {}
}
