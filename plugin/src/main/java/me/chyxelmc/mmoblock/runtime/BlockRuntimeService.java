package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.utils.ClientProtocolUtils;
import me.chyxelmc.mmoblock.runtime.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.runtime.ecs.system.DropSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.MiningSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceReadSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.ReconcileSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.RespawnSystem;
import me.chyxelmc.mmoblock.runtime.ecs.system.VisualSyncSystem;
import me.chyxelmc.mmoblock.utils.TextColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
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
    private final BlockConfigService blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final HologramRuntimeService hologramRuntimeService;
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
    private BukkitTask miningProgressResetTask;

    public BlockRuntimeService(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final BlockConfigService blockConfigService,
            final PersistenceReadSystem persistenceReadSystem,
            final PersistenceSystem persistenceSystem
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.blockConfigService = blockConfigService;
        this.persistenceReadSystem = persistenceReadSystem;
        this.persistenceSystem = persistenceSystem;
        this.hologramRuntimeService = new HologramRuntimeService(plugin, nmsAdapter);
        this.uniqueIdKey = new NamespacedKey(plugin, "unique_id");
        this.miningSystem = new MiningSystem(this.ecsState);
        this.respawnSystem = new RespawnSystem(plugin, this.ecsState);
        this.visualSyncSystem = new VisualSyncSystem(plugin, nmsAdapter);
        this.dropSystem = new DropSystem(plugin, blockConfigService);
        this.lifecycleSystem = new LifecycleSystem();
        this.reconcileSystem = new ReconcileSystem();
        startMiningProgressResetTask();
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

        this.ecsState.putBlock(placedBlock);
        this.persistenceSystem.persistBlockAsync(placedBlock);
        if (isChunkLoaded(world, x, z)) {
            this.hologramRuntimeService.showActive(placedBlock, definition);
        }
        return PlaceResult.success(placedBlock);
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
            }
        }
        despawnInteraction(placedBlock);
        cancelRespawnTask(placedBlock.uniqueId());
        this.ecsState.removeBlock(placedBlock.uniqueId());
        this.hologramRuntimeService.remove(placedBlock);
        this.persistenceSystem.deleteBlockAsync(placedBlock.uniqueId());
        this.persistenceSystem.deleteRespawnAsync(placedBlock.uniqueId());
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
        if (!ClientProtocolUtils.isLegacyClientBelow_1_19_4(player)) {
            return null;
        }

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
                this.plugin.getLogger().warning("Skipping persisted block " + block.uniqueId() + " because world is missing: " + block.world());
                continue;
            }

            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            if (definition == null) {
                this.plugin.getLogger().warning("Skipping persisted block " + block.uniqueId() + " because type is missing: " + block.type());
                continue;
            }

            this.ecsState.putBlock(block);
            if (this.lifecycleSystem.isActive(block)) {
                if (!isChunkLoaded(world, block.x(), block.z())) {
                    continue;
                }
                if (!spawnInteraction(block, definition, world)) {
                    this.plugin.getLogger().warning("Failed to restore interaction for block " + block.uniqueId());
                } else {
                    this.hologramRuntimeService.showActive(block, definition);
                }
                continue;
            }

            final Long respawnAt = this.persistenceReadSystem.findRespawnAt(block.uniqueId());
            if (respawnAt == null) {
                this.lifecycleSystem.markActive(block);
                this.persistenceSystem.persistBlockAsync(block);
                if (isChunkLoaded(world, block.x(), block.z()) && spawnInteraction(block, definition, world)) {
                    this.hologramRuntimeService.showActive(block, definition);
                }
                continue;
            }

            this.lifecycleSystem.markRespawning(block);
            this.persistenceSystem.persistBlockAsync(block);
            final long delay = Math.max(1L, respawnAt - System.currentTimeMillis());
            if (isChunkLoaded(world, block.x(), block.z())) {
                this.hologramRuntimeService.showDead(block, definition, TimeUnit.MILLISECONDS.toSeconds(delay));
            }
            scheduleRespawn(block, world, delay);
        }
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
    }

    void shutdown() {
        stopMiningProgressResetTask();
        for (final PlacedBlock block : this.ecsState.blocks()) {
            cancelRespawnTask(block.uniqueId());
            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            final World world = this.plugin.getServer().getWorld(block.world());
            if (definition != null && world != null) {
                this.visualSyncSystem.clearRealBlockModel(block, definition, world);
            }
            despawnInteraction(block);
        }
        this.hologramRuntimeService.shutdown();
        this.ecsState.clear();
    }

    ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        return this.reconcileSystem.reconcile(
                new ArrayList<>(this.ecsState.snapshot()),
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
                        this.hologramRuntimeService.showDead(block, definition, TimeUnit.MILLISECONDS.toSeconds(delayMillis));
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
                this.hologramRuntimeService.showDead(block, definition, secondsLeft);
            }
        }
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        for (final PlacedBlock block : this.ecsState.blocksInChunk(world.getName(), chunkX, chunkZ)) {
            despawnInteraction(block);
            this.hologramRuntimeService.remove(block);
            this.visualSyncSystem.clearBreakAnimation(world, block);
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
        if (progress < action.clickNeeded()) {
            final String progressBar = renderProgressBar(progress, action.clickNeeded());
            this.hologramRuntimeService.showProgress(block, definition, progressBar);
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

    private void handleBlockBreak(final PlacedBlock block, final BlockDefinition definition, final ToolAction action, final Player player) {
        this.miningSystem.clearAllProgress(block.uniqueId());
        this.dropSystem.executeDrops(block, action, player);
        if (definition.breakAnimation()) {
            this.visualSyncSystem.sendBreakAnimation(block, action, action.clickNeeded(), true);
        }
        playConfiguredSound(player.getWorld(), block, definition.soundOnDead());
        spawnBreakParticles(block, definition);

        this.lifecycleSystem.markRespawning(block);
        this.persistenceSystem.persistBlockAsync(block);

        final World world = this.plugin.getServer().getWorld(block.world());
        if (world != null) {
            this.visualSyncSystem.clearRealBlockModel(block, definition, world);
        }
        despawnInteraction(block);
        this.hologramRuntimeService.showDead(block, definition, definition.respawnTimeSeconds());
        final long respawnAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds());
        this.persistenceSystem.upsertRespawnAsync(block.uniqueId(), respawnAt);

        if (world != null) {
            scheduleRespawn(block, world, TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds()));
        }
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
                        this.hologramRuntimeService.updateDeadRespawnTime(block, definition);
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
                        this.persistenceSystem.persistBlockAsync(block);
                        this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
                        return;
                    }

                    if (spawnInteraction(block, latestDefinition, world)) {
                        this.lifecycleSystem.markActive(block);
                        block.setRespawnAt(null);
                        this.persistenceSystem.persistBlockAsync(block);
                        this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
                        this.hologramRuntimeService.showActive(block, latestDefinition);
                        playConfiguredSound(world, block, latestDefinition.soundOnRespawn());
                        if (latestDefinition.breakAnimation()) {
                            this.visualSyncSystem.clearBreakAnimation(world, block);
                        }
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
                final boolean ecsManagedSpawn = this.plugin.getConfig().getBoolean("ecs.spawn-managed", true);
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
                this.plugin.getLogger().warning("Cannot spawn interaction at " + location + ": " + spawnResult.reason());
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
            this.plugin.getLogger().info(
                    "Spawned interaction=" + spawnResult.interactionUniqueId()
                            + " width=" + definition.hitboxWidth()
                            + " height=" + definition.hitboxHeight()
                            + " blockModel=" + (this.visualSyncSystem.usesRealBlockModel(definition) ? definition.realBlockMaterial() : "none")
            );
            return true;
        } catch (final RuntimeException exception) {
            this.plugin.getLogger().warning("Cannot spawn interaction at " + location + ": " + exception.getMessage());
            return false;
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
            this.plugin.getLogger().warning("Failed to remove interaction entity " + block.interactionEntityId() + ": " + removeResult.reason());
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
        }
        despawnInteraction(block);
        this.ecsState.removeBlock(block.uniqueId());
        this.hologramRuntimeService.remove(block);
        this.persistenceSystem.deleteBlockAsync(block.uniqueId());
        this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
        this.plugin.getLogger().warning("Removed block " + block.uniqueId() + " because definition is missing after reload: " + block.type());
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
        return TextColorUtil.ampersandToMiniMessage(out.toString());
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
        final Location loc = blockCenterLocation(block, world);
        try {
            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    24,
                    0.35D,
                    0.35D,
                    0.35D,
                    0.0D,
                    material.createBlockData()
            );
        } catch (final Throwable t) {
            try {
                // Fall back: spawn ITEM_CRACK using an ItemStack of the material.
                world.spawnParticle(
                        Particle.ITEM,
                        loc,
                        24,
                        0.35D,
                        0.35D,
                        0.35D,
                        0.0D,
                        new ItemStack(material)
                );
            } catch (final Throwable ignored) {
                // Give up silently; particle support may be unavailable on this server build.
            }
        }
    }

    private Location blockCenterLocation(final PlacedBlock block, final World world) {
        return new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D);
    }

    private void startMiningProgressResetTask() {
        stopMiningProgressResetTask();
        this.miningProgressResetTask = this.plugin.getServer().getScheduler().runTaskTimer(
                this.plugin,
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

    private RespawnTarget resolveRespawnTarget(final PlacedBlock block, final BlockDefinition definition, final World world) {
        final int originBlockX = (int) Math.floor(block.originX());
        final int originBlockY = (int) Math.floor(block.originY());
        final int originBlockZ = (int) Math.floor(block.originZ());

        if (!definition.randomLocationEnabled() || definition.randomLocationRadius() <= 0.0D) {
            final Location safeOrigin = findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ);
            final Location loc = safeOrigin != null
                    ? safeOrigin
                    : new Location(world, originBlockX, originBlockY, originBlockZ);
            return new RespawnTarget(loc, block.facing());
        }

        final double radius = definition.randomLocationRadius();
        final int maxAttempts = 24;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            final double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            final double distance = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * radius;
            final int targetBlockX = originBlockX + (int) Math.round(Math.cos(angle) * distance);
            final int targetBlockZ = originBlockZ + (int) Math.round(Math.sin(angle) * distance);

            final Location safe = findSafeBlockLocation(world, targetBlockX, originBlockY, targetBlockZ);
            if (safe != null) {
                final String facing = resolveRandomFacing(world, (int) safe.getX(), (int) safe.getY(), (int) safe.getZ());
                return new RespawnTarget(safe, facing);
            }
        }

        final Location safeOrigin = findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ);
        final Location loc = safeOrigin != null
                ? safeOrigin
                : new Location(world, originBlockX, originBlockY, originBlockZ);
        return new RespawnTarget(loc, block.facing());
    }

    private Location findSafeBlockLocation(final World world, final int blockX, final int baseY, final int blockZ) {
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight();
        final int startY = Math.max(minY, baseY);
        final int topY = maxY - 2;

        for (int y = startY; y <= topY; y++) {
            final Block feet = world.getBlockAt(blockX, y, blockZ);
            final Block head = world.getBlockAt(blockX, y + 1, blockZ);
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
