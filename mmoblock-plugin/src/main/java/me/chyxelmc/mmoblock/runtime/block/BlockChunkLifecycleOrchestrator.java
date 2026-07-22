package me.chyxelmc.mmoblock.runtime.block;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.ecs.system.VisualSyncSystem;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.visual.BdEngineService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.SchematicService;

public final class BlockChunkLifecycleOrchestrator {

    private static final double FAKE_BLOCK_SYNC_RADIUS_SQUARED = 128.0D * 128.0D;
    private static final int MOVE_SYNC_CHUNK_RADIUS = 1;

    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockConfigLoader blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final BlockEcsState ecsState;
    private final LifecycleSystem lifecycleSystem;
    private final VisualSyncSystem visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final BlockModelApplier modelApplier;
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockRespawnOrchestrator respawnOrchestrator;

    public BlockChunkLifecycleOrchestrator(
            final MMOBlock plugin,
            final Scheduler scheduler,
            final BlockConfigLoader blockConfigService,
            final PersistenceReadSystem persistenceReadSystem,
            final PersistenceSystem persistenceSystem,
            final BlockEcsState ecsState,
            final LifecycleSystem lifecycleSystem,
            final VisualSyncSystem visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService,
            final BlockModelApplier modelApplier,
            final SchematicService schematicService,
            final BdEngineService bdEngineService,
            final BlockInteractionOrchestrator interactionOrchestrator,
            final BlockRespawnOrchestrator respawnOrchestrator
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.ecsState = ecsState;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
        this.modelApplier = modelApplier;
        this.schematicService = schematicService;
        this.bdEngineService = bdEngineService;
        this.interactionOrchestrator = interactionOrchestrator;
        this.respawnOrchestrator = respawnOrchestrator;
    }

    public void restoreFromPersistence(final List<PlacedBlockModel> persistedBlocks) {
        for (final PlacedBlockModel block : persistedBlocks) {
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world == null) {
                continue;
            }

            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                continue;
            }

            this.ecsState.putBlock(block);
            if (this.lifecycleSystem.isActive(block)) {
                if (isChunkLoaded(world, block.x(), block.z())) {
                    scheduleRestoreActiveBlock(block, definition, world);
                }
                continue;
            }

            final Long respawnAt = this.persistenceReadSystem.findRespawnAt(block.uniqueId());
            if (respawnAt == null) {
                this.lifecycleSystem.markActive(block);
                this.persistenceSystem.persistBlockAsync(block);
                if (isChunkLoaded(world, block.x(), block.z())) {
                    scheduleRestoreActiveBlock(block, definition, world);
                }
                continue;
            }

