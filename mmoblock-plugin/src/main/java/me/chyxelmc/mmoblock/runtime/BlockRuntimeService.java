package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.BlockLookProtection;
import me.chyxelmc.mmoblock.runtime.block.BlockManagementService;
import me.chyxelmc.mmoblock.runtime.block.BlockPlacementService;
import me.chyxelmc.mmoblock.runtime.block.BlockQueryService;
import me.chyxelmc.mmoblock.runtime.block.PlaceResult;
import me.chyxelmc.mmoblock.runtime.block.ReconcileResult;
import me.chyxelmc.mmoblock.runtime.block.RandomLocationContext;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class BlockRuntimeService {

    private final BlockPlacementService placementService;
    private final BlockQueryService queryService;
    private final BlockManagementService managementService;
    private final BlockLookProtection lookProtection;
    private final NamespacedKey uniqueIdKey;

    public BlockRuntimeService(final BlockServiceFactory factory, final JavaPlugin plugin) {
        this.placementService = factory.getPlacementService();
        this.queryService = factory.getQueryService();
        this.managementService = factory.getManagementService();
        this.lookProtection = factory.getLookProtection();
        this.uniqueIdKey = factory.getUniqueIdKey();

        // Post-construction initialization (moved from old constructor)
        factory.getMiningProgressReset().start();
        this.managementService.startBackgroundTasks();
        plugin.getServer().getPluginManager().registerEvents(this.lookProtection, plugin);
    }

    // ============================================================
    // ECS / Entity Management
    // ============================================================

    public void onInteractionSpawned(final UUID blockUniqueId, final UUID interactionUniqueId) {
        this.managementService.onInteractionSpawned(blockUniqueId, interactionUniqueId);
    }

    public void setEntityManager(final EntityManager entityManager) {
        this.managementService.setEntityManager(entityManager);
    }

    // ============================================================
    // Placement
    // ============================================================

    public PlaceResult place(final String type, final World world, final double x, final double y, final double z, final String facing) {
        return this.placementService.place(type, world, x, y, z, facing);
    }

    public PlaceResult placeNodeBlock(final String type, final World world, final double x, final double y, final double z, final String facing) {
        return this.placementService.placeNodeBlock(type, world, x, y, z, facing);
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
        return this.placementService.placeNodeBlock(type, world, x, y, z, facing, randomLocationContext);
    }

    public PlaceResult placeRandomNodeBlock(
            final String type,
            final World world,
            final String facing,
            final RandomLocationContext randomLocationContext,
            final UUID excludingBlockId
    ) {
        return this.placementService.placeRandomNodeBlock(type, world, facing, randomLocationContext, excludingBlockId);
    }

    // ============================================================
    // Node Block Registration
    // ============================================================

    public void registerNodeBlock(final UUID blockUniqueId) {
        this.placementService.registerNodeBlock(blockUniqueId);
    }

    public void unregisterNodeBlock(final UUID blockUniqueId) {
        this.placementService.unregisterNodeBlock(blockUniqueId);
    }

    // ============================================================
    // Query
    // ============================================================

    public PlacedBlockModel findPlacedBlock(final UUID uniqueId) {
        return this.queryService.findPlacedBlock(uniqueId);
    }

    public UUID resolveBlockUniqueId(final Entity entity) {
        return this.queryService.resolveBlockUniqueId(entity);
    }

    public List<String> blockIds() {
        return this.queryService.blockIds();
    }

    public List<PlacedBlockModel> placedBlocks() {
        return this.queryService.placedBlocks();
    }

    public BlockStateRegistry stateRegistry() {
        return this.queryService.stateRegistry();
    }

    public boolean isPlayerLookProtected(final Player player) {
        return this.queryService.isPlayerLookProtected(player);
    }

    // ============================================================
    // Remove
    // ============================================================

    public boolean removeById(final UUID uniqueId) {
        return this.placementService.removeById(uniqueId);
    }

    public boolean removeByInteractionEntity(final Entity entity) {
        final UUID uniqueId = this.queryService.resolveBlockUniqueId(entity);
        if (uniqueId == null) {
            return false;
        }
        return this.placementService.removeById(uniqueId);
    }

    public boolean remove(final String type, final World world, final double x, final double y, final double z) {
        return this.placementService.remove(type, world, x, y, z);
    }

    // ============================================================
    // Interaction / Mining
    // ============================================================

    public Component handleInteraction(final Entity clickedEntity, final Player player, final String clickType) {
        return this.managementService.handleInteraction(clickedEntity, player, clickType);
    }

    public Component handleLegacyFallbackInteraction(final Player player, final String clickType) {
        return this.managementService.handleLegacyFallbackInteraction(player, clickType);
    }

    // ============================================================
    // Persistence Restore
    // ============================================================

    public void restoreFromPersistence(final List<PlacedBlockModel> persistedBlocks) {
        this.placementService.restoreFromPersistence(persistedBlocks);
    }

    // ============================================================
    // Sync
    // ============================================================

    public void syncFakeBlocksForPlayer(final Player player) {
        this.managementService.syncFakeBlocksForPlayer(player);
    }

    public void syncFakeBlocksForPlayerChunkWindow(final Player player) {
        this.managementService.syncFakeBlocksForPlayerChunkWindow(player);
    }

    // ============================================================
    // Player / Server Lifecycle
    // ============================================================

    public void handlePlayerQuit(final UUID playerUniqueId) {
        this.managementService.handlePlayerQuit(playerUniqueId);
    }

    public void shutdown() {
        this.managementService.shutdown();
    }

    public ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        return this.managementService.reconcileAfterConfigReload(rebindActiveInteractions);
    }

    // ============================================================
    // Chunk Lifecycle
    // ============================================================

    public void handleChunkLoad(final World world, final int chunkX, final int chunkZ) {
        this.placementService.handleChunkLoad(world, chunkX, chunkZ);
    }

    public void handleChunkUnload(final World world, final int chunkX, final int chunkZ) {
        this.placementService.handleChunkUnload(world, chunkX, chunkZ);
    }
}
