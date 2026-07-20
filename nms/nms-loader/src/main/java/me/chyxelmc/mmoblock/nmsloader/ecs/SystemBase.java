package me.chyxelmc.mmoblock.nmsloader.ecs;

/**
 * Base class for ECS systems.
 */
public abstract class SystemBase {

    private final String name;

    protected SystemBase(final String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /**
     * Called each tick by the SystemManager.
     */
    public abstract void tick(final EntityManager entityManager, final long tick);
}

