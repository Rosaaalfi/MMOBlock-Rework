package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class BlockRuntimeService {

    private static final double FAKE_BLOCK_SYNC_RADIUS_SQUARED = 128.0D * 128.0D;
    private static final long MINING_PROGRESS_RESET_TIMEOUT_MS = 5000L;
    private static final long MINING_PROGRESS_RESET_CHECK_TICKS = 20L;
    private static final int MOVE_SYNC_CHUNK_RADIUS = 1;
    private static final double DEAD_UPDATE_NEARBY_RADIUS = 16.0D;

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final BlockConfigService blockConfigService;
    private final PersistenceReadSystem persistenceReadSystem;
    private final PersistenceSystem persistenceSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final NamespacedKey uniqueIdKey;
    private final BlockEcsState ecsState = new BlockEcsState();
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

        if (isThrottled(block.uniqueId(), player.getUniqueId())) {
            return this.blockConfigService.messageComponent("blocks.too_fast", "&c[MMOBlock] Slow down a bit.");
        }

        final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
        if (definition == null) {
            return this.blockConfigService.messageComponent("blocks.config_missing", "&c[MMOBlock] Block config missing.");
        }

        final ItemStack item = player.getInventory().getItemInMainHand();
        final ToolAction action = this.blockConfigService.resolveToolAction(definition, item.getType(), clickType);
        if (action == null) {
            return this.blockConfigService.messageComponent("blocks.tool_not_allowed", "&c[MMOBlock] Tool is not allowed for this block.");
        }

        playConfiguredSound(player.getWorld(), block, definition.soundOnClick());

        applyDurability(item, action.decreaseDurability());
          final int progress = this.miningSystem.incrementProgress(block.uniqueId(), player.getUniqueId(), System.currentTimeMillis());
        if (definition.breakAnimation()) {
              this.visualSyncSystem.sendBreakAnimation(block, action, progress, false);
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
                return false;
            }

            placedBlock.setInteractionEntityId(spawnResult.interactionUniqueId());
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
         final long minDelay = this.plugin.getConfig().getLong("interactionThrottleMs", 50L);
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
        world.spawnParticle(
            Particle.BLOCK,
            blockCenterLocation(block, world),
            24,
            0.35D,
            0.35D,
            0.35D,
            material.createBlockData()
        );
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
            if (this.miningSystem.hasAnyProgress(block.uniqueId())) {
                continue;
            }

            final BlockDefinition definition = this.blockConfigService.findBlock(block.type());
            if (definition != null) {
                this.hologramRuntimeService.showActive(block, definition);
            }
        }
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