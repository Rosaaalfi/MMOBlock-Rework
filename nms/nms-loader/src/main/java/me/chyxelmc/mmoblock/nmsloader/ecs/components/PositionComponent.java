package me.chyxelmc.mmoblock.nmsloader.ecs.components;

import me.chyxelmc.mmoblock.nmsloader.ecs.Component;
import org.bukkit.Location;

/**
 * Stores a Bukkit Location for an entity.
 */
public final class PositionComponent implements Component {

    private Location location;

    public PositionComponent(final Location location) {
        this.location = location;
    }

    public Location location() {
        return location;
    }

    public void setLocation(final Location location) {
        this.location = location;
    }
}

