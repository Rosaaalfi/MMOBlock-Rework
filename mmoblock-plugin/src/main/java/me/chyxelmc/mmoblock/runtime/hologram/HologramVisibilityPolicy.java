package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

final class HologramVisibilityPolicy {

    boolean hasDisplayFacing(final BlockDefinitionModel definition) {
        if (definition == null) {
            return false;
        }
        final String type = definition.displayFacingType();
        return type != null
                && !type.isBlank()
                && definition.displayFacingDistance() > 0.0D;
    }

    boolean hasDetectRange(final BlockDefinitionModel definition) {
        if (definition == null) {
            return false;
        }
        // detectRange works independently of displayFacing type.
        // When detectRange > 0, it controls visibility range regardless of
        // whether the hologram faces the player (distance > 0) or not.
        return definition.displayFacingDetectRange() > 0.0D;
    }

    double rangeSquared(final BlockDefinitionModel definition) {
        if (hasDetectRange(definition)) {
            final double range = Math.max(0.0D, definition.displayFacingDetectRange());
            return range * range;
        }
        return HologramRuntimeService.PACKET_SYNC_RADIUS_SQUARED;
    }

    Location visibleLocation(final HologramPacketSession session, final Player viewer, final World world) {
        if (hasDisplayFacing(session.definition())) {
            if (!isViewerInRange(viewer, world, session.baseLocation(), rangeSquared(session.definition()))) {
                return null;
            }
            return resolveDisplayFacingLocation(session.baseLocation(), viewer, session.definition());
        }
        if (!isViewerInRange(viewer, world, session.baseLocation(), rangeSquared(session.definition()))) {
            return null;
        }
        return session.baseLocation();
    }

    boolean isViewerInRange(final Player viewer, final World world, final HologramPacketSession session) {
        return isViewerInRange(viewer, world, session.baseLocation(), rangeSquared(session.definition()));
    }

    private static boolean isViewerInRange(
            final Player viewer,
            final World world,
            final Location baseLocation,
            final double rangeSquared
    ) {
        return viewer.getWorld().equals(world)
                && viewer.getLocation().distanceSquared(baseLocation) <= rangeSquared;
    }

    private static Location resolveDisplayFacingLocation(
            final Location baseLocation,
            final Player player,
            final BlockDefinitionModel definition
    ) {
        final World world = baseLocation.getWorld();
        if (world == null) {
            return baseLocation.clone();
        }

        final double baseX = baseLocation.getX();
        final double baseZ = baseLocation.getZ();
        final Location playerLocation = player.getLocation();
        final double dx = playerLocation.getX() - baseX;
        final double dz = playerLocation.getZ() - baseZ;

        double angle = Math.toDegrees(Math.atan2(dx, dz));
        if (angle < 0) {
            angle += 360.0D;
        }

        final boolean intercardinal = "intercardinal".equalsIgnoreCase(definition.displayFacingType());
        final double sector = intercardinal ? 45.0D : 90.0D;
        final double snappedAngle = Math.round(angle / sector) * sector;
        final double angleRad = Math.toRadians(snappedAngle);
        final double distance = definition.displayFacingDistance();

        final Location result = baseLocation.clone();
        result.setX(baseX + distance * Math.sin(angleRad));
        result.setZ(baseZ + distance * Math.cos(angleRad));
        result.setYaw((float) ((snappedAngle + 180.0D) % 360.0D));
        result.setPitch(0.0F);
        return result;
    }
}
