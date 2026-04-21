package me.chyxelmc.mmoblock.nmsloader.ecs.components;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.Component;
import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * Component representing a packet-backed hologram. Systems will call the
 * configured NMS adapter to upsert/remove the hologram for players.
 */
public final class HologramComponent implements Component {

    private final UUID hologramUniqueId;
    private final Location baseLocation;
    private final List<NmsAdapter.HologramLine> lines;
    private boolean removed = false;

    public HologramComponent(final UUID hologramUniqueId, final Location baseLocation, final List<NmsAdapter.HologramLine> lines) {
        this.hologramUniqueId = hologramUniqueId;
        this.baseLocation = baseLocation;
        this.lines = lines;
    }

    public UUID hologramUniqueId() {
        return hologramUniqueId;
    }

    public Location baseLocation() {
        return baseLocation;
    }

    public List<NmsAdapter.HologramLine> lines() {
        return lines;
    }

    public boolean removed() {
        return removed;
    }

    public void markRemoved() {
        this.removed = true;
    }
}

