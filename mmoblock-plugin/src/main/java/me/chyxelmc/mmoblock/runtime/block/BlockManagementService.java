package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.block.MiningProgressTracker;
import me.chyxelmc.mmoblock.runtime.block.RespawnScheduler;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.block.ReconcileCoordinator;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.interaction.LegacyInteractionRaytrace;
import me.chyxelmc.mmoblock.runtime.visual.BdEngineService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.SchematicService;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BlockManagementService {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockConfigLoader blockConfigService;
    private final BlockStateRegistry stateRegistry;
    private final BlockPlacementService placementService;
    private final BlockQueryService queryService;
    private final HologramRuntimeService hologramRuntimeService;
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;
    private final BlockModelApplier modelApplier;
    private final BlockVisualSyncService visualSyncSystem;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockMiningOrchestrator miningOrchestrator;
    private final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator;
    private final BlockRespawnOrchestrator respawnOrchestrator;
    private final BlockLookProtection lookProtection;
    private final BlockMiningProgressReset miningProgressReset;
    private final LegacyInteractionRaytrace legacyInteractionRaytrace;
    private final BlockLifecycleState lifecycleSystem;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final ReconcileCoordinator reconcileSystem;
    private SchedulerTask lookRaytraceTask;

    public BlockManagementService(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Scheduler scheduler,
            final BlockConfigLoader blockConfigService,
            final BlockStateRegistry stateRegistry,
            final BlockPlacementService placementService,
            final BlockQueryService queryService,
            final HologramRuntimeService hologramRuntimeService,
            final SchematicService schematicService,
            final BdEngineService bdEngineService,
            final BlockModelApplier modelApplier,
            final BlockVisualSyncService visualSyncSystem,
            final BlockInteractionOrchestrator interactionOrchestrator,
            final BlockMiningOrchestrator miningOrchestrator,
            final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator,
            final BlockRespawnOrchestrator respawnOrchestrator,
            final BlockLookProtection lookProtection,
            final BlockMiningProgressReset miningProgressReset,
            final LegacyInteractionRaytrace legacyInteractionRaytrace,
            final BlockLifecycleState lifecycleSystem,
            final PersistenceReadSystem persistenceReadSystem,
            final PersistenceSystem persistenceSystem,
            final ReconcileCoordinator reconcileSystem
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.stateRegistry = stateRegistry;
        this.placementService = placementService;
        this.queryService = queryService;
        this.hologramRuntimeService = hologramRuntimeService;
        this.schematicService = schematicService;
        this.bdEngineService = bdEngineService;
        this.modelApplier = modelApplier;
        this.visualSyncSystem = visualSyncSystem;
        this.interactionOrchestrator = interactionOrchestrator;
        this.miningOrchestrator = miningOrchestrator;
        this.chunkLifecycleOrchestrator = chunkLifecycleOrchestrator;
        this.respawnOrchestrator = respawnOrchestrator;
        this.lookProtection = lookProtection;
        this.miningProgressReset = miningProgressReset;
        this.legacyInteractionRaytrace = legacyInteractionRaytrace;
        this.lifecycleSystem = lifecycleSystem;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.reconcileSystem = reconcileSystem;
    }

    // ============================================================
    // ECS / Entity Management
    // ============================================================

    public void onInteractionSpawned(final UUID blockUniqueId, final UUID interactionUniqueId) {
        try {
            final PlacedBlockModel block = this.stateRegistry.getBlock(blockUniqueId);
            if (block != null) {
                block.setInteractionEntityId(interactionUniqueId);
                try {
                    final BlockDefinitionModel def = this.blockConfigService.findBlock(block.type());
                    final World world = this.plugin.getServer().getWorld(block.world());
                    if (def != null && world != null) {
                        this.interactionOrchestrator.applyModelsAfterEcsSpawn(block, def, world);
                    }
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.ecs.EntityManager entityManager) {
        this.interactionOrchestrator.setEntityManager(entityManager);
        this.hologramRuntimeService.setEntityManager(entityManager);
    }

    // ============================================================
    // Interaction / Mining
    // ============================================================

    public Component handleInteraction(final org.bukkit.entity.Entity clickedEntity, final Player player, final String clickType) {
        final UUID uniqueId = this.queryService.resolveBlockUniqueId(clickedEntity);
        if (uniqueId == null) {
            return null;
        }
        final PlacedBlockModel block = this.stateRegistry.getBlock(uniqueId);
        if (block == null) {
            return null;
        }
        return processMiningClick(block, player, clickType);
    }

    public Component handleLegacyFallbackInteraction(final Player player, final String clickType) {
        final double reach = Math.max(1.5D, this.plugin.getConfig().getDouble("interaction.legacy-reach", 6.0D));
        final PlacedBlockModel block = this.legacyInteractionRaytrace.findHit(player, reach);
        if (block == null) {
            return null;
        }
        return processMiningClick(block, player, clickType);
    }

    private Component processMiningClick(final PlacedBlockModel block, final Player player, final String clickType) {
        return this.miningOrchestrator.processMiningClick(block, player, clickType);
    }

    // ============================================================
    // Player Quit
    // ============================================================

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.hologramRuntimeService.handleViewerQuit(playerUniqueId);
        this.nmsAdapter.clearPacketHologramCacheForPlayer(playerUniqueId);
        this.nmsAdapter.clearPacketBdEngineModelCacheForPlayer(playerUniqueId);
        final Player player = this.plugin.getServer().getPlayer(playerUniqueId);
        if (player != null) {
            for (final PlacedBlockModel block : this.stateRegistry.blocks()) {
                if (!this.lifecycleSystem.isRespawning(block)) continue;
                if (!block.world().equals(player.getWorld().getName())) continue;
                final BlockDefinitionModel def = this.blockConfigService.findBlock(block.type());
                if (def == null || !def.schematicsEnabled() || def.schematicsDeadFile() == null || def.schematicsDeadFile().isBlank()) continue;
                try {
                    this.schematicService.clearSchematicForPlayer(block.uniqueId().toString(), player);
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }
        }
        this.lookProtection.unprotect(playerUniqueId);
    }

    // ============================================================
    // Shutdown
    // ============================================================

    public void shutdown() {
        this.miningProgressReset.stop();
        stopLookRaytraceTask();
        final boolean serverStopping = Bukkit.isStopping();
        for (final PlacedBlockModel block : this.stateRegistry.blocks()) {
            this.placementService.cancelRespawnTask(block.uniqueId());
            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            final World world = this.plugin.getServer().getWorld(block.world());
            if (!serverStopping && definition != null && world != null) {
                this.visualSyncSystem.clearRealBlockModel(block, definition, world);
                this.modelApplier.clearSchematicModel(block, world);
                this.modelApplier.clearBdEngineModel(block, world);
                this.modelApplier.clearModelEngineModel(block, world);
                this.modelApplier.clearModelEngineCollision(block, world);
                this.modelApplier.clearBetterModelModel(block, world);
                this.modelApplier.clearBetterModelCollision(block, world);
            }
            this.placementService.despawnInteraction(block);
        }
        this.hologramRuntimeService.shutdown();
        this.schematicService.clearAll();
        this.bdEngineService.clearAll();
        try {
            me.chyxelmc.mmoblock.api.integration.BetterModelIntegration.removeAll();
        } catch (final Throwable ignored) {
            // BetterModel not installed or class loading failed
        }
        this.modelApplier.clearAllCollisions();
        this.visualSyncSystem.clearOriginalMaterials();
        this.stateRegistry.clear();
        this.placementService.clearTransientState();
    }

    // ============================================================
    // Reconcile (config reload)
    // ============================================================

    public ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        final List<PlacedBlockModel> persistedBlocks = new ArrayList<>(this.stateRegistry.snapshot()).stream()
                .filter(block -> !this.placementService.isTransient(block.uniqueId()))
                .toList();
        return this.reconcileSystem.reconcile(
                persistedBlocks,
                rebindActiveInteractions,
                this.blockConfigService::findBlock,
                worldName -> this.plugin.getServer().getWorld(worldName),
                this.lifecycleSystem::isActive,
                this.persistenceReadSystem::findRespawnAt,
                this.placementService::cleanupMissingDefinition,
                this.lifecycleSystem::markActive,
                this.persistenceSystem::persistBlockAsync,
                (block, definition, world) -> isChunkLoaded(world, block.x(), block.z()) && this.placementService.spawnInteraction(block, definition, world),
                (block, definition) -> {
                    final World world = this.plugin.getServer().getWorld(block.world());
                    if (world != null && isChunkLoaded(world, block.x(), block.z())) {
                        this.hologramRuntimeService.showActive(block, definition);
                    }
                },
                (block, definition, delayMillis) -> {
                    final World world = this.plugin.getServer().getWorld(block.world());
                    if (world != null && isChunkLoaded(world, block.x(), block.z())) {
                        this.placementService.showDeadOrRemoveSuppressed(block, definition, TimeUnit.MILLISECONDS.toSeconds(delayMillis));
                    }
                },
                this.placementService::scheduleRespawn,
                this.placementService::despawnInteraction
        );
    }

    // ============================================================
    // Sync
    // ============================================================

    public void syncFakeBlocksForPlayer(final Player player) {
        this.chunkLifecycleOrchestrator.syncFakeBlocksForPlayer(player);
    }

    public void syncFakeBlocksForPlayerChunkWindow(final Player player) {
        this.chunkLifecycleOrchestrator.syncFakeBlocksForPlayerChunkWindow(player);
    }

    // ============================================================
    // Background Tasks (started after construction)
    // ============================================================

    public void startBackgroundTasks() {
        startLookRaytraceTask();
    }

    private void startLookRaytraceTask() {
        stopLookRaytraceTask();
        final int interval = Math.max(1, this.plugin.getConfig().getInt("interaction.look-check-interval-ticks", 2));
        this.lookRaytraceTask = this.scheduler.runTimer(
                this::checkLookRaytrace,
                1L,
                interval
        );
    }

    private void stopLookRaytraceTask() {
        if (this.lookRaytraceTask != null) {
            this.lookRaytraceTask.cancel();
            this.lookRaytraceTask = null;
        }
        this.lookProtection.clear();
    }

    private void checkLookRaytrace() {
        final double defaultReach = Math.max(1.5D, this.plugin.getConfig().getDouble("interaction.reach", 6.0D));
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            try {
                if (this.legacyInteractionRaytrace.findHit(player, defaultReach) != null) {
                    this.lookProtection.protect(player.getUniqueId());
                } else {
                    this.lookProtection.unprotect(player.getUniqueId());
                }
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
        }
    }

    // ============================================================
    // Utilities
    // ============================================================

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }
}
