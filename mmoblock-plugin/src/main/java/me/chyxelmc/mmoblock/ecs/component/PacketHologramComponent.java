package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.ecs.Component;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.Location;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PacketHologramComponent implements Component {

    private final UUID hologramUniqueId;
    private Location baseLocation;
    private List<NmsAdapter.HologramLine> lines;
    private long revision = 1L;
    private boolean removed;

    public PacketHologramComponent(
            final UUID hologramUniqueId,
            final Location baseLocation,
            final List<NmsAdapter.HologramLine> lines
    ) {
        this.hologramUniqueId = hologramUniqueId;
        this.baseLocation = baseLocation.clone();
        this.lines = List.copyOf(lines);
    }

    public UUID hologramUniqueId() {
        return this.hologramUniqueId;
    }

    public Location baseLocation() {
        return this.baseLocation;
    }

    public List<NmsAdapter.HologramLine> lines() {
        return this.lines;
    }

    public long revision() {
        return this.revision;
    }

    public boolean removed() {
        return this.removed;
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

    public void markRemoved() {
        this.removed = true;
        this.revision++;
    }

    private static boolean sameLocation(final Location first, final Location second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        final String firstWorld = first.getWorld() == null ? null : first.getWorld().getName();
        final String secondWorld = second.getWorld() == null ? null : second.getWorld().getName();
        return Objects.equals(firstWorld, secondWorld)
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0;
    }
}
