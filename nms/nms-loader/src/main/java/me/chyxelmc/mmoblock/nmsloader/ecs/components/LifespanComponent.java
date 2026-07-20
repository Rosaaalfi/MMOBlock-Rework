package me.chyxelmc.mmoblock.nmsloader.ecs.components;

import me.chyxelmc.mmoblock.nmsloader.ecs.Component;

/**
 * Component that provides a remaining tick lifespan for an entity. The value
 * is decremented by systems each tick; when it reaches zero the entity is
 * considered expired and may be removed.
 */
public final class LifespanComponent implements Component {

    private long remainingTicks;

    public LifespanComponent(final long initialTicks) {
        this.remainingTicks = initialTicks;
    }

    public long remainingTicks() {
        return remainingTicks;
    }

    public void decrement() {
        if (remainingTicks > 0) remainingTicks--;
    }

    public boolean expired() {
        return remainingTicks <= 0;
    }
}

