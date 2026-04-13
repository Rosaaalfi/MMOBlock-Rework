package me.chyxelmc.mmoblock.model;

import org.bukkit.Location;

import java.util.UUID;

public final class PlacedBlock {

    private final UUID uniqueId;
    private final String type;
    private final String world;
    private final double originX;
    private final double originY;
    private final double originZ;
    private double x;
    private double y;
    private double z;
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
        this(uniqueId, type, world, x, y, z, x, y, z, facing, status);
    }

    public PlacedBlock(
        final UUID uniqueId,
        final String type,
        final String world,
        final double originX,
        final double originY,
        final double originZ,
        final double x,
        final double y,
        final double z,
        final String facing,
        final String status
    ) {
        this.uniqueId = uniqueId;
        this.type = type;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
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

    public double originX() {
        return this.originX;
    }

    public double originY() {
        return this.originY;
    }

    public double originZ() {
        return this.originZ;
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

    public void setCurrentLocation(final double x, final double y, final double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void resetCurrentLocationToOrigin() {
        this.x = this.originX;
        this.y = this.originY;
        this.z = this.originZ;
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

