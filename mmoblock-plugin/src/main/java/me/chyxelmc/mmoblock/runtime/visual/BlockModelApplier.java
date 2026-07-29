package me.chyxelmc.mmoblock.runtime.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.api.integration.BetterModelIntegration;
import me.chyxelmc.mmoblock.api.integration.ModelEngineIntegration;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

/**
 * Handles application and removal of third-party block models (Schematics, BDEngine,
 * ModelEngine, BetterModel) and their collision blocks.
 *
 * <p>Extracted from {@link BlockRuntimeService} during the Phase 3 monolith
 * decomposition.</p>
 */
public final class BlockModelApplier {

    private static final String FOR_BLOCK_SUFFIX = "' for block ";

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final BlockStateRegistry stateRegistry;
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;

    // ModelEngine collision tracking
    private final Map<UUID, List<CollisionEntry>> modelEngineCollisions = new HashMap<>();

    // BetterModel collision tracking
    private final Map<UUID, List<CollisionEntry>> betterModelCollisions = new HashMap<>();

    // ModelEngine entity tracking — maps block UUID to Marker entity
    private final Map<UUID, org.bukkit.entity.Marker> modelEngineEntities = new ConcurrentHashMap<>();

    public BlockModelApplier(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final BlockStateRegistry stateRegistry,
            final SchematicService schematicService,
            final BdEngineService bdEngineService
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.stateRegistry = stateRegistry;
        this.schematicService = schematicService;
        this.bdEngineService = bdEngineService;
    }

    // -------------------------------------------------------------
    // Schematic model
    // -------------------------------------------------------------

