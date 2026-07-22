package me.chyxelmc.mmoblock.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.runtime.block.BlockChunkLifecycleOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockEventDispatcher;
import me.chyxelmc.mmoblock.runtime.block.BlockLookProtection;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockMiningOrchestrator;
import me.chyxelmc.mmoblock.runtime.block.BlockMiningProgressReset;
import me.chyxelmc.mmoblock.runtime.block.BlockRandomLocationResolver;
import me.chyxelmc.mmoblock.runtime.block.BlockRespawnOrchestrator;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.interaction.LegacyInteractionRaytrace;
import me.chyxelmc.mmoblock.runtime.visual.BdEngineService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.SchematicService;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.ecs.system.BlockMiningSystem;
import me.chyxelmc.mmoblock.ecs.system.BlockRespawnSystem;
import me.chyxelmc.mmoblock.ecs.system.DropSpawnSystem;
import me.chyxelmc.mmoblock.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.ecs.system.ReconcileSystem;
import me.chyxelmc.mmoblock.ecs.system.VisualSyncSystem;
import net.kyori.adventure.text.Component;

public final class BlockRuntimeService {

    /**
     * Particle used for block-break dust effects. Resolved at class load time to
     * handle the particle rename across Minecraft versions.
     * <ul>
     *   <li>1.21.2+  → {@code BLOCK_CRUMBLE}
     *   <li>1.20.5–1.21.1 → {@code BLOCK}
     *   <li>1.19.4–1.20.4 → {@code BLOCK_DUST}
     * </ul>
     */
    private static final Particle BREAK_PARTICLE;

    static {
        Particle particle;
        try {
            particle = Particle.valueOf("BLOCK_CRUMBLE"); // 1.21.2+
        } catch (final IllegalArgumentException ex) {
            try {
                particle = Particle.valueOf("BLOCK");      // 1.20.5–1.21.1
            } catch (final IllegalArgumentException ex2) {
                particle = Particle.valueOf("BLOCK_DUST"); // 1.19.4–1.20.4
            }
        }
        BREAK_PARTICLE = particle;
    }

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockConfigLoader blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final DataCache dataCache;
    private final HologramRuntimeService hologramRuntimeService;
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;
    private final BlockModelApplier modelApplier;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockLifecycleOrchestrator lifecycleOrchestrator;
    private final BlockEventDispatcher eventDispatcher;
    private final BlockMiningOrchestrator miningOrchestrator;
    private final BlockRespawnOrchestrator respawnOrchestrator;
    private final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator;
    private final NamespacedKey uniqueIdKey;
    private final BlockEcsState ecsState = new BlockEcsState();
    private final BlockMiningSystem miningSystem;
    private final BlockRespawnSystem respawnSystem;
    private final VisualSyncSystem visualSyncSystem;
    private final DropSpawnSystem dropSystem;
    private final LifecycleSystem lifecycleSystem;
    private final ReconcileSystem reconcileSystem;
    private final BlockLookProtection lookProtection;
    private final BlockMiningProgressReset miningProgressReset;
    private final BlockRandomLocationResolver randomLocationResolver;
    private final LegacyInteractionRaytrace legacyInteractionRaytrace;
    private SchedulerTask lookRaytraceTask;
    private final java.util.Set<UUID> transientBlocks = new java.util.HashSet<>();
    private final java.util.Set<UUID> suppressDeadHologram = new java.util.HashSet<>();
    private final Map<UUID, RandomLocationContext> nodeRandomLocationContexts = new HashMap<>();

