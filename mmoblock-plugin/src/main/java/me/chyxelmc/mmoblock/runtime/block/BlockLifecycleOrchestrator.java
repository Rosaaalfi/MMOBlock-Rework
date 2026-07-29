package me.chyxelmc.mmoblock.runtime.block;

import java.util.Set;
import java.util.UUID;

import org.bukkit.World;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.block.RespawnScheduler;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.runtime.block.PlaceResult;
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.ServerSideFakeBlockService;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;

public final class BlockLifecycleOrchestrator {

    private final MMOBlock plugin;
    private final BlockConfigLoader blockConfigService;
    private final PersistenceSystem persistenceSystem;
    private final BlockStateRegistry stateRegistry;
    private final RespawnScheduler respawnSystem;
    private final BlockLifecycleState lifecycleSystem;
    private final BlockVisualSyncService visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final ServerSideFakeBlockService serverSideFakeBlockService;
    private final BlockModelApplier modelApplier;
    private final BlockEventDispatcher eventDispatcher;
    private final Set<UUID> transientBlocks;
    private final Set<UUID> suppressDeadHologram;

    public BlockLifecycleOrchestrator(
            final MMOBlock plugin,
            final BlockConfigLoader blockConfigService,
            final PersistenceSystem persistenceSystem,
            final BlockStateRegistry stateRegistry,
            final RespawnScheduler respawnSystem,
            final BlockLifecycleState lifecycleSystem,
            final BlockVisualSyncService visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService,
            final ServerSideFakeBlockService serverSideFakeBlockService,
            final BlockModelApplier modelApplier,
            final BlockEventDispatcher eventDispatcher,
            final Set<UUID> transientBlocks,
            final Set<UUID> suppressDeadHologram
    ) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
        this.persistenceSystem = persistenceSystem;
        this.stateRegistry = stateRegistry;
        this.respawnSystem = respawnSystem;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
        this.serverSideFakeBlockService = serverSideFakeBlockService;
        this.modelApplier = modelApplier;
        this.eventDispatcher = eventDispatcher;
        this.transientBlocks = transientBlocks;
        this.suppressDeadHologram = suppressDeadHologram;
    }

    public PlaceResult place(
            final String type,
            final World world,
            final double x,
            final double y,
            final double z,
            final String facing,
            final boolean persist,
            final boolean suppressDead
    ) {
        final BlockDefinitionModel definition = this.blockConfigService.findBlock(type);
        if (definition == null) {
            return PlaceResult.error("Unknown block id: " + type);
        }

        if (this.stateRegistry.containsAt(world.getName(), x, y, z)) {
            return PlaceResult.error("Block already exists at that position");
        }

        final UUID uniqueId = UUID.randomUUID();
        final PlacedBlockModel placedBlock = new PlacedBlockModel(uniqueId, definition.id(), world.getName(), x, y, z, facing, BlockLifecycleState.STATUS_ACTIVE);

        this.stateRegistry.putBlock(placedBlock);

        if (isChunkLoaded(world, x, z)) {
            applyVisuals(placedBlock, definition, world);
            this.serverSideFakeBlockService.syncNearbyPlayers(
                    world,
                    new org.bukkit.Location(world, placedBlock.x() + 0.5D, placedBlock.y() + 0.5D, placedBlock.z() + 0.5D),
                    this.blockConfigService.realBlockRadiusSquared()
            );
        }

        this.eventDispatcher.callPlace(placedBlock, definition);
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

    public boolean remove(final String type, final World world, final double x, final double y, final double z) {
        final PlacedBlockModel placedBlock = this.stateRegistry.blockAt(world.getName(), x, y, z);
        if (placedBlock == null) {
            return false;
        }
        if (!placedBlock.type().equalsIgnoreCase(type)) {
            return false;
        }

        clearVisuals(placedBlock);
        this.respawnSystem.cancel(placedBlock.uniqueId());
        this.stateRegistry.removeBlock(placedBlock.uniqueId());
        this.eventDispatcher.callRemove(placedBlock);
        this.hologramRuntimeService.remove(placedBlock);
        if (!this.transientBlocks.contains(placedBlock.uniqueId())) {
            this.persistenceSystem.deleteBlockAsync(placedBlock.uniqueId());
            this.persistenceSystem.deleteRespawnAsync(placedBlock.uniqueId());
        }
        unregisterNodeBlock(placedBlock.uniqueId());
        return true;
    }

    public void cleanupMissingDefinition(final PlacedBlockModel block) {
        this.respawnSystem.cancel(block.uniqueId());
        clearVisuals(block);
        this.stateRegistry.removeBlock(block.uniqueId());
        this.hologramRuntimeService.remove(block);
        this.persistenceSystem.deleteBlockAsync(block.uniqueId());
        this.persistenceSystem.deleteRespawnAsync(block.uniqueId());
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
    }

    private void applyVisuals(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        this.visualSyncSystem.applyRealBlockModel(block, definition, world);
        this.modelApplier.applySchematicModel(block, definition, world, false);
        this.modelApplier.applyBdEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineModel(block, definition, world);
        this.modelApplier.applyModelEngineCollision(block, definition, world);
        this.modelApplier.applyBetterModelModel(block, definition, world);
        this.modelApplier.applyBetterModelCollision(block, definition, world);
    }

    private void clearVisuals(final PlacedBlockModel block) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }
        final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
        this.serverSideFakeBlockService.demoteBlock(block);
        // Remove from FakeBlockRegistry so the reconcile timer won't re-promote this block
        FakeBlockRegistry.remove(block.world(), (int) Math.floor(block.x()), (int) Math.floor(block.y()), (int) Math.floor(block.z()));
        FakeBlockRegistry.remove(block.world(), (int) Math.floor(block.originX()), (int) Math.floor(block.originY()), (int) Math.floor(block.originZ()));
        if (definition != null) {
            this.visualSyncSystem.clearRealBlockModel(block, definition, world);
        }
        this.modelApplier.clearSchematicModel(block, world);
        this.modelApplier.clearBdEngineModel(block, world);
        this.modelApplier.clearModelEngineModel(block, world);
        this.modelApplier.clearModelEngineCollision(block, world);
        this.modelApplier.clearBetterModelModel(block, world);
        this.modelApplier.clearBetterModelCollision(block, world);
    }

    private static boolean isChunkLoaded(final World world, final double x, final double z) {
        return world != null && world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }
}
