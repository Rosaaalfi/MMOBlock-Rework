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
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.ServerSideFakeBlockService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;

public final class BlockRespawnOrchestrator {

    private static final double DEAD_UPDATE_NEARBY_RADIUS = 16.0D;
    private static final int DEFAULT_VERTICAL_RANGE = 10;
    private static final long FAILED_LOCATION_RETRY_MILLIS = 1_000L;

    private final BlockConfigLoader blockConfigService;
    private final PersistenceSystem persistenceSystem;
    private final BlockStateRegistry stateRegistry;
    private final RespawnScheduler respawnSystem;
    private final BlockLifecycleState lifecycleSystem;
    private final BlockVisualSyncService visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final BlockRandomLocationResolver randomLocationResolver;
    private final ServerSideFakeBlockService serverSideFakeBlockService;
    private final BlockModelApplier modelApplier;
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
            final ServerSideFakeBlockService serverSideFakeBlockService,
            final BlockModelApplier modelApplier,
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
        this.serverSideFakeBlockService = serverSideFakeBlockService;
        this.modelApplier = modelApplier;
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
        if (world != null
                && definition.schematicsEnabled()
                && definition.schematicsDeadFile() != null
                && !definition.schematicsDeadFile().isBlank()) {
            this.modelApplier.applySchematicModel(block, definition, world, true);
            this.serverSideFakeBlockService.syncNearbyPlayers(
                    world,
                    new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D),
                    this.blockConfigService.realBlockRadiusSquared()
            );
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
        final boolean hadDeadSchematic = latestDefinition.schematicsEnabled()
                && latestDefinition.schematicsDeadFile() != null
                && !latestDefinition.schematicsDeadFile().isBlank();

        // Remove the old packet hologram before its UUID is reused at another position.
        // Otherwise a replaced packet session can leave its former display entities behind.
        this.hologramRuntimeService.remove(block);

        // Clear the dead visual before resolving terrain. A physical dead-state block
        // must not become the height-map support for its own respawn position.
        if (hadDeadModel || hadDeadSchematic) {
            this.serverSideFakeBlockService.demoteBlock(block);
            FakeBlockRegistry.remove(block.world(), (int) Math.floor(block.x()), (int) Math.floor(block.y()), (int) Math.floor(block.z()));
        }
        if (hadDeadModel) {
            this.visualSyncSystem.clearRealBlockModel(block, latestDefinition, world);
        }
        if (hadDeadSchematic) {
            this.modelApplier.clearSchematicModel(block, world);
        }

        final RespawnTarget respawnTarget = resolveRespawnTarget(block, latestDefinition, world);
        if (respawnTarget == null) {
            schedule(block, world, FAILED_LOCATION_RETRY_MILLIS);
            return;
        }
        moveBlockToRespawnTarget(block, respawnTarget);

        if (!isChunkLoaded(world, block.x(), block.z())) {
            markActiveAndPersist(block);
            return;
        }

        applyVisuals(block, latestDefinition, world);
        this.serverSideFakeBlockService.syncNearbyPlayers(
                world,
                new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D),
                this.blockConfigService.realBlockRadiusSquared()
        );
        markActiveAndPersist(block);
        this.hologramRuntimeService.showActive(block, latestDefinition);
        this.miningOrchestrator.playConfiguredSound(world, block, latestDefinition.soundOnRespawn());
        if (latestDefinition.breakAnimation()) {
            this.visualSyncSystem.clearBreakAnimation(world, block);
        }
        this.eventDispatcher.callRespawn(block, latestDefinition);
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

            return null;
        }

        final int originBlockX = (int) Math.floor(block.originX());
        final int originBlockY = (int) Math.floor(block.originY());
        final int originBlockZ = (int) Math.floor(block.originZ());

        if (!definition.randomLocationEnabled() || definition.randomLocationRadius() <= 0.0D) {
            return new RespawnTarget(new Location(world, originBlockX, originBlockY, originBlockZ), block.facing());
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
        return safeOrigin == null ? null : new RespawnTarget(safeOrigin, block.facing());
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

    private void applyVisuals(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        this.visualSyncSystem.applyRealBlockModel(block, definition, world);
        this.modelApplier.applySchematicModel(block, definition, world, false);
        this.modelApplier.applyBdEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineCollision(block, definition, world);
        this.modelApplier.applyBetterModelModel(block, definition, world);
        this.modelApplier.applyBetterModelCollision(block, definition, world);
    }

    private record RespawnTarget(Location location, String facing) {
    }

    @FunctionalInterface
    public interface MissingDefinitionCleanup {
        void cleanup(PlacedBlockModel block);
    }
}
