package me.chyxelmc.mmoblock.ecs;

/**
 * Base class for ECS systems.
 *
 * External OOP/Bukkit code should submit commands to {@link EntityManager};
 * internal ECS systems are already inside the tick loop and may mutate
 * components directly.
 */
public abstract class SystemBase {

    private final String name;

    protected SystemBase(final String name) {
        this.name = name;
    }

    /**
     * A human-readable name for this system (used for debugging/logging).
     */
    public String name() {
        return name;
    }

    /**
     * Called each tick by the {@link SystemManager}.
     *
     * @param entityManager the read-only query interface for ECS data
     * @param tick          the current tick number
     */
    public abstract void tick(EntityManager entityManager, long tick);
}
