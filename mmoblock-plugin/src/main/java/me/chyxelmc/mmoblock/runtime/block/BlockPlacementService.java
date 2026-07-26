package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.block.RespawnScheduler;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.visual.BdEngineService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.SchematicService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlockPlacementService {

    private final MMOBlock plugin;
    private final BlockConfigLoader blockConfigService;
    private final BlockStateRegistry stateRegistry;
    private final PersistenceSystem persistenceSystem;
    private final RespawnScheduler respawnSystem;
    private final BlockLifecycleState lifecycleSystem;
    private final BlockVisualSyncService visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final BlockModelApplier modelApplier;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockEventDispatcher eventDispatcher;
    private final BlockRandomLocationResolver randomLocationResolver;
    private final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator;
    private final BlockLifecycleOrchestrator lifecycleOrchestrator;
    private final BlockRespawnOrchestrator respawnOrchestrator;
    private final BlockMiningOrchestrator miningOrchestrator;
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;
    private final Set<UUID> transientBlocks;
    private final Set<UUID> suppressDeadHologram;
    private final Map<UUID, RandomLocationContext> nodeRandomLocationContexts;

    public BlockPlacementService(
            final MMOBlock plugin,
            final BlockConfigLoader blockConfigService,
            final BlockStateRegistry stateRegistry,
            final PersistenceSystem persistenceSystem,
            final RespawnScheduler respawnSystem,
            final BlockLifecycleState lifecycleSystem,
            final BlockVisualSyncService visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService,
            final BlockModelApplier modelApplier,
            final BlockInteractionOrchestrator interactionOrchestrator,
            final BlockEventDispatcher eventDispatcher,
            final BlockRandomLocationResolver randomLocationResolver,
            final BlockChunkLifecycleOrchestrator chunkLifecycleOrchestrator,
            final BlockLifecycleOrchestrator lifecycleOrchestrator,
            final BlockRespawnOrchestrator respawnOrchestrator,
            final BlockMiningOrchestrator miningOrchestrator,
            final SchematicService schematicService,
            final BdEngineService bdEngineService,
            final Set<UUID> transientBlocks,
            final Set<UUID> suppressDeadHologram,
            final Map<UUID, RandomLocationContext> nodeRandomLocationContexts
    ) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
        this.stateRegistry = stateRegistry;
        this.persistenceSystem = persistenceSystem;
        this.respawnSystem = respawnSystem;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
        this.modelApplier = modelApplier;
        this.interactionOrchestrator = interactionOrchestrator;
        this.eventDispatcher = eventDispatcher;
        this.randomLocationResolver = randomLocationResolver;
        this.chunkLifecycleOrchestrator = chunkLifecycleOrchestrator;
        this.lifecycleOrchestrator = lifecycleOrchestrator;
        this.respawnOrchestrator = respawnOrchestrator;
        this.miningOrchestrator = miningOrchestrator;
        this.schematicService = schematicService;
        this.bdEngineService = bdEngineService;
        this.transientBlocks = transientBlocks;
        this.suppressDeadHologram = suppressDeadHologram;
        this.nodeRandomLocationContexts = nodeRandomLocationContexts;
    }

    // ============================================================
    // Place / Node Block
    // ============================================================

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
            final RandomLocationContext randomLocationContext,
            final UUID excludingBlockId
    ) {
        final Location location = this.randomLocationResolver.resolveRandomContextLocation(world, randomLocationContext, excludingBlockId);
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

    // ============================================================
    // Node Block Registration
    // ============================================================

    public void registerNodeBlock(final UUID blockUniqueId) {
        this.lifecycleOrchestrator.registerNodeBlock(blockUniqueId);
    }

    public void unregisterNodeBlock(final UUID blockUniqueId) {
        this.lifecycleOrchestrator.unregisterNodeBlock(blockUniqueId);
        this.nodeRandomLocationContexts.remove(blockUniqueId);
    }

    // ============================================================
    // Remove
    // ============================================================

    public boolean remove(final String type, final World world, final double x, final double y, final double z) {
        return this.lifecycleOrchestrator.remove(type, world, x, y, z);
    }

    public boolean removeById(final UUID uniqueId) {
        final PlacedBlockModel block = this.stateRegistry.getBlock(uniqueId);
        if (block == null) {
            return false;
        }
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return false;
        }
        return remove(block.type(), world, block.x(), block.y(), block.z());
    }

    // ============================================================
    // Chunk Lifecycle
    // ============================================================

    public void handleChunkLoad(final World world, final int chunkX, final int chunkZ) {
        this.chunkLifecycleOrchestrator.handleChunkLoad(world, chunkX, chunkZ);
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        this.chunkLifecycleOrchestrator.handleChunkUnload(world, chunkX, chunkZ);
    }

    // ============================================================
    // Persistence Restore
    // ============================================================

    public void restoreFromPersistence(final List<PlacedBlockModel> persistedBlocks) {
        this.chunkLifecycleOrchestrator.restoreFromPersistence(persistedBlocks);
    }

    // ============================================================
    // Methods used by BlockManagementService and BlockServiceFactory
    // ============================================================

    public boolean spawnInteraction(final PlacedBlockModel placedBlock, final me.chyxelmc.mmoblock.model.BlockDefinitionModel definition, final World world) {
        return this.interactionOrchestrator.spawn(placedBlock, definition, world);
    }

    public void despawnInteraction(final PlacedBlockModel block) {
        this.interactionOrchestrator.despawn(block);
    }

    public void cleanupMissingDefinition(final PlacedBlockModel block) {
        this.lifecycleOrchestrator.cleanupMissingDefinition(block);
    }

    public void scheduleRespawn(final PlacedBlockModel block, final World world, final long delayMillis) {
        this.respawnOrchestrator.schedule(block, world, delayMillis);
    }

    public void showDeadOrRemoveSuppressed(final PlacedBlockModel block, final me.chyxelmc.mmoblock.model.BlockDefinitionModel definition, final long seconds) {
        this.respawnOrchestrator.showDeadOrRemoveSuppressed(block, definition, seconds);
    }

    public void cancelRespawnTask(final UUID uniqueId) {
        this.respawnSystem.cancel(uniqueId);
    }

    public boolean isTransient(final UUID uniqueId) {
        return this.transientBlocks.contains(uniqueId);
    }

    public boolean shouldSuppressDeadHologram(final UUID uniqueId) {
        return this.suppressDeadHologram.contains(uniqueId);
    }

    public void clearTransientState() {
        this.transientBlocks.clear();
        this.suppressDeadHologram.clear();
        this.nodeRandomLocationContexts.clear();
    }
}