    public BlockRuntimeService(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Scheduler scheduler,
            final BlockConfigLoader blockConfigService,
            final PersistenceReadSystem persistenceReadSystem,
            final PersistenceSystem persistenceSystem,
            final DataCache dataCache
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.dataCache = dataCache;
        this.hologramRuntimeService = new HologramRuntimeService(plugin, nmsAdapter, scheduler);
        this.schematicService = new SchematicService(plugin, nmsAdapter);
        this.bdEngineService = new BdEngineService(plugin, nmsAdapter);
        this.modelApplier = new BlockModelApplier(plugin, nmsAdapter, schematicService, bdEngineService);
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
        this.miningSystem = new BlockMiningSystem(this.ecsState);
        this.respawnSystem = new BlockRespawnSystem(plugin, scheduler, this.ecsState);
        this.visualSyncSystem = new VisualSyncSystem(plugin, nmsAdapter);
        this.interactionOrchestrator = new BlockInteractionOrchestrator(
                plugin,
                nmsAdapter,
                this.visualSyncSystem,
                this.modelApplier,
                this.uniqueIdKey
        );
        this.eventDispatcher = new BlockEventDispatcher(plugin);
        this.dropSystem = new DropSpawnSystem(plugin, blockConfigService, scheduler, nmsAdapter);
        this.lifecycleSystem = new LifecycleSystem();
        this.reconcileSystem = new ReconcileSystem();
        this.randomLocationResolver = new BlockRandomLocationResolver(this.ecsState);
        this.lifecycleOrchestrator = new BlockLifecycleOrchestrator(
                plugin,
                blockConfigService,
                this.persistenceSystem,
                this.ecsState,
                this.respawnSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.interactionOrchestrator,
                this.eventDispatcher,
                this.transientBlocks,
                this.suppressDeadHologram
        );
        this.miningOrchestrator = new BlockMiningOrchestrator(
                plugin,
                blockConfigService,
                this.persistenceSystem,
                this.miningSystem,
                this.dropSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.interactionOrchestrator,
                this.eventDispatcher,
                this::isTransient,
                this::shouldSuppressDeadHologram,
                this::scheduleRespawn,
                BREAK_PARTICLE
        );
        this.respawnOrchestrator = new BlockRespawnOrchestrator(
                blockConfigService,
                this.persistenceSystem,
                this.ecsState,
                this.respawnSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.randomLocationResolver,
                this.interactionOrchestrator,
                this.eventDispatcher,
                this.miningOrchestrator,
                this.nodeRandomLocationContexts,
                this::isTransient,
                this::shouldSuppressDeadHologram,
                this::cleanupMissingDefinition
        );
        this.chunkLifecycleOrchestrator = new BlockChunkLifecycleOrchestrator(
                plugin,
                scheduler,
                blockConfigService,
                this.persistenceReadSystem,
                this.persistenceSystem,
                this.ecsState,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService,
                this.modelApplier,
                this.bdEngineService,
                this.interactionOrchestrator,
                this.respawnOrchestrator
        );
        this.lookProtection = new BlockLookProtection(this.ecsState);
        this.legacyInteractionRaytrace = new LegacyInteractionRaytrace(
                this.ecsState,
                blockConfigService,
                this.lifecycleSystem
        );
        this.miningProgressReset = new BlockMiningProgressReset(
                plugin,
                scheduler,
                blockConfigService,
                this.ecsState,
                this.miningSystem,
                this.lifecycleSystem,
                this.visualSyncSystem,
                this.hologramRuntimeService
        );
        this.miningProgressReset.start();
        startLookRaytraceTask();
        this.plugin.getServer().getPluginManager().registerEvents(this.lookProtection, this.plugin);
    }

    /**
     * Called by ECS systems when an interaction has been spawned by NMS for the
     * given blockUniqueId. We use this to update the PlacedBlockModel.interactionEntityId
     * so rest of the plugin can observe the NMS entity once spawn is complete.
     */
    public void onInteractionSpawned(final java.util.UUID blockUniqueId, final java.util.UUID interactionUniqueId) {
        try {
            final PlacedBlockModel block = this.ecsState.getBlock(blockUniqueId);
            if (block != null) {
                block.setInteractionEntityId(interactionUniqueId);
                try {
                    final BlockDefinitionModel def = this.blockConfigService.findBlock(block.type());
                    final World world = this.plugin.getServer().getWorld(block.world());
                if (def != null && world != null) {
                    this.interactionOrchestrator.applyModelsAfterEcsSpawn(block, def, world);
                }
                } catch (final Exception ignored) {
                // expected - reflection fallback
                }
            }
        } catch (final Exception ignored) {
        // expected - reflection fallback
        }
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.ecs.EntityManager entityManager) {
        this.interactionOrchestrator.setEntityManager(entityManager);
        this.hologramRuntimeService.setEntityManager(entityManager);
    }

