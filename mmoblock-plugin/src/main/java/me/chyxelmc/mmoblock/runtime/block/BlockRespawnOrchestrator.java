package me.chyxelmc.mmoblock.runtime.block;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.block.RespawnScheduler;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.runtime.block.RandomLocationContext;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;

public final class BlockRespawnOrchestrator {

    private static final double DEAD_UPDATE_NEARBY_RADIUS = 16.0D;
    private static final int DEFAULT_VERTICAL_RANGE = 10;

    private final BlockConfigLoader blockConfigService;
    private final PersistenceSystem persistenceSystem;
    private final BlockStateRegistry stateRegistry;
    private final RespawnScheduler respawnSystem;
    private final BlockLifecycleState lifecycleSystem;
    private final BlockVisualSyncService visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final BlockRandomLocationResolver randomLocationResolver;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockEventDispatcher eventDispatcher;
    private final BlockMiningOrchestrator miningOrchestrator;
    private final Map<UUID, RandomLocationContext> nodeRandomLocationContexts;
    private final Predicate<UUID> transientBlockPredicate;
    private final Predicate<UUID> suppressDeadHologramPredicate;
    private final MissingDefinitionCleanup missingDefinitionCleanup;

    public BlockRespawnOrchestrator(
            final BlockConfigLoader blockConfigService,
            final PersistenceSystem persistenceSystem,
            final BlockStateRegistry stateRegistry,
            final RespawnScheduler respawnSystem,
            final BlockLifecycleState lifecycleSystem,
            final BlockVisualSyncService visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService,
            final BlockRandomLocationResolver randomLocationResolver,
            final BlockInteractionOrchestrator interactionOrchestrator,
            final BlockEventDispatcher eventDispatcher,
            final BlockMiningOrchestrator miningOrchestrator,
            final Map<UUID, RandomLocationContext> nodeRandomLocationContexts,
            final Predicate<UUID> transientBlockPredicate,
            final Predicate<UUID> suppressDeadHologramPredicate,
            final MissingDefinitionCleanup missingDefinitionCleanup
    ) {
        this.blockConfigService = blockConfigService;
        this.persistenceSystem = persistenceSystem;
        this.stateRegistry = stateRegistry;
        this.respawnSystem = respawnSystem;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
        this.randomLocationResolver = randomLocationResolver;
        this.interactionOrchestrator = interactionOrchestrator;
        this.eventDispatcher = eventDispatcher;
        this.miningOrchestrator = miningOrchestrator;
        this.nodeRandomLocationContexts = nodeRandomLocationContexts;
        this.transientBlockPredicate = transientBlockPredicate;
        this.suppressDeadHologramPredicate = suppressDeadHologramPredicate;
        this.missingDefinitionCleanup = missingDefinitionCleanup;
    }

    public void showDeadOrRemoveSuppressed(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final long seconds
    ) {
        if (this.suppressDeadHologramPredicate.test(block.uniqueId())) {
            this.hologramRuntimeService.remove(block);
            return;
        }
        this.hologramRuntimeService.showDead(block, definition, seconds);

        // Apply dead block model if configured
        final World world = Bukkit.getServer().getWorld(block.world());
        if (world != null && this.visualSyncSystem.hasDeadBlockModel(definition)) {
            this.visualSyncSystem.applyDeadBlockModel(block, definition, world);
        }
    }

    public void schedule(final PlacedBlockModel block, final World world, final long delayMillis) {
        final long respawnAtMs = System.currentTimeMillis() + delayMillis;
        block.setRespawnAt(respawnAtMs);
        this.respawnSystem.schedule(
                block,
                delayMillis,
                () -> updateDeadHologram(block, world),
                () -> completeRespawn(block, world)
        );
    }

    private void updateDeadHologram(final PlacedBlockModel block, final World world) {
        final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
        if (definition != null
                && this.lifecycleSystem.isRespawning(block)
                && isChunkLoaded(world, block.x(), block.z())
                && this.hologramRuntimeService.hasNearbyPlayers(block, DEAD_UPDATE_NEARBY_RADIUS)
                && !this.suppressDeadHologramPredicate.test(block.uniqueId())) {
            this.hologramRuntimeService.updateDeadRespawnTime(block, definition);
        }
    }

