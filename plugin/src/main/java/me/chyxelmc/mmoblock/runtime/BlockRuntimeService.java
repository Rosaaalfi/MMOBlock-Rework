package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.api.event.BlockMineEvent;
import me.chyxelmc.mmoblock.api.event.BlockPlaceEvent;
import me.chyxelmc.mmoblock.api.event.BlockRemoveEvent;
import me.chyxelmc.mmoblock.api.event.BlockRespawnEvent;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.ConditionDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.nmsloader.utils.ClientProtocolUtils;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.runtime.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.runtime.ecs.system.DropSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.MiningSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.ReconcileSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.RespawnSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.VisualSyncSystem;
import me.chyxelmc.mmoblock.utils.ConditionEvaluator;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockRuntimeService {

    private static final double FAKE_BLOCK_SYNC_RADIUS_SQUARED = 128.0D * 128.0D;
    private static final long MINING_PROGRESS_RESET_TIMEOUT_MS = 5000L;
    private static final long MINING_PROGRESS_RESET_CHECK_TICKS = 20L;
    private static final int MOVE_SYNC_CHUNK_RADIUS = 1;
    private static final double DEAD_UPDATE_NEARBY_RADIUS = 16.0D;
    private static final double NODE_RANDOM_MIN_BLOCK_DISTANCE = 1.5D;
    private static final int RANDOM_LOCATION_MAX_ATTEMPTS = 48;

    private static final java.util.Set<Material> VEGETATION_MATERIALS;

    static {
        // Build a materials set in a multi-version-safe way. Use Material.matchMaterial
        // so missing enum constants on older server versions don't cause NoSuchFieldError.
        final java.util.Set<Material> set = java.util.EnumSet.noneOf(Material.class);
        final String[] names = new String[]{
                "GRASS",
                "SHORT_GRASS",
                "TALL_GRASS",
                "FERN",
                "LARGE_FERN",
                "DEAD_BUSH",
                "SEAGRASS",
                "TALL_SEAGRASS",
                "VINE",
                "HANGING_ROOTS",
                "AZALEA",
                "FLOWERING_AZALEA",
                "MOSS_CARPET",
                "SUGAR_CANE",
                "BAMBOO",
                "SWEET_BERRY_BUSH",
                "SHORT_DRY_GRASS",
                "TALL_DRY_GRASS"
        };
        for (final String n : names) {
            try {
                final Material m = Material.matchMaterial(n);
                if (m != null) {
                    set.add(m);
                }
            } catch (final Throwable ignored) {
                // ignore missing materials
            }
        }
        VEGETATION_MATERIALS = java.util.Collections.unmodifiableSet(set);
    }

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockConfigService blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final DataCache dataCache;
    private final HologramRuntimeService hologramRuntimeService;
    private final SchematicService schematicService;
    private final NamespacedKey uniqueIdKey;
    private final BlockEcsState ecsState = new BlockEcsState();
    // Optional ECS integration
    private me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager;
    private final MiningSystem miningSystem;
    private final RespawnSystem respawnSystem;
    private final VisualSyncSystem visualSyncSystem;
    private final DropSystem dropSystem;
    private final LifecycleSystem lifecycleSystem;
    private final ReconcileSystem reconcileSystem;
    private SchedulerTask miningProgressResetTask;
    private SchedulerTask lookRaytraceTask;
    // Track players we've applied the invisible mining-fatigue effect to so we don't remove
    // someone else's effect accidentally.
    private final java.util.Set<UUID> lookDebuffed = new java.util.HashSet<>();
    private final java.util.Set<UUID> transientBlocks = new java.util.HashSet<>();
    private final java.util.Set<UUID> suppressDeadHologram = new java.util.HashSet<>();
    private final Map<UUID, RandomLocationContext> nodeRandomLocationContexts = new HashMap<>();
    // We track players currently prevented from breaking placed blocks via `lookDebuffed`.
    // Instead of applying a visible/intrusive potion effect to the player, we cancel
    // their digging/break events server-side so their POV remains unaffected.

    public BlockRuntimeService(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Scheduler scheduler,
            final BlockConfigService blockConfigService,
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
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
        this.miningSystem = new MiningSystem(this.ecsState);
        this.respawnSystem = new RespawnSystem(plugin, scheduler, this.ecsState);
        this.visualSyncSystem = new VisualSyncSystem(plugin, nmsAdapter);
        this.dropSystem = new DropSystem(plugin, blockConfigService);
        this.lifecycleSystem = new LifecycleSystem();
        this.reconcileSystem = new ReconcileSystem();
        startMiningProgressResetTask();
        startLookRaytraceTask();
        // Register server-side protection listener to cancel digging/break events for
        // players currently looking at placed blocks. This keeps their POV unaffected
        // while still preventing block breaking server-side.
        this.plugin.getServer().getPluginManager().registerEvents(new LookProtectionListener(), this.plugin);
    }

    /**
     * Called by ECS systems when an interaction has been spawned by NMS for the
     * given blockUniqueId. We use this to update the PlacedBlock.interactionEntityId
     * so rest of the plugin can observe the NMS entity once spawn is complete.
     */
    public void onInteractionSpawned(final java.util.UUID blockUniqueId, final java.util.UUID interactionUniqueId) {
        try {
            final PlacedBlock block = this.ecsState.getBlock(blockUniqueId);
            if (block != null) {
                block.setInteractionEntityId(interactionUniqueId);
                try {
                    final BlockDefinition def = this.blockConfigService.findBlock(block.type());
                    final World world = this.plugin.getServer().getWorld(block.world());
                if (def != null && world != null) {
                    this.visualSyncSystem.applyRealBlockModel(block, def, world);
                    applySchematicModel(block, def, world, false);
                }
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager entityManager) {
        this.entityManager = entityManager;
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
        final Location location = resolveRandomContextLocation(world, randomLocationContext, null);
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
        final BlockDefinition definition = this.blockConfigService.findBlock(type);
        if (definition == null) {
            return PlaceResult.error("Unknown block id: " + type);
        }

        if (this.ecsState.containsAt(world.getName(), x, y, z)) {
            return PlaceResult.error("Block already exists at that position");
        }

        final UUID uniqueId = UUID.randomUUID();
        final PlacedBlock placedBlock = new PlacedBlock(uniqueId, definition.id(), world.getName(), x, y, z, facing, LifecycleSystem.STATUS_ACTIVE);

        if (isChunkLoaded(world, x, z) && !spawnInteraction(placedBlock, definition, world)) {
            return PlaceResult.error("Failed to spawn interaction entity");
        }

        this.plugin.getServer().getPluginManager().callEvent(new BlockPlaceEvent(null, placedBlock, definition));

        this.ecsState.putBlock(placedBlock);
        if (persist) {
            this.persistenceSystem.persistBlockAsync(placedBlock);
        } else {
            this.transientBlocks.add(uniqueId);
        }
        if (suppressDead) {
            this.suppressDeadHologram.add(uniqueId);
        }
        if (isChunkLoaded(world, x, z)) {
            this.hologramRuntimeService.showActive(placedBlock, definition);
        }
        return PlaceResult.success(placedBlock);
    }

    public void registerNodeBlock(final UUID blockUniqueId) {
        if (blockUniqueId == null) {
            return;
        }
        this.transientBlocks.add(blockUniqueId);
        this.suppressDeadHologram.add(blockUniqueId);
    }

    public void unregisterNodeBlock(final UUID blockUniqueId) {
        if (blockUniqueId == null) {
            return;
        }
        this.transientBlocks.remove(blockUniqueId);
        this.suppressDeadHologram.remove(blockUniqueId);
        this.nodeRandomLocationContexts.remove(blockUniqueId);
    }

    public PlacedBlock findPlacedBlock(final UUID uniqueId) {
        PlacedBlock block = this.ecsState.getBlock(uniqueId);
        if (block != null) {
            return block;
        }
        block = this.dataCache.getBlock(uniqueId);
        return block;
    }

    public boolean removeById(final UUID uniqueId) {
        final PlacedBlock block = this.ecsState.getBlock(uniqueId);
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
        final PlacedBlock placedBlock = this.ecsState.blockAt(world.getName(), x, y, z);
        if (placedBlock == null) {
            return false;
        }
        if (!placedBlock.type().equalsIgnoreCase(type)) {
            return false;
        }

        final BlockDefinition definition = this.blockConfigService.findBlock(placedBlock.type());
        if (definition != null) {
            final World blockWorld = this.plugin.getServer().getWorld(placedBlock.world());
            if (blockWorld != null) {
                this.visualSyncSystem.clearRealBlockModel(placedBlock, definition, blockWorld);
                clearSchematicModel(placedBlock, blockWorld);
            }
        }
        despawnInteraction(placedBlock);
        cancelRespawnTask(placedBlock.uniqueId());
        this.ecsState.removeBlock(placedBlock.uniqueId());
        this.plugin.getServer().getPluginManager().callEvent(new BlockRemoveEvent(null, placedBlock));
        this.hologramRuntimeService.remove(placedBlock);
        if (!isTransient(placedBlock.uniqueId())) {
            this.persistenceSystem.deleteBlockAsync(placedBlock.uniqueId());
            this.persistenceSystem.deleteRespawnAsync(placedBlock.uniqueId());
        }
        unregisterNodeBlock(placedBlock.uniqueId());
        return true;
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

        final PlacedBlock block = this.ecsState.getBlock(uniqueId);
        if (block == null) {
            return null;
        }
        return processMiningClick(block, player, clickType);
    }

    public Component handleLegacyFallbackInteraction(final Player player, final String clickType) {
        final double reach = Math.max(1.5D, this.plugin.getConfig().getDouble("interaction.legacy-reach", 6.0D));
        final LegacyInteractionHit hit = findLegacyAabbHit(player, reach);
        if (hit == null) {
            return null;
        }
        return processMiningClick(hit.block(), player, clickType);
    }

    void restoreFromPersistence(final List<PlacedBlock> persistedBlocks) {
        for (final PlacedBlock block : persistedBlocks) {
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world == null) {
                // logging removed: skipping persisted block because world is missing
                continue;
            }

            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                // logging removed: skipping persisted block because type is missing
                continue;
            }

            this.ecsState.putBlock(block);
            if (this.lifecycleSystem.isActive(block)) {
                if (!isChunkLoaded(world, block.x(), block.z())) {
                    continue;
                }
                scheduleRestoreActiveBlock(block, definition, world);
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
                this.scheduler.runAtLocationLater(blockLocation(world, block), () -> showDeadOrRemoveSuppressed(block, definition, seconds), 20L);
            }
            scheduleRespawn(block, world, delay);
        }
    }

    private void scheduleRestoreActiveBlock(final PlacedBlock block, final BlockDefinition definition, final World world) {
        this.scheduler.runAtLocationLater(blockLocation(world, block), () -> {
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            if (!isChunkLoaded(world, block.x(), block.z())) {
                return;
            }
            if (spawnInteraction(block, definition, world)) {
                this.hologramRuntimeService.showActive(block, definition);
            }
        }, 20L);
    }

    private static Location blockLocation(final World world, final PlacedBlock block) {
        return new Location(world, block.x(), block.y(), block.z());
    }

    public List<String> blockIds() {
        return new ArrayList<>(this.blockConfigService.blockIds());
    }

    public List<PlacedBlock> placedBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(this.ecsState.blocks()));
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
    }

    public void syncFakeBlocksForPlayerChunkWindow(final Player player) {
        final int chunkX = player.getLocation().getChunk().getX();
        final int chunkZ = player.getLocation().getChunk().getZ();
        final Collection<PlacedBlock> candidateBlocks = this.ecsState.blocksInChunkWindow(
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
    }

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.hologramRuntimeService.handleViewerQuit(playerUniqueId);
        this.nmsAdapter.clearPacketHologramCacheForPlayer(playerUniqueId);
        // Clear dead schematics for the quitting player so their client doesn't
        // retain stale fake blocks during the disconnect edge case
        final Player player = this.plugin.getServer().getPlayer(playerUniqueId);
        if (player != null) {
            for (final PlacedBlock block : this.ecsState.blocks()) {
                if (!this.lifecycleSystem.isRespawning(block)) continue;
                if (!block.world().equals(player.getWorld().getName())) continue;
                final BlockDefinition def = this.blockConfigService.findBlock(block.type());
                if (def == null || !def.schematicsEnabled() || def.schematicsDeadFile() == null || def.schematicsDeadFile().isBlank()) continue;
                try {
                    this.schematicService.clearSchematicForPlayer(block.uniqueId().toString(), player);
                } catch (final Throwable ignored) {
                }
            }
        }
        // If we applied a debuff to this player, remove it and clear tracking.
        try {
            this.lookDebuffed.remove(playerUniqueId);
        } catch (final Throwable ignored) {
        }
    }

    void shutdown() {
        stopMiningProgressResetTask();
        stopLookRaytraceTask();
        final boolean serverStopping = Bukkit.isStopping();
        for (final PlacedBlock block : this.ecsState.blocks()) {
            cancelRespawnTask(block.uniqueId());
            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            final World world = this.plugin.getServer().getWorld(block.world());
            if (!serverStopping && definition != null && world != null) {
                this.visualSyncSystem.clearRealBlockModel(block, definition, world);
                clearSchematicModel(block, world);
            }
            despawnInteraction(block);
        }
        this.hologramRuntimeService.shutdown();
        this.schematicService.clearAll();
        this.ecsState.clear();
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
        // Clear tracking set; we do not apply persistent potion effects so nothing to remove
        try {
            this.lookDebuffed.clear();
        } catch (final Throwable ignored) {
        }
    }

    /**
     * Periodic check: for each online player, perform a raytrace against PlacedBlocks
     * (using existing legacy AABB raytrace logic). If the player is currently looking at
     * a PlacedBlock, apply an invisible Mining Fatigue (SLOW_DIGGING) effect at a very
     * high amplifier to prevent block breaking. Remove the effect when they stop looking.
     */
    private void checkLookRaytrace() {
        final double defaultReach = Math.max(1.5D, this.plugin.getConfig().getDouble("interaction.reach", 6.0D));

        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            try {
                final LegacyInteractionHit hit = findLegacyAabbHit(player, defaultReach);
                if (hit != null) {
                    // Mark player as protected (server-side) so we can cancel digging/break events
                    if (!this.lookDebuffed.contains(player.getUniqueId())) {
                        this.lookDebuffed.add(player.getUniqueId());
                    }
                } else {
                    // Remove protection when not looking anymore
                    this.lookDebuffed.remove(player.getUniqueId());
                }
            } catch (final Throwable ignored) {
            }
        }
    }

    ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        final List<PlacedBlock> persistedBlocks = new ArrayList<>(this.ecsState.snapshot()).stream()
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
        for (final PlacedBlock block : this.ecsState.blocksInChunk(world.getName(), chunkX, chunkZ)) {
            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                continue;
            }
            if (this.lifecycleSystem.isActive(block)) {
                if (spawnInteraction(block, definition, world)) {
                    this.hologramRuntimeService.showActive(block, definition);
                }
                continue;
            }

            if (this.lifecycleSystem.isRespawning(block)) {
                final long secondsLeft = Math.max(0L, (block.respawnAt() == null ? 0L : block.respawnAt() - System.currentTimeMillis()) / 1000L);
                showDeadOrRemoveSuppressed(block, definition, secondsLeft);
            }
        }
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedBlock block : this.ecsState.blocksInChunk(world.getName(), chunkX, chunkZ)) {
            despawnInteraction(block);
            this.hologramRuntimeService.remove(block);
            this.visualSyncSystem.clearBreakAnimation(world, block);
            clearSchematicModel(block, world);
        }
    }

    private Component processMiningClick(final PlacedBlock block, final Player player, final String clickType) {
        if (!this.lifecycleSystem.isActive(block)) {
            return this.blockConfigService.messageComponent("blocks.not_active", "&c[MMOBlock] Block is not active.");
        }

        final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
        if (definition == null) {
            return this.blockConfigService.messageComponent("blocks.config_missing", "&c[MMOBlock] Block config missing.");
        }

        if (!checkConditions(definition, player)) {
            return Component.empty();
        }

        final ItemStack item = player.getInventory().getItemInMainHand();
        final ToolAction action = this.blockConfigService.resolveToolAction(definition, item.getType(), clickType);
        if (action == null) {
            // If the player's tool is not allowed for this block, do not show the "too_fast" throttling
            // message. Return the explicit tool-not-allowed message first.
            return this.blockConfigService.messageComponent("blocks.tool_not_allowed", "&c[MMOBlock] Tool is not allowed for this block.");
        }

        // Only check throttling for valid tool actions. This prevents showing the
        // "too_fast" message when the tool is not permitted for the block.
        if (isThrottled(block.uniqueId(), player.getUniqueId())) {
            return this.blockConfigService.messageComponent("blocks.too_fast", "&c[MMOBlock] Slow down a bit.");
        }

        playConfiguredSound(player.getWorld(), block, definition.soundOnClick());

        applyDurability(item, action.decreaseDurability());
        final int progress = this.miningSystem.incrementProgress(block.uniqueId(), player.getUniqueId(), System.currentTimeMillis());
        if (definition.breakAnimation()) {
            this.visualSyncSystem.sendBreakAnimation(block, action, progress, false);
        }
        // spawn break particles on each click if enabled in the definition
        if (definition.particleBreak()) {
            spawnBreakParticles(block, definition);
        }
        final BlockMineEvent mineEvent = new BlockMineEvent(player, block, definition, clickType, progress, action.clickNeeded(), false);
        this.plugin.getServer().getPluginManager().callEvent(mineEvent);

        if (progress < action.clickNeeded()) {
            final String progressBar = renderProgressBar(progress, action.clickNeeded());
            this.hologramRuntimeService.showProgress(block, definition, progressBar, progress, action.clickNeeded());
            final Map<String, String> placeholders = new HashMap<>();
            placeholders.put("{progress}", String.valueOf(progress));
            placeholders.put("{needed}", String.valueOf(action.clickNeeded()));
            placeholders.put("{progress_bar}", progressBar);
            return this.blockConfigService.messageComponent(
                    "blocks.mining_progress",
                    "&e[MMOBlock] Mining progress: {progress}/{needed} &7[{progress_bar}&7]",
                    placeholders
            );
        }

        this.miningSystem.clearProgress(block.uniqueId(), player.getUniqueId());
        handleBlockBreak(block, definition, action, player);
        return this.blockConfigService.messageComponent(
                "blocks.broken",
                "&a[MMOBlock] Block broken. Respawning in {respawn}s",
                Map.of("{respawn}", String.valueOf(definition.respawnTimeSeconds()))
        );
    }

    private boolean checkConditions(final BlockDefinition definition, final Player player) {
        final List<ConditionDefinition> conditions = definition.conditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (final ConditionDefinition condition : conditions) {
            if (condition == null) {
                continue;
            }
            final String type = condition.type() == null ? "" : condition.type().toLowerCase(java.util.Locale.ROOT);
            if (!"placeholder".equals(type) && !"placholder".equals(type)) {
                continue;
            }
            if (ConditionEvaluator.isMet(this.plugin, player, condition)) {
                continue;
            }
            sendConditionTitle(player, condition);
            return false;
        }
        return true;
    }

    private void sendConditionTitle(final Player player, final ConditionDefinition condition) {
        if (player == null || condition == null) {
            return;
        }
        final String titleRaw = ConditionEvaluator.resolvePlaceholder(this.plugin, player, condition.sendTitle());
        final String subtitleRaw = ConditionEvaluator.resolvePlaceholder(this.plugin, player, condition.sendSubtitle());
        final Component title = (titleRaw == null || titleRaw.isBlank()) ? Component.empty() : TextColor.toComponent(titleRaw);
        final Component subtitle = (subtitleRaw == null || subtitleRaw.isBlank()) ? Component.empty() : TextColor.toComponent(subtitleRaw);
        if (title.equals(Component.empty()) && subtitle.equals(Component.empty())) {
            return;
        }
        player.showTitle(Title.title(title, subtitle));
    }

    private void handleBlockBreak(final PlacedBlock block, final BlockDefinition definition, final ToolAction action, final Player player) {
        final BlockMineEvent completeEvent = new BlockMineEvent(player, block, definition, action.clickType(), action.clickNeeded(), action.clickNeeded(), true);
        this.plugin.getServer().getPluginManager().callEvent(completeEvent);
        this.miningSystem.clearAllProgress(block.uniqueId());
        this.dropSystem.executeDrops(block, action, player);
        if (definition.breakAnimation()) {
            this.visualSyncSystem.sendBreakAnimation(block, action, action.clickNeeded(), true);
        }
        playConfiguredSound(player.getWorld(), block, definition.soundOnDead());
        spawnBreakParticles(block, definition);

        this.lifecycleSystem.markRespawning(block);
        if (!isTransient(block.uniqueId())) {
            this.persistenceSystem.persistBlockAsync(block);
        }

        final World world = this.plugin.getServer().getWorld(block.world());
        if (world != null) {
            this.visualSyncSystem.clearRealBlockModel(block, definition, world);
            clearSchematicModel(block, world);
        }
        despawnInteraction(block);
        if (world != null && definition.schematicsEnabled() && definition.schematicsDeadFile() != null && !definition.schematicsDeadFile().isBlank()) {
            applySchematicModel(block, definition, world, true);
        }
        if (shouldSuppressDeadHologram(block.uniqueId())) {
            this.hologramRuntimeService.remove(block);
        } else {
            this.hologramRuntimeService.showDead(block, definition, definition.respawnTimeSeconds());
        }
        final long respawnAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds());
        if (!isTransient(block.uniqueId())) {
            this.persistenceSystem.upsertRespawnAsync(block.uniqueId(), respawnAt);
        }

        if (world != null) {
            scheduleRespawn(block, world, TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds()));
        }
    }

    private void showDeadOrRemoveSuppressed(final PlacedBlock block, final BlockDefinition definition, final long seconds) {
        if (shouldSuppressDeadHologram(block.uniqueId())) {
            this.hologramRuntimeService.remove(block);
            return;
        }
        this.hologramRuntimeService.showDead(block, definition, seconds);
    }

    private void scheduleRespawn(final PlacedBlock block, final World world, final long delayMillis) {
        final long respawnAtMs = System.currentTimeMillis() + delayMillis;
        block.setRespawnAt(respawnAtMs);
        this.respawnSystem.schedule(
                block,
                delayMillis,
                () -> {
                    final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
                    if (definition != null
                            && this.lifecycleSystem.isRespawning(block)
                            && isChunkLoaded(world, block.x(), block.z())
                            && this.hologramRuntimeService.hasNearbyPlayers(block, DEAD_UPDATE_NEARBY_RADIUS)) {
                        if (!shouldSuppressDeadHologram(block.uniqueId())) {
                            this.hologramRuntimeService.updateDeadRespawnTime(block, definition);
                        }
                    }
                },
                () -> {
                    final BlockDefinition latestDefinition = this.blockConfigService.findBlock(block.type());
                    if (latestDefinition == null) {
                        cleanupMissingDefinition(block);
                        return;
                    }

                    final RespawnTarget respawnTarget = resolveRespawnTarget(block, latestDefinition, world);
                    if (respawnTarget != null) {
                        final double oldX = block.x();
                        final double oldY = block.y();
                        final double oldZ = block.z();
                        block.setCurrentLocation(
                                respawnTarget.location().getX(),
                                respawnTarget.location().getY(),
                                respawnTarget.location().getZ()
                        );
                        if (respawnTarget.facing() != null) {
                            // TODO: PlacedBlock currently does not expose a setter for facing.
                            // If PlacedBlock#setFacing(String) is added, set the computed facing here:
                            // block.setFacing(respawnTarget.facing());
                        }
                        this.ecsState.updateBlockPosition(block, oldX, oldY, oldZ);
                    }

                    if (!isChunkLoaded(world, block.x(), block.z())) {
                        this.lifecycleSystem.markActive(block);
                        block.setRespawnAt(null);
                        if (!isTransient(block.uniqueId())) {
                            this.persistenceSystem.persistBlockAsync(block);
                            this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
                        }
                        return;
                    }

                    if (spawnInteraction(block, latestDefinition, world)) {
                        this.lifecycleSystem.markActive(block);
                        block.setRespawnAt(null);
                        if (!isTransient(block.uniqueId())) {
                            this.persistenceSystem.persistBlockAsync(block);
                            this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
                        }
                        this.hologramRuntimeService.showActive(block, latestDefinition);
                        playConfiguredSound(world, block, latestDefinition.soundOnRespawn());
                        if (latestDefinition.breakAnimation()) {
                            this.visualSyncSystem.clearBreakAnimation(world, block);
                        }
                        this.plugin.getServer().getPluginManager().callEvent(new BlockRespawnEvent(block, latestDefinition));
                    }
                }
        );
    }


    private void applyDurability(final ItemStack item, final int decreaseDurability) {
        if (decreaseDurability <= 0 || item.getType().getMaxDurability() <= 0) {
            return;
        }

        final ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        final int nextDamage = damageable.getDamage() + decreaseDurability;
        if (nextDamage >= item.getType().getMaxDurability()) {
            item.setAmount(Math.max(0, item.getAmount() - 1));
            return;
        }

        damageable.setDamage(nextDamage);
        item.setItemMeta(meta);
    }

    private boolean spawnInteraction(final PlacedBlock placedBlock, final BlockDefinition definition, final World world) {
        final Location location = new Location(world, placedBlock.x() + 0.5D, placedBlock.y(), placedBlock.z() + 0.5D);
        try {

            if (placedBlock.interactionEntityId() != null) {
                this.nmsAdapter.removeInteraction(world, placedBlock.interactionEntityId());
                placedBlock.setInteractionEntityId(null);
            }
            removeDuplicateTaggedInteractions(world, location, placedBlock.uniqueId());

            // If ECS is available, create an ECS entity representing this interaction
            java.util.UUID ecsEntityId = null;
            if (this.entityManager != null) {
                ecsEntityId = me.chyxelmc.mmoblock.nmsloader.utils.NmsEcsUtils.createInteractionEntity(
                        this.entityManager,
                        location,
                        (float) definition.hitboxWidth(),
                        (float) definition.hitboxHeight(),
                        this.uniqueIdKey,
                        placedBlock.uniqueId()
                );

                // If configured to let the ECS systems manage actual NMS spawning, skip
                // immediate NMS spawn here and let InteractionSpawnSystem perform it on
                // the next tick. This keeps deterministic lifecycle in ECS while
                // allowing the plugin to opt-in via config.
                // Default to ECS-managed spawn if config key is missing; allow opt-out
                final boolean ecsManagedSpawn = this.plugin.getConfig().getBoolean("ecs.spawn-managed", false);
                if (ecsManagedSpawn) {
                    // We created the ECS entity; the InteractionSpawnSystem will attempt
                    // to spawn the NMS interaction and update the InteractionComponent.
                    // Ensure fake-block is sent immediately even though NMS spawn is
                    // deferred to the ECS system — this prevents players nearby from
                    // missing the fake-block on respawn.
                    try {
                        this.visualSyncSystem.applyRealBlockModel(placedBlock, definition, world);
                    } catch (final Throwable ignored) {
                    }
                    applySchematicModel(placedBlock, definition, world, false);
                    return true;
                }
            }

            final NmsAdapter.SpawnResult spawnResult = this.nmsAdapter.spawnInteraction(
                    world,
                    location,
                    (float) definition.hitboxWidth(),
                    (float) definition.hitboxHeight(),
                    this.uniqueIdKey,
                    placedBlock.uniqueId()
            );
            if (!spawnResult.success() || spawnResult.interactionUniqueId() == null) {
                this.plugin.getLogger().warning(
                        "Failed to spawn interaction for block " + placedBlock.uniqueId()
                                + " at " + world.getName() + " "
                                + placedBlock.x() + "," + placedBlock.y() + "," + placedBlock.z()
                                + " reason=" + spawnResult.reason()
                );
                // If ECS entity was created but spawn failed, remove ECS entity to avoid orphan
                if (ecsEntityId != null) {
                    try {
                        this.entityManager.removeEntity(ecsEntityId);
                    } catch (final Throwable ignored) {
                    }
                }
                return false;
            }

            placedBlock.setInteractionEntityId(spawnResult.interactionUniqueId());
            // If ECS present, update the InteractionComponent.spawnedInteraction with the NMS entity UUID
            if (this.entityManager != null && ecsEntityId != null) {
                final me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent comp =
                        this.entityManager.getComponent(ecsEntityId, me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent.class);
                if (comp != null) {
                    try {
                        comp.setSpawnedInteraction(spawnResult.interactionUniqueId());
                    } catch (final Throwable ignored) {
                    }
                }
            }

            this.visualSyncSystem.applyRealBlockModel(placedBlock, definition, world);
            applySchematicModel(placedBlock, definition, world, false);
            // logging removed: spawned interaction info
            return true;
        } catch (final RuntimeException exception) {
            // logging removed: exception during spawnInteraction
            return false;
        }
    }

    private void applySchematicModel(final PlacedBlock block, final BlockDefinition definition, final World world, final boolean dead) {
        if (definition == null || !definition.schematicsEnabled()) return;
        try {
            // Clear any existing schematic (normal or dead) to prevent stale blocks
            // from the previous state lingering visually
            clearSchematicModel(block, world);
            this.schematicService.showSchematic(
                    block.uniqueId().toString(),
                    definition,
                    world,
                    block.x(),
                    block.y(),
                    block.z(),
                    dead
            );
        } catch (final Throwable ignored) {
        }
    }

    private void clearSchematicModel(final PlacedBlock block, final World world) {
        if (world == null) return;
        try {
            this.schematicService.clearSchematic(block.uniqueId().toString(), world);
        } catch (final Throwable ignored) {
        }
    }

    private void despawnInteraction(final PlacedBlock block) {
        if (block.interactionEntityId() == null) {
            return;
        }
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }
        final NmsAdapter.RemoveResult removeResult = this.nmsAdapter.removeInteraction(world, block.interactionEntityId());
        if (!removeResult.success()) {
            // logging removed: failed to remove interaction entity
            final Entity entity = world.getEntity(block.interactionEntityId());
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            return;
        }

        // Also remove any ECS entity that represents this interaction
        if (this.entityManager != null) {
            try {
                for (final java.util.UUID ecsId : this.entityManager.allEntities()) {
                    final me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent comp =
                            this.entityManager.getComponent(ecsId, me.chyxelmc.mmoblock.nmsloader.ecs.components.InteractionComponent.class);
                    if (comp == null) continue;
                    if (comp.blockUniqueId() != null && comp.blockUniqueId().equals(block.uniqueId())) {
                        try {
                            this.entityManager.removeEntity(ecsId);
                        } catch (final Throwable ignored) {
                        }
                        break;
                    }
                }
            } catch (final Throwable ignored) {
            }
        }

        block.setInteractionEntityId(null);
    }

    private void cleanupMissingDefinition(final PlacedBlock block) {
        cancelRespawnTask(block.uniqueId());
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world != null) {
            final BlockDefinition existing = this.blockConfigService.findBlock(block.type());
            if (existing != null) {
                this.visualSyncSystem.clearRealBlockModel(block, existing, world);
            }
            clearSchematicModel(block, world);
        }
        despawnInteraction(block);
        this.ecsState.removeBlock(block.uniqueId());
        this.hologramRuntimeService.remove(block);
        this.persistenceSystem.deleteBlockAsync(block.uniqueId());
        this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
        // logging removed: removed block because definition is missing after reload
    }

    private void cancelRespawnTask(final UUID uniqueId) {
        this.respawnSystem.cancel(uniqueId);
    }

    private boolean isThrottled(final UUID blockId, final UUID playerId) {
        final long now = System.currentTimeMillis();
        final long minDelay = this.blockConfigService.interactionThrottleMs();
        return this.miningSystem.isThrottled(blockId, playerId, now, minDelay);
    }

    private String renderProgressBar(final int progress, final int needed) {
        final int length = Math.max(1, this.plugin.getConfig().getInt("progressbar.barlength", 16));
        final String done = this.plugin.getConfig().getString("progressbar.progressing", "&a|");
        final String left = this.plugin.getConfig().getString("progressbar.noprogress", "&7|");
        final int completed = Math.min(length, (int) Math.round((progress / (double) needed) * length));
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < completed; i++) {
            out.append(done);
        }
        for (int i = completed; i < length; i++) {
            out.append(left);
        }
        return TextColor.ampersandToMiniMessage(out.toString());
    }

    private void removeDuplicateTaggedInteractions(final World world, final Location location, final UUID blockUniqueId) {
        for (final Entity entity : world.getNearbyEntities(location, 1.5D, 2.0D, 1.5D, candidate -> candidate instanceof Interaction)) {
            final Interaction interaction = (Interaction) entity;
            final String raw = interaction.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
            if (raw == null || !raw.equals(blockUniqueId.toString())) {
                continue;
            }
            interaction.remove();
        }
    }

    private LegacyInteractionHit findLegacyAabbHit(final Player player, final double maxDistance) {
        final World world = player.getWorld();
        final Vector origin = player.getEyeLocation().toVector();
        final Vector direction = player.getEyeLocation().getDirection().normalize();
        final double maxDistanceSquared = maxDistance * maxDistance;

        LegacyInteractionHit nearest = null;
        for (final PlacedBlock block : this.ecsState.blocks()) {
            if (!world.getName().equals(block.world())) {
                continue;
            }
            if (!this.lifecycleSystem.isActive(block)) {
                continue;
            }

            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                continue;
            }

            final Location center = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
            if (center.distanceSquared(player.getLocation()) > maxDistanceSquared + 4.0D) {
                continue;
            }

            final BoundingBox box = interactionBoundingBox(center, definition);
            final var hit = box.rayTrace(origin, direction, maxDistance);
            if (hit == null) {
                continue;
            }

            final double distance = hit.getHitPosition().distance(origin);
            if (nearest == null || distance < nearest.distance()) {
                nearest = new LegacyInteractionHit(block, distance);
            }
        }

        return nearest;
    }

    private BoundingBox interactionBoundingBox(final Location center, final BlockDefinition definition) {
        final double width = Math.max(0.25D, definition.hitboxWidth());
        final double height = Math.max(0.25D, definition.hitboxHeight());
        final double half = width / 2.0D;
        return new BoundingBox(
                center.getX() - half,
                center.getY(),
                center.getZ() - half,
                center.getX() + half,
                center.getY() + height,
                center.getZ() + half
        );
    }


    private boolean almostEquals(final double a, final double b) {
        return Math.abs(a - b) < 0.000001D;
    }

    private boolean isChunkLoaded(final World world, final double x, final double z) {
        final int chunkX = (int) Math.floor(x) >> 4;
        final int chunkZ = (int) Math.floor(z) >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    private void playConfiguredSound(final World world, final PlacedBlock block, final Sound sound) {
        if (sound == null) {
            return;
        }
        world.playSound(blockCenterLocation(block, world), sound, 1.0F, 1.0F);
    }

    private void spawnBreakParticles(final PlacedBlock block, final BlockDefinition definition) {
        if (!definition.particleBreak()) {
            return;
        }
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }
        final Material material = this.visualSyncSystem.resolveParticleMaterial(definition);
        if (material == null) {
            return;
        }
        
        final Location loc = blockCenterLocation(block, world);
        
        if (material.isBlock()) {
            try {
                world.spawnParticle(
                        Particle.BLOCK_DUST,
                        loc,
                        24,
                        0.35D,
                        0.35D,
                        0.35D,
                        0.0D,
                        material.createBlockData()
                );
            } catch (final Throwable t) {
                // Ignore
            }
        }
    }

    private Location blockCenterLocation(final PlacedBlock block, final World world) {
        return new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D);
    }

    private void startMiningProgressResetTask() {
        stopMiningProgressResetTask();
        this.miningProgressResetTask = this.scheduler.runTimer(
                this::resetInactiveMiningProgress,
                MINING_PROGRESS_RESET_CHECK_TICKS,
                MINING_PROGRESS_RESET_CHECK_TICKS
        );
    }

    private void stopMiningProgressResetTask() {
        if (this.miningProgressResetTask != null) {
            this.miningProgressResetTask.cancel();
            this.miningProgressResetTask = null;
        }
    }

    private void resetInactiveMiningProgress() {
        final long now = System.currentTimeMillis();
        for (final UUID blockId : this.miningSystem.activeMiningBlockIds()) {
            final PlacedBlock block = this.ecsState.getBlock(blockId);
            if (block == null) {
                this.miningSystem.clearAllProgress(blockId);
                continue;
            }
            if (!this.lifecycleSystem.isActive(block)) {
                continue;
            }
            if (this.miningSystem.evictInactiveProgress(block.uniqueId(), now, MINING_PROGRESS_RESET_TIMEOUT_MS).isEmpty()) {
                continue;
            }

            // If there is still active progress for this block after eviction, skip cleanup.
            if (this.miningSystem.hasAnyProgress(block.uniqueId())) {
                continue;
            }

            // No active player interactions remain; clear any visible break animation
            // so clients don't keep showing a partial crack after inactivity.
            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world != null && definition != null) {
                try {
                    this.visualSyncSystem.clearBreakAnimation(world, block);
                } catch (final Throwable ignored) {
                }
                this.hologramRuntimeService.showActive(block, definition);
            }
        }
    }

    private record RespawnTarget(Location location, String facing) {}

    public record RandomLocationContext(
            double originX,
            double originY,
            double originZ,
            boolean enabled,
            double radius,
            boolean closest,
            double centerDistance
    ) {}

    private RespawnTarget resolveRespawnTarget(final PlacedBlock block, final BlockDefinition definition, final World world) {
        final RandomLocationContext nodeContext = this.nodeRandomLocationContexts.get(block.uniqueId());
        if (nodeContext != null) {
            final Location location = resolveRandomContextLocation(world, nodeContext, block.uniqueId());
            if (location != null) {
                final String facing = resolveRandomFacing(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                return new RespawnTarget(location, facing);
            }
            final Location fallback = findSafeBlockLocation(
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
            final Location safeOrigin = findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false);
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

            final Location safe = findSafeBlockLocation(world, targetBlockX, originBlockY, targetBlockZ, block.uniqueId(), false);
            if (safe != null) {
                final String facing = resolveRandomFacing(world, (int) safe.getX(), (int) safe.getY(), (int) safe.getZ());
                return new RespawnTarget(safe, facing);
            }
        }

        final Location safeOrigin = findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, block.uniqueId(), false);
        final Location loc = safeOrigin != null
                ? safeOrigin
                : new Location(world, originBlockX, originBlockY, originBlockZ);
        return new RespawnTarget(loc, block.facing());
    }

    private Location resolveRandomContextLocation(
            final World world,
            final RandomLocationContext context,
            final UUID excludingBlockId
    ) {
        if (world == null || context == null) {
            return null;
        }

        final int originBlockX = (int) Math.floor(context.originX());
        final int originBlockY = (int) Math.floor(context.originY());
        final int originBlockZ = (int) Math.floor(context.originZ());
        if (!context.enabled() || context.radius() <= 0.0D) {
            return findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, excludingBlockId, context.closest());
        }

        final double radius = Math.max(0.0D, context.radius());
        final double centerDistance = Math.max(0.0D, context.centerDistance());
        for (int attempt = 0; attempt < RANDOM_LOCATION_MAX_ATTEMPTS; attempt++) {
            final double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            final double minDistance = Math.min(radius, centerDistance);
            final double distance = minDistance + (Math.sqrt(ThreadLocalRandom.current().nextDouble()) * Math.max(0.0D, radius - minDistance));
            final int targetBlockX = originBlockX + (int) Math.round(Math.cos(angle) * distance);
            final int targetBlockZ = originBlockZ + (int) Math.round(Math.sin(angle) * distance);
            if (horizontalDistanceSquared(targetBlockX, targetBlockZ, originBlockX, originBlockZ) < centerDistance * centerDistance) {
                continue;
            }

            final Location safe = findSafeBlockLocation(world, targetBlockX, originBlockY, targetBlockZ, excludingBlockId, context.closest());
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    private Location findSafeBlockLocation(
            final World world,
            final int blockX,
            final int baseY,
            final int blockZ,
            final UUID excludingBlockId,
            final boolean requireClosestHorizontalBlock
    ) {
        if (!isOwnedByCurrentRegion(new Location(world, blockX, baseY, blockZ))) {
            return null;
        }
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight();
        final int startY = Math.max(minY, baseY);
        final int topY = maxY - 2;

        for (int y = startY; y <= topY; y++) {
            final Block feet = world.getBlockAt(blockX, y, blockZ);
            final Block head = world.getBlockAt(blockX, y + 1, blockZ);
            clearSnowLayer(feet);
            clearSnowLayer(head);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }

            final int groundedY = resolveGroundedSpawnY(world, blockX, y, blockZ, minY);
            if (groundedY < 0) {
                continue;
            }
            // Reject if hemmed in on 3+ horizontal sides
            if (isHemmedIn(world, blockX, groundedY, blockZ)) {
                continue;
            }
            if (requireClosestHorizontalBlock && !hasHorizontalClosestBlock(world, blockX, groundedY, blockZ)) {
                continue;
            }
            if (isTooCloseToPlacedBlock(world.getName(), blockX, groundedY, blockZ, excludingBlockId)) {
                continue;
            }
            if (hasBlockingEntityAt(world, blockX, groundedY, blockZ)) {
                continue;
            }
            return new Location(world, blockX, groundedY, blockZ);
        }
        return null;
    }

    private int resolveGroundedSpawnY(final World world, final int blockX, final int initialY, final int blockZ, final int minY) {
        if (isGroundSupport(world, blockX, initialY, blockZ)) {
            return initialY;
        }

        // If support below is AIR, try dropping down up to 3 blocks to find ground support.
        final int maxDownAttempts = 3;
        for (int down = 1; down <= maxDownAttempts; down++) {
            final int candidateY = initialY - down;
            if (candidateY < minY) {
                return -1;
            }

            final Block feet = world.getBlockAt(blockX, candidateY, blockZ);
            final Block head = world.getBlockAt(blockX, candidateY + 1, blockZ);
            clearSnowLayer(feet);
            clearSnowLayer(head);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            if (isGroundSupport(world, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        return -1;
    }

    private boolean isHemmedIn(final World world, final int blockX, final int spawnY, final int blockZ) {
        // Check unique horizontal directions (N/S/E/W) for blocking at feet or head level
        final int[][] offsets = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

        int blockedDirections = 0;
        for (final int[] offset : offsets) {
            final Block feetAdj = world.getBlockAt(blockX + offset[0], spawnY + offset[1], blockZ + offset[2]);
            final Block headAdj = world.getBlockAt(blockX + offset[0], spawnY + 1 + offset[1], blockZ + offset[2]);
            final boolean feetBlocked = !feetAdj.getType().isAir() && !feetAdj.isPassable();
            final boolean headBlocked = !headAdj.getType().isAir() && !headAdj.isPassable();
            if (feetBlocked || headBlocked) {
                blockedDirections++;
            }
        }
        // Reject if 3 or all 4 horizontal directions are blocked
        return blockedDirections >= 3;
    }

    private void clearSnowLayer(final Block block) {
        if (block != null && isOwnedByCurrentRegion(block.getLocation()) && block.getType() == Material.SNOW) {
            block.setType(Material.AIR, false);
        }
    }

    private static boolean isOwnedByCurrentRegion(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            final java.lang.reflect.Method method = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return Boolean.TRUE.equals(method.invoke(null, location));
        } catch (final NoSuchMethodException ignored) {
            return true;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private double horizontalDistanceSquared(final int x, final int z, final int otherX, final int otherZ) {
        final double dx = (x + 0.5D) - (otherX + 0.5D);
        final double dz = (z + 0.5D) - (otherZ + 0.5D);
        return (dx * dx) + (dz * dz);
    }

    private boolean hasHorizontalClosestBlock(final World world, final int blockX, final int spawnY, final int blockZ) {
        final int[][] offsets = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (final int[] offset : offsets) {
            if (isSolidAt(world, blockX + offset[0], spawnY, blockZ + offset[1])
                    || isSolidAt(world, blockX + offset[0], spawnY + 1, blockZ + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooCloseToPlacedBlock(
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID excludingBlockId
    ) {
        final double minDistanceSquared = NODE_RANDOM_MIN_BLOCK_DISTANCE * NODE_RANDOM_MIN_BLOCK_DISTANCE;
        final double centerX = blockX + 0.5D;
        final double centerZ = blockZ + 0.5D;
        for (final PlacedBlock placedBlock : this.ecsState.blocks()) {
            if (excludingBlockId != null && excludingBlockId.equals(placedBlock.uniqueId())) {
                continue;
            }
            if (!worldName.equals(placedBlock.world())) {
                continue;
            }
            if (Math.abs(placedBlock.y() - blockY) > 2.0D) {
                continue;
            }
            final double otherX = Math.floor(placedBlock.x()) + 0.5D;
            final double otherZ = Math.floor(placedBlock.z()) + 0.5D;
            final double dx = centerX - otherX;
            final double dz = centerZ - otherZ;
            if ((dx * dx) + (dz * dz) < minDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBlockingEntityAt(final World world, final int blockX, final int blockY, final int blockZ) {
        final BoundingBox spawnBox = new BoundingBox(
                blockX,
                blockY,
                blockZ,
                blockX + 1.0D,
                blockY + 2.0D,
                blockZ + 1.0D
        );
        final Location center = new Location(world, blockX + 0.5D, blockY + 1.0D, blockZ + 0.5D);
        for (final Entity entity : world.getNearbyEntities(center, 1.0D, 1.5D, 1.0D)) {
            if (!entity.isValid() || entity.isDead()) {
                continue;
            }
            if (entity instanceof Interaction) {
                continue;
            }
            try {
                if (entity.getBoundingBox().overlaps(spawnBox)) {
                    return true;
                }
            } catch (final Throwable ignored) {
                return true;
            }
        }
        return false;
    }

    private String resolveRandomFacing(final World world, final int blockX, final int spawnY, final int blockZ) {
        final boolean northBlocked = isSolidAt(world, blockX, spawnY, blockZ - 1)
                || isSolidAt(world, blockX, spawnY + 1, blockZ - 1);
        final boolean southBlocked = isSolidAt(world, blockX, spawnY, blockZ + 1)
                || isSolidAt(world, blockX, spawnY + 1, blockZ + 1);
        final boolean eastBlocked  = isSolidAt(world, blockX + 1, spawnY, blockZ)
                || isSolidAt(world, blockX + 1, spawnY + 1, blockZ);
        final boolean westBlocked  = isSolidAt(world, blockX - 1, spawnY, blockZ)
                || isSolidAt(world, blockX - 1, spawnY + 1, blockZ);

        final java.util.List<String> candidates = new java.util.ArrayList<>();

        if (northBlocked) candidates.add("south");
        if (southBlocked) candidates.add("north");
        if (eastBlocked)  candidates.add("west");
        if (westBlocked)  candidates.add("east");

        if (candidates.isEmpty()) {
            candidates.add("north");
            candidates.add("south");
            candidates.add("east");
            candidates.add("west");
            candidates.add("up");
            candidates.add("down");
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean isSolidAt(final World world, final int x, final int y, final int z) {
        final Block b = world.getBlockAt(x, y, z);
        return !b.getType().isAir() && !b.isPassable();
    }

    private boolean isGroundSupport(final World world, final int blockX, final int spawnY, final int blockZ) {
        if (spawnY - 1 < world.getMinHeight()) {
            return false;
        }
        final Block support = world.getBlockAt(blockX, spawnY - 1, blockZ);
        clearSnowLayer(support);
        if (support.getType().isAir()) {
            return false;
        }
        // Reject vegetation as ground — block would spawn on/inside a plant
        if (VEGETATION_MATERIALS.contains(support.getType())) {
            return false;
        }
        return true;
    }

    private record LegacyInteractionHit(PlacedBlock block, double distance) {
    }

    public boolean isPlayerLookProtected(final Player player) {
        if (player == null) return false;
        return this.lookDebuffed.contains(player.getUniqueId());
    }

    /**
     * Listener that cancels digging/break events for players present in the lookDebuffed set.
     * This prevents block destruction server-side while avoiding client-side mining fatigue visuals.
     */
    private final class LookProtectionListener implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onBlockDamage(final BlockDamageEvent event) {
            try {
                final org.bukkit.block.Block block = event.getBlock();
                // If this block position corresponds to a PlacedBlock (fake block), prevent digging
                if (BlockRuntimeService.this.ecsState.containsAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
                    event.setCancelled(true);
                }
            } catch (final Throwable ignored) {
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onBlockBreak(final BlockBreakEvent event) {
            try {
                final org.bukkit.block.Block block = event.getBlock();
                // Prevent breaking of fake blocks (treat them like bedrock)
                if (BlockRuntimeService.this.ecsState.containsAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
                    event.setCancelled(true);
                }
            } catch (final Throwable ignored) {
            }
        }

        // Only cancel block-damage/break events; interactions should still be handled
        // by the interaction listeners so players can interact with fake blocks normally.
    }


    public record PlaceResult(boolean success, String message, PlacedBlock placedBlock) {

        public static PlaceResult success(final PlacedBlock placedBlock) {
            return new PlaceResult(true, "", placedBlock);
        }

        public static PlaceResult error(final String message) {
            return new PlaceResult(false, message, null);
        }
    }

    public record ReconcileResult(int reboundInteractions, int cleanedMissingDefinitions, int rescheduledRespawns, int failedRebinds) {
    }
}
