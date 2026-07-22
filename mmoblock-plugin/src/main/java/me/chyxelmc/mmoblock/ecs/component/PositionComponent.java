package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.ecs.Component;
import org.bukkit.Location;

public final class PositionComponent implements Component {

    private Location location;

    public PositionComponent(final Location location) {
        this.location = location.clone();
    }

    public Location location() {
        return this.location;
    }

    public void setLocation(final Location location) {
        this.location = location.clone();
    }
}