    public PlaceResult place(final String type, final World world, final double x, final double y, final double z, final String facing) {
        return placeInternal(type, world, x, y, z, facing, true, false);
    }

    public PlaceResult placeNodeBlock(final String type, final World world, final double x, final double y, final double z, final String facing) {
        return placeNodeBlock(type, world, x, y, z, facing, null);
    }

    public PlaceResult placeNodeBlock(
            final String type,
            final World world,
            final double x,
            final double y,
            final double z,
            final String facing,
            final RandomLocationContext randomLocationContext
    ) {
        final PlaceResult result = placeInternal(type, world, x, y, z, facing, false, true);
        if (result.success()) {
            registerNodeBlock(result.placedBlock().uniqueId());
            if (randomLocationContext != null) {
                this.nodeRandomLocationContexts.put(result.placedBlock().uniqueId(), randomLocationContext);
            }
        }
        return result;
    }

    public PlaceResult placeRandomNodeBlock(
            final String type,
            final World world,
            final String facing,
            final RandomLocationContext randomLocationContext
    ) {
        final Location location = this.randomLocationResolver.resolveRandomContextLocation(world, randomLocationContext, null);
        if (location == null) {
            return PlaceResult.error("No safe node spawn location found");
        }
        return placeNodeBlock(type, world, location.getX(), location.getY(), location.getZ(), facing, randomLocationContext);
    }

    private PlaceResult placeInternal(
            final String type,
            final World world,
            final double x,
            final double y,
            final double z,
            final String facing,
            final boolean persist,
            final boolean suppressDead
    ) {
        return this.lifecycleOrchestrator.place(type, world, x, y, z, facing, persist, suppressDead);
    }

    public void registerNodeBlock(final UUID blockUniqueId) {
        this.lifecycleOrchestrator.registerNodeBlock(blockUniqueId);
    }

    public void unregisterNodeBlock(final UUID blockUniqueId) {
        this.lifecycleOrchestrator.unregisterNodeBlock(blockUniqueId);
        this.nodeRandomLocationContexts.remove(blockUniqueId);
    }

    public PlacedBlockModel findPlacedBlock(final UUID uniqueId) {
        PlacedBlockModel block = this.ecsState.getBlock(uniqueId);
        if (block != null) {
            return block;
        }
        block = this.dataCache.getBlock(uniqueId);
        return block;
    }

