package me.chyxelmc.mmoblock.runtime.block;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.World;

import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.ecs.system.BlockRespawnSystem;
import me.chyxelmc.mmoblock.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.ecs.system.VisualSyncSystem;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService.RandomLocationContext;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;

public final class BlockRespawnOrchestrator {

    private static final double DEAD_UPDATE_NEARBY_RADIUS = 16.0D;
    private static final int RANDOM_LOCATION_MAX_ATTEMPTS = 48;

    private final BlockConfigLoader blockConfigService;
    private final PersistenceSystem persistenceSystem;
    private final BlockEcsState ecsState;
    private final BlockRespawnSystem respawnSystem;
    private final LifecycleSystem lifecycleSystem;
    private final VisualSyncSystem visualSyncSystem;
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
            final BlockEcsState ecsState,
            final BlockRespawnSystem respawnSystem,
            final LifecycleSystem lifecycleSystem,
            final VisualSyncSystem visualSyncSystem,
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
        this.ecsState = ecsState;
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

        final RespawnTarget respawnTarget = resolveRespawnTarget(block, latestDefinition, world);
        if (respawnTarget != null) {
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
        this.ecsState.updateBlockPosition(block, oldX, oldY, oldZ);
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
            final Location fallback = this.randomLocationResolver.findSafeBlockLocation(
                    world,
                    (int) Math.floor(nodeContext.originX()),
                    (int) Math.floor(nodeContext.originY()),
                    (int) Math.floor(nodeContext.originZ()),
                    block.uniqueId(),
                    nodeContext.closest()
            );
            final Location loc = fallback != null
                    ? fallback
                    : new Location(world, Math.floor(nodeContext.originX()), Math.floor(nodeContext.originY()), Math.floor(nodeContext.originZ()));
            return new RespawnTarget(loc, block.facing());
        }

        final int originBlockX = (int) Math.floor(block.originX());
        final int originBlockY = (int) Math.floor(block.originY());
        final int originBlockZ = (int) Math.floor(block.originZ());

        if (!definition.randomLocationEnabled() || definition.randomLocationRadius() <= 0.0D) {
            final Location safeOrigin = this.randomLocationResolver.findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false);
            final Location loc = safeOrigin != null
                    ? safeOrigin
                    : new Location(world, originBlockX, originBlockY, originBlockZ);
            return new RespawnTarget(loc, block.facing());
        }

        final double radius = definition.randomLocationRadius();
        for (int attempt = 0; attempt < RANDOM_LOCATION_MAX_ATTEMPTS; attempt++) {
            final double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            final double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
            final int targetBlockX = originBlockX + (int) Math.round(Math.cos(angle) * distance);
            final int targetBlockZ = originBlockZ + (int) Math.round(Math.sin(angle) * distance);

            final Location safe = this.randomLocationResolver.findSafeBlockLocation(world, targetBlockX, originBlockY, targetBlockZ, block.uniqueId(), false);
            if (safe != null) {
                final String facing = this.randomLocationResolver.resolveRandomFacing(world, (int) safe.getX(), (int) safe.getY(), (int) safe.getZ());
                return new RespawnTarget(safe, facing);
            }
        }

        final Location safeOrigin = this.randomLocationResolver.findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false);
        final Location loc = safeOrigin != null
                ? safeOrigin
                : new Location(world, originBlockX, originBlockY, originBlockZ);
        return new RespawnTarget(loc, block.facing());
    }

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private record RespawnTarget(Location location, String facing) {
    }

    @FunctionalInterface
    public interface MissingDefinitionCleanup {
        void cleanup(PlacedBlockModel block);
    }
}