            this.lifecycleSystem.markRespawning(block);
            this.persistenceSystem.persistBlockAsync(block);
            final long delay = Math.max(1L, respawnAt - System.currentTimeMillis());
            if (isChunkLoaded(world, block.x(), block.z())) {
                final long seconds = TimeUnit.MILLISECONDS.toSeconds(delay);
                this.scheduler.runAtLocationLater(
                        blockLocation(world, block),
                        () -> this.respawnOrchestrator.showDeadOrRemoveSuppressed(block, definition, seconds),
                        20L
                );
            }
            this.respawnOrchestrator.schedule(block, world, delay);
        }
    }

    private void syncSchematicsForPlayer(final Player player, final Collection<PlacedBlockModel> blocks) {
        final World world = player.getWorld();
        for (final PlacedBlockModel block : blocks) {
            if (!block.world().equals(world.getName())) continue;
            if (!this.lifecycleSystem.isActive(block)) continue;
            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            if (definition == null || !definition.schematicsEnabled()) continue;
            this.schematicService.showSchematicForPlayer(
                    block.uniqueId().toString(),
                    player,
                    definition,
                    world,
                    block.x(),
                    block.y(),
                    block.z(),
                    false
            );
        }
    }

    public void syncFakeBlocksForPlayer(final Player player) {
        this.visualSyncSystem.syncFakeBlocksForPlayer(
                player,
                this.ecsState.blocks(),
                this.blockConfigService::findBlock,
                LifecycleSystem.STATUS_ACTIVE,
                FAKE_BLOCK_SYNC_RADIUS_SQUARED
        );
        this.hologramRuntimeService.syncForPlayer(player, this.ecsState.blocks());
        syncSchematicsForPlayer(player, this.ecsState.blocks());
        for (final PlacedBlockModel block : this.ecsState.blocks()) {
            this.bdEngineService.syncForPlayer(player, block.uniqueId());
        }
    }

    public void syncFakeBlocksForPlayerChunkWindow(final Player player) {
        final int chunkX = player.getLocation().getChunk().getX();
        final int chunkZ = player.getLocation().getChunk().getZ();
        final Collection<PlacedBlockModel> candidateBlocks = this.ecsState.blocksInChunkWindow(
                player.getWorld().getName(),
                chunkX,
                chunkZ,
                MOVE_SYNC_CHUNK_RADIUS
        );
        this.visualSyncSystem.syncFakeBlocksForPlayer(
                player,
                candidateBlocks,
                this.blockConfigService::findBlock,
                LifecycleSystem.STATUS_ACTIVE,
                FAKE_BLOCK_SYNC_RADIUS_SQUARED
        );
        this.hologramRuntimeService.syncForPlayer(player, candidateBlocks);
        syncSchematicsForPlayer(player, candidateBlocks);
        for (final PlacedBlockModel block : candidateBlocks) {
            this.bdEngineService.syncForPlayer(player, block.uniqueId());
        }
    }

    public void handleChunkLoad(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedBlockModel block : this.ecsState.blocksInChunk(world.getName(), chunkX, chunkZ)) {
            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                continue;
            }
            if (this.lifecycleSystem.isActive(block)) {
                if (this.interactionOrchestrator.spawn(block, definition, world)) {
                    this.hologramRuntimeService.showActive(block, definition);
                    // Schedule a delayed visual re-apply 10 ticks later to handle startup
                    // timing issues where the NMS layer or world may not be fully ready.
                    // This is particularly important for schematics and CraftEngine blocks
                    // on server restart where the initial apply may silently fail.
                    scheduleDelayedVisualApply(block, definition, world);
                }
                continue;
            }

            if (this.lifecycleSystem.isRespawning(block)) {
                final long secondsLeft = Math.max(0L, (block.respawnAt() == null ? 0L : block.respawnAt() - System.currentTimeMillis()) / 1000L);
                this.respawnOrchestrator.showDeadOrRemoveSuppressed(block, definition, secondsLeft);
            }
        }
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedBlockModel block : this.ecsState.blocksInChunk(world.getName(), chunkX, chunkZ)) {
            this.interactionOrchestrator.despawn(block);
            this.hologramRuntimeService.remove(block);
            this.visualSyncSystem.clearBreakAnimation(world, block);
            this.modelApplier.clearSchematicModel(block, world);
            this.modelApplier.clearBdEngineModel(block, world);
            this.modelApplier.clearBetterModelModel(block, world);
            this.modelApplier.clearBetterModelCollision(block, world);

            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            if (definition != null) {
                this.visualSyncSystem.clearRealBlockModel(block, definition, world);
                this.modelApplier.clearModelEngineModel(block, world);
                this.modelApplier.clearModelEngineCollision(block, world);
            }
        }
    }

    private void scheduleRestoreActiveBlock(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final World world
    ) {
        this.scheduler.runAtLocationLater(blockLocation(world, block), () -> {
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            if (!isChunkLoaded(world, block.x(), block.z())) {
                return;
            }
            if (this.interactionOrchestrator.spawn(block, definition, world)) {
                this.hologramRuntimeService.showActive(block, definition);
                scheduleDelayedVisualApply(block, definition, world);
            }
        }, 20L);
    }

    /**
     * Schedule a delayed re-apply of visual models (schematics, CraftEngine blocks, etc.)
     * to handle timing issues on server startup where the NMS layer or world may not
     * be fully ready during the initial spawn.
     * <p>
     * This runs 10 ticks after the successful spawn and re-applies the models that were
     * already applied in {@link BlockInteractionOrchestrator#applySpawnedModels}.
     * Non-fatal exceptions during re-application are silently ignored.
     */
    private void scheduleDelayedVisualApply(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final World world
    ) {
        final UUID blockId = block.uniqueId();
        final String worldName = world.getName();
        this.scheduler.runAtLocationLater(blockLocation(world, block), () -> {
            try {
                final World w = this.plugin.getServer().getWorld(worldName);
                if (w == null) return;
                if (!this.ecsState.containsBlock(blockId)) return;
                final PlacedBlockModel current = this.ecsState.getBlock(blockId);
                if (current == null || !this.lifecycleSystem.isActive(current)) return;
                final BlockDefinitionModel def = this.blockConfigService.findBlock(current.type());
                if (def == null) return;
                this.visualSyncSystem.applyRealBlockModel(current, def, w);
                this.modelApplier.applySchematicModel(current, def, w, false);
            } catch (final Exception ignored) {
                // expected - non-critical retry
            }
        }, 10L);
    }

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private static Location blockLocation(final World world, final PlacedBlockModel block) {
        return new Location(world, block.x(), block.y(), block.z());
    }
}
