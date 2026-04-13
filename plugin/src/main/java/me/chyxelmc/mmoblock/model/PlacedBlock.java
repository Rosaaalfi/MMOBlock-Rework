package me.chyxelmc.mmoblock.model;

import org.bukkit.Location;

import java.util.UUID;

public final class PlacedBlock {

    private final UUID uniqueId;
    private final String type;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final String facing;
    private String status;
    private UUID interactionEntityId;
    private Long respawnAt;

    public PlacedBlock(
        final UUID uniqueId,
        final String type,
        final String world,
        final double x,
        final double y,
        final double z,
        final String facing,
        final String status
    ) {
        this.uniqueId = uniqueId;
        this.type = type;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.status = status;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public String type() {
        return type;
    }

    public String world() {
        return world;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public String facing() {
        return facing;
    }

    public String status() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public UUID interactionEntityId() {
        return interactionEntityId;
    }

    public void setInteractionEntityId(final UUID interactionEntityId) {
        this.interactionEntityId = interactionEntityId;
    }

    public Long respawnAt() {
        return this.respawnAt;
    }

    public void setRespawnAt(final Long respawnAt) {
        this.respawnAt = respawnAt;
    }

    public boolean matches(final String type, final String worldName, final double x, final double y, final double z) {
        return this.type.equalsIgnoreCase(type)
            && this.world.equals(worldName)
            && almostEquals(this.x, x)
            && almostEquals(this.y, y)
            && almostEquals(this.z, z);
    }

    public Location toLocation() {
        return new Location(null, this.x, this.y, this.z);
    }

    private boolean almostEquals(final double a, final double b) {
        return Math.abs(a - b) < 0.000001D;
    }
}

