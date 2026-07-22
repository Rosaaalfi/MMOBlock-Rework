package me.chyxelmc.mmoblock.runtime.interaction;

import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.ecs.system.LifecycleSystem;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class LegacyInteractionRaytrace {

    private final BlockEcsState ecsState;
    private final BlockConfigLoader blockConfigService;
    private final LifecycleSystem lifecycleSystem;

    public LegacyInteractionRaytrace(
            final BlockEcsState ecsState,
            final BlockConfigLoader blockConfigService,
            final LifecycleSystem lifecycleSystem
    ) {
        this.ecsState = ecsState;
        this.blockConfigService = blockConfigService;
        this.lifecycleSystem = lifecycleSystem;
    }

    public PlacedBlockModel findHit(final Player player, final double maxDistance) {
        final Hit hit = findNearestHit(player, maxDistance);
        return hit == null ? null : hit.block();
    }

    private Hit findNearestHit(final Player player, final double maxDistance) {
        final World world = player.getWorld();
        final Vector origin = player.getEyeLocation().toVector();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final double maxDistanceSquared = maxDistance * maxDistance;

        Hit nearest = null;
        for (final PlacedBlockModel block : this.ecsState.blocks()) {
            if (!world.getName().equals(block.world())) {
                continue;
            }
            if (!this.lifecycleSystem.isActive(block)) {
                continue;
            }

            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                continue;
            }

            final Location center = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
            if (center.distanceSquared(player.getLocation()) > maxDistanceSquared + 4.0D) {
                continue;
            }

            final BoundingBox box = interactionBoundingBox(center, definition);
            final RayTraceResult result = box.rayTrace(origin, direction, maxDistance);
            if (result == null || result.getHitPosition() == null) {
                continue;
            }
            final double distance = result.getHitPosition().distance(origin);
            if (nearest == null || distance < nearest.distance()) {
                nearest = new Hit(block, distance);
            }
        }
        return nearest;
    }

    private BoundingBox interactionBoundingBox(final Location center, final BlockDefinitionModel definition) {
        final double halfWidth = Math.max(0.05D, definition.hitboxWidth()) / 2.0D;
        final double height = Math.max(0.05D, definition.hitboxHeight());
        final double y = center.getY();
        return new BoundingBox(
                center.getX() - halfWidth,
                y,
                center.getZ() - halfWidth,
                center.getX() + halfWidth,
                y + height,
                center.getZ() + halfWidth
        );
    }

    private record Hit(PlacedBlockModel block, double distance) {
    }
}