    private void completeRespawn(final PlacedBlockModel block, final World world) {
        final BlockDefinitionModel latestDefinition = this.blockConfigService.findBlock(block.type());
        if (latestDefinition == null) {
            this.missingDefinitionCleanup.cleanup(block);
            return;
        }

        // Check whether a dead block model is configured before we potentially
        // change the block's position (so we can clear it at the old position)
        final boolean hadDeadModel = this.visualSyncSystem.hasDeadBlockModel(latestDefinition);

        final RespawnTarget respawnTarget = resolveRespawnTarget(block, latestDefinition, world);
        if (respawnTarget != null) {
            // Clear the dead block BEFORE moving. The dead block was placed at the
            // ORIGIN position (where the dead hologram is shown), so we temporarily
            // set the block's coordinates to origin to clear at the right spot.
            // After clearing, the original current position is restored immediately.
            // This is safe because clearRealBlockModel only reads block.x/y/z and
            // performs no registry mutations.
            if (hadDeadModel) {
                final double currX = block.x();
                final double currY = block.y();
                final double currZ = block.z();
                block.setCurrentLocation(block.originX(), block.originY(), block.originZ());
                this.visualSyncSystem.clearRealBlockModel(block, latestDefinition, world);
                block.setCurrentLocation(currX, currY, currZ);
            }
            moveBlockToRespawnTarget(block, respawnTarget);
        }

        if (!isChunkLoaded(world, block.x(), block.z())) {
            markActiveAndPersist(block);
            return;
        }

        if (this.interactionOrchestrator.spawn(block, latestDefinition, world)) {
            markActiveAndPersist(block);
            this.hologramRuntimeService.showActive(block, latestDefinition);
            this.miningOrchestrator.playConfiguredSound(world, block, latestDefinition.soundOnRespawn());
            if (latestDefinition.breakAnimation()) {
                this.visualSyncSystem.clearBreakAnimation(world, block);
            }
            this.eventDispatcher.callRespawn(block, latestDefinition);
        }
    }

    private void moveBlockToRespawnTarget(final PlacedBlockModel block, final RespawnTarget respawnTarget) {
        final double oldX = block.x();
        final double oldY = block.y();
        final double oldZ = block.z();
        block.setCurrentLocation(
                respawnTarget.location().getX(),
                respawnTarget.location().getY(),
                respawnTarget.location().getZ()
        );
        if (respawnTarget.facing() != null) {
            // TODO: PlacedBlockModel currently does not expose a setter for facing.
            // If PlacedBlockModel#setFacing(String) is added, set the computed facing here:
            // block.setFacing(respawnTarget.facing());
        }
        this.stateRegistry.updateBlockPosition(block, oldX, oldY, oldZ);
    }

    private void markActiveAndPersist(final PlacedBlockModel block) {
        this.lifecycleSystem.markActive(block);
        block.setRespawnAt(null);
        if (!this.transientBlockPredicate.test(block.uniqueId())) {
            this.persistenceSystem.persistBlockAsync(block);
            this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
        }
    }

