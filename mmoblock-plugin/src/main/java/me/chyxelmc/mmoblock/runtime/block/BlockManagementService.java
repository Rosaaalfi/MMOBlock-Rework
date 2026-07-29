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
import me.chyxelmc.mmoblock.runtime.interaction.ServerSideFakeBlockService;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockManagementService {

    private static final long CLICK_DEDUPE_MILLIS = 150L;
    private static final long CLICK_DEDUPE_RETENTION_MILLIS = 1_000L;
    private static final int CLICK_DEDUPE_CLEANUP_THRESHOLD = 4_096;

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
    private final ServerSideFakeBlockService serverSideFakeBlockService;
    private final BlockModelApplier modelApplier;
    private final BlockVisualSyncService visualSyncSystem;
    private final BlockMiningOrchestrator miningOrchestrator;
    private final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator;
    private final BlockRespawnOrchestrator respawnOrchestrator;
    private final BlockMiningProgressReset miningProgressReset;
    private final BlockLifecycleState lifecycleSystem;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final ReconcileCoordinator reconcileSystem;
    private final Map<ClickDedupeKey, Long> recentClicks = new ConcurrentHashMap<>();
    private SchedulerTask serverSideInteractionReconcileTask;

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
            final ServerSideFakeBlockService serverSideFakeBlockService,
            final BlockModelApplier modelApplier,
            final BlockVisualSyncService visualSyncSystem,
            final BlockMiningOrchestrator miningOrchestrator,
            final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator,
            final BlockRespawnOrchestrator respawnOrchestrator,
            final BlockMiningProgressReset miningProgressReset,
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
        this.serverSideFakeBlockService = serverSideFakeBlockService;
        this.modelApplier = modelApplier;
        this.visualSyncSystem = visualSyncSystem;
        this.miningOrchestrator = miningOrchestrator;
        this.chunkLifecycleOrchestrator = chunkLifecycleOrchestrator;
        this.respawnOrchestrator = respawnOrchestrator;
        this.miningProgressReset = miningProgressReset;
        this.lifecycleSystem = lifecycleSystem;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.reconcileSystem = reconcileSystem;
    }

    // ============================================================
    // Real Block Mode Interaction
    // ============================================================

    /**
     * Handles a click on a real/fake block at a world position.
     * Looks up the block in the state registry by position and delegates to the mining orchestrator.
     *
     * @param player    the clicking player
     * @param clickType "left_click" or "right_click"
     * @param world     the world
     * @param x         block X coordinate
     * @param y         block Y coordinate
     * @param z         block Z coordinate
     * @return a message component, or {@code null} if no block was found
     */
    public Component handleRealBlockClick(final Player player, final String clickType, final World world, final double x, final double y, final double z) {
        final PlacedBlockModel block = this.stateRegistry.blockAt(world.getName(), x, y, z);
        if (block == null) {
            return null;
        }
        if (isDuplicateClick(player, clickType, block)) {
            return Component.empty();
        }
        return this.miningOrchestrator.processMiningClick(block, player, clickType);
    }

    public Component handleBlockBreakAttempt(final Player player, final World world, final double x, final double y, final double z) {
        final PlacedBlockModel block = this.stateRegistry.blockAt(world.getName(), x, y, z);
        if (block == null || !this.miningOrchestrator.canProcessBlockBreak(block, player)) {
            return null;
        }
        if (isDuplicateClick(player, "block_break", block)) {
            return Component.empty();
        }
        return this.miningOrchestrator.processBlockBreak(block, player);
    }

    private boolean isDuplicateClick(final Player player, final String clickType, final PlacedBlockModel block) {
        final long now = System.currentTimeMillis();
        cleanupRecentClicks(now);
        final ClickDedupeKey key = new ClickDedupeKey(player.getUniqueId(), block.uniqueId(), clickType);
        final Long previous = this.recentClicks.put(key, now);
        return previous != null && now - previous <= CLICK_DEDUPE_MILLIS;
    }

    private void cleanupRecentClicks(final long now) {
        if (this.recentClicks.size() < CLICK_DEDUPE_CLEANUP_THRESHOLD) {
            return;
        }
        this.recentClicks.entrySet().removeIf(entry -> now - entry.getValue() > CLICK_DEDUPE_RETENTION_MILLIS);
    }

    // ============================================================
    // Player Quit
    // ============================================================

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.miningOrchestrator.cancelAutoProgressForPlayer(playerUniqueId);
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
    }

    // ============================================================
    // Shutdown
    // ============================================================

    public void shutdown() {
        stopServerSideInteractionReconcileTask();
        this.miningOrchestrator.cancelAllAutoProgress();
        this.miningProgressReset.stop();
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
        this.serverSideFakeBlockService.demoteAll();
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
                (block, definition, world) -> isChunkLoaded(world, block.x(), block.z()),
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
                block -> { /* despawn no-op — interaction entities removed */ }
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

    public void syncServerSideInteractionBlocks(final Player player) {
        this.serverSideFakeBlockService.syncForPlayer(player, this.blockConfigService.realBlockRadiusSquared());
    }

    public void syncServerSideInteractionBlocks(final World world) {
        this.serverSideFakeBlockService.syncWorld(world, this.blockConfigService.realBlockRadiusSquared());
    }

    public boolean isServerSideInteractionBlock(final String worldName, final int x, final int y, final int z) {
        return this.serverSideFakeBlockService.isPromoted(worldName, x, y, z);
    }

    // ============================================================
    // Background Tasks (started after construction)
    // ============================================================

    public void startBackgroundTasks() {
        stopServerSideInteractionReconcileTask();
        this.serverSideInteractionReconcileTask = this.scheduler.runTimer(
                () -> this.serverSideFakeBlockService.reconcile(
                        this.plugin.getServer().getOnlinePlayers(),
                        this.blockConfigService.realBlockRadiusSquared()
                ),
                1L,
                5L
        );
    }

    private void stopServerSideInteractionReconcileTask() {
        if (this.serverSideInteractionReconcileTask != null) {
            this.serverSideInteractionReconcileTask.cancel();
            this.serverSideInteractionReconcileTask = null;
        }
    }

    // ============================================================
    // Utilities
    // ============================================================

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private record ClickDedupeKey(UUID playerUniqueId, UUID blockUniqueId, String clickType) {
    }
}