    public boolean removeById(final UUID uniqueId) {
        final PlacedBlockModel block = this.ecsState.getBlock(uniqueId);
        if (block == null) {
            return false;
        }
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return false;
        }
        return remove(block.type(), world, block.x(), block.y(), block.z());
    }

    public boolean removeByInteractionEntity(final Entity entity) {
        final UUID uniqueId = resolveBlockUniqueId(entity);
        if (uniqueId == null) {
            return false;
        }
        return removeById(uniqueId);
    }

    public UUID resolveBlockUniqueId(final Entity entity) {
        if (entity == null) {
            return null;
        }
        final String uniqueIdRaw = entity.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
        if (uniqueIdRaw == null) {
            return null;
        }
        try {
            return UUID.fromString(uniqueIdRaw);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isTransient(final UUID uniqueId) {
        return this.transientBlocks.contains(uniqueId);
    }

    private boolean shouldSuppressDeadHologram(final UUID uniqueId) {
        return this.suppressDeadHologram.contains(uniqueId);
    }

    public boolean remove(final String type, final World world, final double x, final double y, final double z) {
        return this.lifecycleOrchestrator.remove(type, world, x, y, z);
    }

    public Component handleInteraction(final Entity clickedEntity, final Player player, final String clickType) {
        final String uniqueIdRaw = clickedEntity.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
        if (uniqueIdRaw == null) {
            return null;
        }

        final UUID uniqueId;
        try {
            uniqueId = UUID.fromString(uniqueIdRaw);
        } catch (final IllegalArgumentException exception) {
            return null;
        }

        final PlacedBlockModel block = this.ecsState.getBlock(uniqueId);
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

    void restoreFromPersistence(final List<PlacedBlockModel> persistedBlocks) {
        this.chunkLifecycleOrchestrator.restoreFromPersistence(persistedBlocks);
    }

    public List<String> blockIds() {
        return new ArrayList<>(this.blockConfigService.blockIds());
    }

    public List<PlacedBlockModel> placedBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(this.ecsState.blocks()));
    }

    public me.chyxelmc.mmoblock.ecs.BlockEcsState ecsState() {
        return this.ecsState;
    }

    public void syncFakeBlocksForPlayer(final Player player) {
        this.chunkLifecycleOrchestrator.syncFakeBlocksForPlayer(player);
    }

    public void syncFakeBlocksForPlayerChunkWindow(final Player player) {
        this.chunkLifecycleOrchestrator.syncFakeBlocksForPlayerChunkWindow(player);
    }

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.hologramRuntimeService.handleViewerQuit(playerUniqueId);
        this.nmsAdapter.clearPacketHologramCacheForPlayer(playerUniqueId);
        this.nmsAdapter.clearPacketBdEngineModelCacheForPlayer(playerUniqueId);
        // Clear dead schematics for the quitting player so their client doesn't
        // retain stale fake blocks during the disconnect edge case
        final Player player = this.plugin.getServer().getPlayer(playerUniqueId);
        if (player != null) {
            for (final PlacedBlockModel block : this.ecsState.blocks()) {
                if (!this.lifecycleSystem.isRespawning(block)) continue;
                if (!block.world().equals(player.getWorld().getName())) continue;
                final BlockDefinitionModel def = this.blockConfigService.findBlock(block.type());
                if (def == null || !def.schematicsEnabled() || def.schematicsDeadFile() == null || def.schematicsDeadFile().isBlank()) continue;
                try {
                    this.schematicService.clearSchematicForPlayer(block.uniqueId().toString(), player);
                } catch (final Exception ignored) {
                // expected - reflection fallback
                }
            }
        }
        this.lookProtection.unprotect(playerUniqueId);
    }

    void shutdown() {
        this.miningProgressReset.stop();
        stopLookRaytraceTask();
        final boolean serverStopping = Bukkit.isStopping();
        for (final PlacedBlockModel block : this.ecsState.blocks()) {
            cancelRespawnTask(block.uniqueId());
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
            despawnInteraction(block);
        }
        this.hologramRuntimeService.shutdown();
        this.schematicService.clearAll();
        this.bdEngineService.clearAll();
        me.chyxelmc.mmoblock.api.integration.BetterModelIntegration.removeAll();
        this.modelApplier.clearAllCollisions();
        this.ecsState.clear();
        this.transientBlocks.clear();
        this.suppressDeadHologram.clear();
        this.nodeRandomLocationContexts.clear();
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

    /**
     * Periodic check: for each online player, perform a raytrace against PlacedBlocks
     * (using existing legacy AABB raytrace logic). If the player is currently looking at
     * a PlacedBlockModel, apply an invisible Mining Fatigue (SLOW_DIGGING) effect at a very
     * high amplifier to prevent block breaking. Remove the effect when they stop looking.
     */
    private void checkLookRaytrace() {
        final double defaultReach = Math.max(1.5D, this.plugin.getConfig().getDouble("interaction.reach", 6.0D));

        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            try {
                if (this.legacyInteractionRaytrace.findHit(player, defaultReach) != null) {
                    this.lookProtection.protect(player.getUniqueId());
                } else {
                    this.lookProtection.unprotect(player.getUniqueId());
                }
            } catch (final Exception ignored) {
            // expected - reflection fallback
            }
        }
    }

    ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        final List<PlacedBlockModel> persistedBlocks = new ArrayList<>(this.ecsState.snapshot()).stream()
                .filter(block -> !isTransient(block.uniqueId()))
                .toList();
        return this.reconcileSystem.reconcile(
                persistedBlocks,
                rebindActiveInteractions,
                this.blockConfigService::findBlock,
                worldName -> this.plugin.getServer().getWorld(worldName),
                this.lifecycleSystem::isActive,
                this.persistenceReadSystem::findRespawnAt,
                this::cleanupMissingDefinition,
                this.lifecycleSystem::markActive,
                this.persistenceSystem::persistBlockAsync,
                (block, definition, world) -> isChunkLoaded(world, block.x(), block.z()) && this.spawnInteraction(block, definition, world),
                (block, definition) -> {
                    final World world = this.plugin.getServer().getWorld(block.world());
                    if (world != null && isChunkLoaded(world, block.x(), block.z())) {
                        this.hologramRuntimeService.showActive(block, definition);
                    }
                },
                (block, definition, delayMillis) -> {
                    final World world = this.plugin.getServer().getWorld(block.world());
                    if (world != null && isChunkLoaded(world, block.x(), block.z())) {
                        showDeadOrRemoveSuppressed(block, definition, TimeUnit.MILLISECONDS.toSeconds(delayMillis));
                    }
                },
                this::scheduleRespawn,
                this::despawnInteraction
        );
    }

    public void handleChunkLoad(final World world, final int chunkX, final int chunkZ) {
        this.chunkLifecycleOrchestrator.handleChunkLoad(world, chunkX, chunkZ);
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        this.chunkLifecycleOrchestrator.handleChunkUnload(world, chunkX, chunkZ);
    }

    private Component processMiningClick(final PlacedBlockModel block, final Player player, final String clickType) {
        return this.miningOrchestrator.processMiningClick(block, player, clickType);
    }

    private void showDeadOrRemoveSuppressed(final PlacedBlockModel block, final BlockDefinitionModel definition, final long seconds) {
        this.respawnOrchestrator.showDeadOrRemoveSuppressed(block, definition, seconds);
    }

    private void scheduleRespawn(final PlacedBlockModel block, final World world, final long delayMillis) {
        this.respawnOrchestrator.schedule(block, world, delayMillis);
    }


    private boolean spawnInteraction(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        return this.interactionOrchestrator.spawn(placedBlock, definition, world);
    }

    private void despawnInteraction(final PlacedBlockModel block) {
        this.interactionOrchestrator.despawn(block);
    }

    private void cleanupMissingDefinition(final PlacedBlockModel block) {
        this.lifecycleOrchestrator.cleanupMissingDefinition(block);
    }

    private void cancelRespawnTask(final UUID uniqueId) {
        this.respawnSystem.cancel(uniqueId);
    }

    private boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    public record RandomLocationContext(
            double originX,
            double originY,
            double originZ,
            boolean enabled,
            double radius,
            boolean closest,
            double centerDistance
    ) {
    }

    public boolean isPlayerLookProtected(final Player player) {
        return this.lookProtection.isProtected(player);
    }


    public record PlaceResult(boolean success, String message, PlacedBlockModel placedBlock) {

        public static PlaceResult success(final PlacedBlockModel placedBlock) {
            return new PlaceResult(true, "", placedBlock);
        }

        public static PlaceResult error(final String message) {
            return new PlaceResult(false, message, null);
        }
    }

    public record ReconcileResult(int reboundInteractions, int cleanedMissingDefinitions, int rescheduledRespawns, int failedRebinds) {
    }
}