    private RespawnTarget resolveRespawnTarget(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final World world
    ) {
        final RandomLocationContext nodeContext = this.nodeRandomLocationContexts.get(block.uniqueId());
        if (nodeContext != null) {
            final Location location = this.randomLocationResolver.resolveRandomContextLocation(world, nodeContext, block.uniqueId());
            if (location != null) {
                final String facing = this.randomLocationResolver.resolveRandomFacing(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                return new RespawnTarget(location, facing);
            }
            // resolveRandomContextLocation returned null — spread around origin
            // without closest requirement so it works on flat terrain, and
            // ensure the result is NOT within 1 block of the center.
            final int originBlockX = (int) Math.floor(nodeContext.originX());
            final int originBlockY = (int) Math.floor(nodeContext.originY());
            final int originBlockZ = (int) Math.floor(nodeContext.originZ());

            // Compute vertical range from the node context radius
            final int nodeVerticalRange = Math.max(DEFAULT_VERTICAL_RANGE, (int) Math.ceil(nodeContext.radius()));

            // First try: origin without closest requirement
            final Location originSafe = this.randomLocationResolver.findSafeBlockLocation(
                    world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false, nodeVerticalRange
            );
            if (originSafe != null
                    && !isWithinHorizontalDistance(originSafe, originBlockX, originBlockZ, 1.0D)) {
                final String facing = this.randomLocationResolver.resolveRandomFacing(world,
                        originSafe.getBlockX(), originSafe.getBlockY(), originSafe.getBlockZ());
                return new RespawnTarget(originSafe, facing);
            }

            // Second try: spread around origin with offsets (avoid center 1x1 area)
            final int[][] offsets = {
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
                {3, 0}, {-3, 0}, {0, 3}, {0, -3}
            };
            for (final int[] offset : offsets) {
                final Location offsetLoc = this.randomLocationResolver.findSafeBlockLocation(
                        world,
                        originBlockX + offset[0],
                        originBlockY,
                        originBlockZ + offset[1],
                        block.uniqueId(),
                        false,
                        nodeVerticalRange
                );
                if (offsetLoc != null) {
                    final String facing = this.randomLocationResolver.resolveRandomFacing(world,
                            offsetLoc.getBlockX(), offsetLoc.getBlockY(), offsetLoc.getBlockZ());
                    return new RespawnTarget(offsetLoc, facing);
                }
            }

            // Ultimate fallback: the absolute origin (should rarely happen)
            return new RespawnTarget(
                    new Location(world, originBlockX, originBlockY, originBlockZ),
                    block.facing()
            );
        }

        final int originBlockX = (int) Math.floor(block.originX());
        final int originBlockY = (int) Math.floor(block.originY());
        final int originBlockZ = (int) Math.floor(block.originZ());

        if (!definition.randomLocationEnabled() || definition.randomLocationRadius() <= 0.0D) {
            final Location safeOrigin = this.randomLocationResolver.findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false, DEFAULT_VERTICAL_RANGE);
            final Location loc = safeOrigin != null
                    ? safeOrigin
                    : new Location(world, originBlockX, originBlockY, originBlockZ);
            return new RespawnTarget(loc, block.facing());
        }

        // Reuse resolveRandomContextLocation instead of duplicating random angle/distance logic.
        // Block definitions don't have closest/centerDistance config, so use safe defaults.
        final RandomLocationContext blockContext = new RandomLocationContext(
                block.originX(), block.originY(), block.originZ(),
                true,
                definition.randomLocationRadius(),
                false,
                1.0D
        );
        final Location location = this.randomLocationResolver.resolveRandomContextLocation(world, blockContext, block.uniqueId());
        if (location != null) {
            final String facing = this.randomLocationResolver.resolveRandomFacing(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
            return new RespawnTarget(location, facing);
        }

        // Fallback: try the origin
        final Location safeOrigin = this.randomLocationResolver.findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false, DEFAULT_VERTICAL_RANGE);
        final Location loc = safeOrigin != null
                ? safeOrigin
                : new Location(world, originBlockX, originBlockY, originBlockZ);
        return new RespawnTarget(loc, block.facing());
    }

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private static boolean isWithinHorizontalDistance(
            final Location loc,
            final int originBlockX,
            final int originBlockZ,
            final double threshold
    ) {
        final double dx = (loc.getBlockX() + 0.5D) - (originBlockX + 0.5D);
        final double dz = (loc.getBlockZ() + 0.5D) - (originBlockZ + 0.5D);
        return (dx * dx) + (dz * dz) <= threshold * threshold;
    }

    private record RespawnTarget(Location location, String facing) {
    }

    @FunctionalInterface
    public interface MissingDefinitionCleanup {
        void cleanup(PlacedBlockModel block);
    }
}
