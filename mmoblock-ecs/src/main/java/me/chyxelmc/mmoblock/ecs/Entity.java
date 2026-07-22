package me.chyxelmc.mmoblock.ecs;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight wrapper around a {@link UUID} representing a single ECS entity.
 *
 * <p>Entities are created via {@link EntityManager#createEntity()} or directly
 * using {@link #create()}. Components are attached and queried through the
 * {@link EntityManager} using the entity's UUID.</p>
 */
public record Entity(UUID uniqueId) {

    /**
     * Create a new entity with a random UUID.
     */
    public static Entity create() {
        return new Entity(UUID.randomUUID());
    }

    /**
     * Create an entity wrapping the given UUID.
     */
    public static Entity of(final UUID uuid) {
        return new Entity(Objects.requireNonNull(uuid, "uuid"));
    }

    /**
     * Returns the underlying UUID of this entity.
     */
    public UUID uniqueId() {
        return uniqueId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity entity)) return false;
        return uniqueId.equals(entity.uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }

    @Override
    public String toString() {
        return "Entity{" + uniqueId + '}';
    }
}
