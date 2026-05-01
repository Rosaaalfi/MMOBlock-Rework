package me.chyxelmc.mmoblock.nmsloader.ecs.components;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.Component;
import org.bukkit.Location;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Component representing a packet-backed hologram. Systems will call the
 * configured NMS adapter to upsert/remove the hologram for players.
 */
public final class HologramComponent implements Component {

    private final UUID hologramUniqueId;
    private Location baseLocation;
    private List<NmsAdapter.HologramLine> lines;
    private long revision = 1L;
    private boolean removed = false;

    public HologramComponent(final UUID hologramUniqueId, final Location baseLocation, final List<NmsAdapter.HologramLine> lines) {
        this.hologramUniqueId = hologramUniqueId;
        this.baseLocation = baseLocation.clone();
        this.lines = List.copyOf(lines);
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

    public void update(final Location baseLocation, final List<NmsAdapter.HologramLine> lines) {
        final List<NmsAdapter.HologramLine> copiedLines = List.copyOf(lines);
        if (sameLocation(this.baseLocation, baseLocation) && Objects.equals(this.lines, copiedLines)) {
            return;
        }
        this.baseLocation = baseLocation.clone();
        this.lines = copiedLines;
        this.revision++;
    }

    public long revision() {
        return revision;
    }

    public boolean removed() {
        return removed;
    }

    public void markRemoved() {
        this.removed = true;
    }

    private static boolean sameLocation(final Location a, final Location b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        final String worldA = a.getWorld() == null ? null : a.getWorld().getName();
        final String worldB = b.getWorld() == null ? null : b.getWorld().getName();
        return Objects.equals(worldA, worldB)
                && Double.compare(a.getX(), b.getX()) == 0
                && Double.compare(a.getY(), b.getY()) == 0
                && Double.compare(a.getZ(), b.getZ()) == 0;
    }
}