    public void applySchematicModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world, final boolean dead) {
        if (definition == null || !definition.schematicsEnabled()) return;
        try {
            clearSchematicModel(block, world);
            this.schematicService.showSchematic(
                    block.uniqueId().toString(),
                    definition,
                    world,
                    dead ? block.originX() : block.x(),
                    dead ? block.originY() : block.y(),
                    dead ? block.originZ() : block.z(),
                    dead
            );
        } catch (final Throwable ignored) {
        }
    }

    public void clearSchematicModel(final PlacedBlockModel block, final World world) {
        if (world == null) return;
        try {
            this.schematicService.clearSchematic(block.uniqueId().toString(), world);
        } catch (final Throwable ignored) {
        }
    }

    // -------------------------------------------------------------
    // BDEngine model
    // -------------------------------------------------------------

    public void applyBdEngineModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (definition == null || !definition.bdengineEnabled()) return;
        try {
            this.bdEngineService.showModel(
                    new BdEngineService.PlacedBlockKey(block.uniqueId()),
                    definition,
                    world,
                    block.x(),
                    block.y(),
                    block.z()
            );
            playBdEngineAnimation(
                    block,
                    definition.bdengineOnSpawnAnimation(),
                    definition.bdengineOnSpawnTimelineLength(),
                    definition.bdengineOnSpawnAnimationMode()
            );
        } catch (final Throwable ignored) {
        }
    }

    public void clearBdEngineModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        try {
            this.bdEngineService.clearModel(block.uniqueId(), world);
        } catch (final Throwable ignored) {
        }
    }

    public void playBdEngineAnimation(final PlacedBlockModel block, final String animationName, final double timelineLengthSeconds, final String mode) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        try {
            this.bdEngineService.playAnimation(block.uniqueId(), animationName, timelineLengthSeconds, mode);
        } catch (final Throwable ignored) {
        }
    }

    // -------------------------------------------------------------
    // ModelEngine integration
    // -------------------------------------------------------------

    public void applyModelEngineModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (definition == null || !definition.modelEngineEnabled()) return;
        try {
            if (!ModelEngineIntegration.isAvailable()) return;
        } catch (final Throwable ignored) {
            return;
        }

        // Remove any existing Marker for this block before creating a new one
        clearModelEngineModel(block, world);

        final Location location = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
        final org.bukkit.entity.Marker marker = world.spawn(location, org.bukkit.entity.Marker.class);
        try {
            ModelEngineIntegration.showModel(
                    marker,
                    definition.modelEngineModelId(),
                    definition.modelEngineModelSize()
            );
            this.modelEngineEntities.put(block.uniqueId(), marker);
        } catch (final Throwable ex) {
            marker.remove();
            MMOBlockLogger.warning("[ModelEngine] Failed to show model '" + definition.modelEngineModelId()
                    + "' for block " + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void clearModelEngineModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        final org.bukkit.entity.Marker marker = this.modelEngineEntities.remove(block.uniqueId());
        if (marker == null) return;
        try {
            if (ModelEngineIntegration.isAvailable()) {
                ModelEngineIntegration.removeModel(marker);
            }
        } catch (final Throwable ignored) {
            // ModelEngine not available or integration error
        }
        if (marker.isValid()) {
            marker.remove();
        }
    }

    public void playModelEngineAnimation(final PlacedBlockModel block, final BlockDefinitionModel definition, final String animationName, final double lerpIn, final double lerpOut, final double speed) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        if (definition == null || !definition.modelEngineEnabled()) return;
        final org.bukkit.entity.Marker marker = this.modelEngineEntities.get(block.uniqueId());
        if (marker == null || !marker.isValid()) return;
        try {
            if (!ModelEngineIntegration.isAvailable()) return;
        } catch (final Throwable ignored) {
            return;
        }
        try {
            ModelEngineIntegration.playAnimation(
                    marker,
                    definition.modelEngineModelId(),
                    animationName,
                    lerpIn,
                    lerpOut,
                    speed
            );
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[ModelEngine] Failed to play animation '" + animationName
                    + "' for block " + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------
    // ModelEngine collision
    // -------------------------------------------------------------

    public void applyModelEngineCollision(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (block == null || definition == null) return;
        final List<String> positions = definition.modelEngineCollisionPositions();
        if (positions == null || positions.isEmpty()) return;
        final double x = block.x();
        final double y = block.y();
        final double z = block.z();
        final List<CollisionEntry> entries = new ArrayList<>();
        for (final String rawPosition : positions) {
            final int[] offset = parseBlockOffset(rawPosition);
            if (offset == null) continue;
            final int worldX = (int) Math.floor(x) + offset[0];
            final int worldY = (int) Math.floor(y) + offset[1];
            final int worldZ = (int) Math.floor(z) + offset[2];
            final Location location = new Location(world, worldX, worldY, worldZ);
            if (FakeBlockRegistry.contains(world.getName(), worldX, worldY, worldZ)) {
                continue;
            }
            this.nmsAdapter.showFakeBlock(world, location, Material.BARRIER);
            FakeBlockRegistry.add(world.getName(), worldX, worldY, worldZ, Material.BARRIER.name());
            entries.add(new CollisionEntry(block.uniqueId(), world.getName(), worldX, worldY, worldZ));
        }
        if (!entries.isEmpty()) {
            this.modelEngineCollisions.put(block.uniqueId(), entries);
            // Register collision positions in state registry for multi-block click detection
            for (final CollisionEntry entry : entries) {
                this.stateRegistry.addAdditionalPosition(entry.worldName(), entry.x(), entry.y(), entry.z(), block.uniqueId());
            }
        }
    }

    public void clearModelEngineCollision(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        final List<CollisionEntry> entries = this.modelEngineCollisions.remove(block.uniqueId());
        if (entries == null) return;
        for (final CollisionEntry entry : entries) {
            final World entryWorld = world != null && world.getName().equals(entry.worldName())
                    ? world
                    : this.plugin.getServer().getWorld(entry.worldName());
            if (entryWorld != null) {
                this.nmsAdapter.clearFakeBlock(entryWorld, new Location(entryWorld, entry.x(), entry.y(), entry.z()));
            }
            FakeBlockRegistry.remove(entry.worldName(), entry.x(), entry.y(), entry.z());
            this.stateRegistry.removeAdditionalPosition(entry.worldName(), entry.x(), entry.y(), entry.z());
        }
    }

    // -------------------------------------------------------------
    // BetterModel integration
    // -------------------------------------------------------------

    public void applyBetterModelModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (definition == null || !definition.betterModelEnabled()) return;
        try {
            if (!BetterModelIntegration.isAvailable()) return;
        } catch (final Throwable ignored) {
            return;
        }
        final Location location = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
        try {
            BetterModelIntegration.showModel(
                    null,
                    location,
                    definition.betterModelModelId(),
                    block.uniqueId(),
                    definition.betterModelModelSize()
            );
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[BetterModel] Failed to show model '" + definition.betterModelModelId()
                    + FOR_BLOCK_SUFFIX + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void clearBetterModelModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        try {
            if (!BetterModelIntegration.isAvailable()) return;
        } catch (final Throwable ignored) {
            return;
        }
        try {
            BetterModelIntegration.removeModel(block.uniqueId());
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[BetterModel] Failed to remove model for block "
                    + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void playBetterModelAnimation(final PlacedBlockModel block, final BlockDefinitionModel definition, final String animationName) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        if (definition == null || !definition.betterModelEnabled()) return;
        try {
            if (!BetterModelIntegration.isAvailable()) return;
        } catch (final Throwable ignored) {
            return;
        }
        try {
            final boolean played = BetterModelIntegration.playAnimation(
                    block.uniqueId(), animationName
            );
            if (!played) {
                MMOBlockLogger.warning("[BetterModel] Could not play animation '"
                        + animationName + FOR_BLOCK_SUFFIX + block.uniqueId()
                        + " — model '" + definition.betterModelModelId() + "' may not be attached");
            }
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[BetterModel] Failed to play animation '" + animationName
                    + FOR_BLOCK_SUFFIX + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------
    // BetterModel collision
    // -------------------------------------------------------------

    public void applyBetterModelCollision(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (block == null || definition == null) return;
        if (!definition.betterModelEnabled()) return;
        final List<String> positions = definition.betterModelCollisionPositions();
        if (positions == null || positions.isEmpty()) return;
        final double x = block.x();
        final double y = block.y();
        final double z = block.z();
        final List<CollisionEntry> entries = new ArrayList<>();
        for (final String rawPosition : positions) {
            final int[] offset = parseBlockOffset(rawPosition);
            if (offset == null) continue;
            final int worldX = (int) Math.floor(x) + offset[0];
            final int worldY = (int) Math.floor(y) + offset[1];
            final int worldZ = (int) Math.floor(z) + offset[2];
            final Location location = new Location(world, worldX, worldY, worldZ);
            if (FakeBlockRegistry.contains(world.getName(), worldX, worldY, worldZ)) {
                continue;
            }
            this.nmsAdapter.showFakeBlock(world, location, Material.BARRIER);
            FakeBlockRegistry.add(world.getName(), worldX, worldY, worldZ, Material.BARRIER.name());
            entries.add(new CollisionEntry(block.uniqueId(), world.getName(), worldX, worldY, worldZ));
        }
        if (!entries.isEmpty()) {
            this.betterModelCollisions.put(block.uniqueId(), entries);
            // Register collision positions in state registry for multi-block click detection
            for (final CollisionEntry entry : entries) {
                this.stateRegistry.addAdditionalPosition(entry.worldName(), entry.x(), entry.y(), entry.z(), block.uniqueId());
            }
        }
    }

    public void clearBetterModelCollision(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        final List<CollisionEntry> entries = this.betterModelCollisions.remove(block.uniqueId());
        if (entries == null) return;
        for (final CollisionEntry entry : entries) {
            final World entryWorld = world != null && world.getName().equals(entry.worldName())
                    ? world
                    : this.plugin.getServer().getWorld(entry.worldName());
            if (entryWorld != null) {
                this.nmsAdapter.clearFakeBlock(entryWorld, new Location(entryWorld, entry.x(), entry.y(), entry.z()));
            }
            FakeBlockRegistry.remove(entry.worldName(), entry.x(), entry.y(), entry.z());
            this.stateRegistry.removeAdditionalPosition(entry.worldName(), entry.x(), entry.y(), entry.z());
        }
    }

    // -------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------

    public static int[] parseBlockOffset(final String raw) {
        if (raw == null || raw.isBlank()) return null;
        final String[] parts = raw.split(",");
        if (parts.length < 3) return null;
        try {
            return new int[]{
                    (int) Math.round(Double.parseDouble(parts[0].trim())),
                    (int) Math.round(Double.parseDouble(parts[1].trim())),
                    (int) Math.round(Double.parseDouble(parts[2].trim()))
            };
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Clear all collision blocks for shutdown.
     */
    public void clearAllCollisions() {
        for (final Map.Entry<UUID, List<CollisionEntry>> entry : new ArrayList<>(this.modelEngineCollisions.entrySet())) {
            final World world = this.plugin.getServer().getWorld(entry.getValue().isEmpty() ? "" : entry.getValue().get(0).worldName());
            if (world != null) {
                for (final CollisionEntry c : entry.getValue()) {
                    this.nmsAdapter.clearFakeBlock(world, new Location(world, c.x(), c.y(), c.z()));
                    FakeBlockRegistry.remove(c.worldName(), c.x(), c.y(), c.z());
                    this.stateRegistry.removeAdditionalPosition(c.worldName(), c.x(), c.y(), c.z());
                }
            }
        }
        this.modelEngineCollisions.clear();
        for (final Map.Entry<UUID, List<CollisionEntry>> entry : new ArrayList<>(this.betterModelCollisions.entrySet())) {
            final World world = this.plugin.getServer().getWorld(entry.getValue().isEmpty() ? "" : entry.getValue().get(0).worldName());
            if (world != null) {
                for (final CollisionEntry c : entry.getValue()) {
                    this.nmsAdapter.clearFakeBlock(world, new Location(world, c.x(), c.y(), c.z()));
                    FakeBlockRegistry.remove(c.worldName(), c.x(), c.y(), c.z());
                    this.stateRegistry.removeAdditionalPosition(c.worldName(), c.x(), c.y(), c.z());
                }
            }
        }
        this.betterModelCollisions.clear();
    }

    /**
     * A collision block entry tracking a fake barrier block placed for model collision.
     */
    public record CollisionEntry(UUID blockUniqueId, String worldName, int x, int y, int z) {
    }
}
