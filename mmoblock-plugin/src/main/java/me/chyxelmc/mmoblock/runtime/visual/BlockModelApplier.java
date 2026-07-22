package me.chyxelmc.mmoblock.runtime.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.api.integration.BetterModelIntegration;
import me.chyxelmc.mmoblock.api.integration.ModelEngineIntegration;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;

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
    private final SchematicService schematicService;
    private final BdEngineService bdEngineService;

    // ModelEngine collision tracking
    private final Map<UUID, List<CollisionEntry>> modelEngineCollisions = new HashMap<>();

    // BetterModel collision tracking
    private final Map<UUID, List<CollisionEntry>> betterModelCollisions = new HashMap<>();

    public BlockModelApplier(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final SchematicService schematicService,
            final BdEngineService bdEngineService
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
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
                    block.x(),
                    block.y(),
                    block.z(),
                    dead
            );
        } catch (final Exception ignored) {
        }
    }

    public void clearSchematicModel(final PlacedBlockModel block, final World world) {
        if (world == null) return;
        try {
            this.schematicService.clearSchematic(block.uniqueId().toString(), world);
        } catch (final Exception ignored) {
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
        } catch (final Exception ignored) {
        }
    }

    public void clearBdEngineModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        try {
            this.bdEngineService.clearModel(block.uniqueId(), world);
        } catch (final Exception ignored) {
        }
    }

    public void playBdEngineAnimation(final PlacedBlockModel block, final String animationName, final double timelineLengthSeconds, final String mode) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        try {
            this.bdEngineService.playAnimation(block.uniqueId(), animationName, timelineLengthSeconds, mode);
        } catch (final Exception ignored) {
        }
    }

    // -------------------------------------------------------------
    // ModelEngine integration
    // -------------------------------------------------------------

    public void applyModelEngineModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (definition == null || !definition.modelEngineEnabled()) return;
        if (!ModelEngineIntegration.isAvailable()) return;
        final Entity entity = world.getEntity(block.interactionEntityId());
        if (entity == null) {
            this.plugin.getLogger().warning("[ModelEngine] Entity not found for block " + block.uniqueId()
                    + " (id=" + block.interactionEntityId() + ")");
            return;
        }
        if (!(entity instanceof org.bukkit.entity.Interaction)) {
            this.plugin.getLogger().warning("[ModelEngine] Entity " + block.interactionEntityId()
                    + " is not an Interaction: " + entity.getType());
            return;
        }
        try {
            ModelEngineIntegration.showModel(
                    entity,
                    definition.modelEngineModelId(),
                    definition.modelEngineModelSize()
            );
            playModelEngineAnimation(
                    block,
                    definition,
                    definition.modelEngineOnSpawnName(),
                    definition.modelEngineOnSpawnLerpIn(),
                    definition.modelEngineOnSpawnLerpOut(),
                    definition.modelEngineOnSpawnSpeed()
            );
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[ModelEngine] Failed to show model '" + definition.modelEngineModelId()
                    + "' on " + block.interactionEntityId() + ": " + ex.getMessage());
        }
    }

    public void clearModelEngineModel(final PlacedBlockModel block, final World world) {
        if (block == null || block.interactionEntityId() == null) return;
        if (!ModelEngineIntegration.isAvailable()) return;
        final Entity entity = world.getEntity(block.interactionEntityId());
        if (entity == null) return;
        try {
            ModelEngineIntegration.removeModel(entity);
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[ModelEngine] Failed to remove model from "
                    + block.interactionEntityId() + ": " + ex.getMessage());
        }
    }

    public void playModelEngineAnimation(final PlacedBlockModel block, final BlockDefinitionModel definition, final String animationName, final double lerpIn, final double lerpOut, final double speed) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        if (definition == null || !definition.modelEngineEnabled()) return;
        if (!ModelEngineIntegration.isAvailable()) return;
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) return;
        final Entity entity = world.getEntity(block.interactionEntityId());
        if (entity == null) return;
        try {
            ModelEngineIntegration.playAnimation(entity, definition.modelEngineModelId(), animationName, lerpIn, lerpOut, speed);
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[ModelEngine] Failed to play animation '" + animationName
                    + "' on " + block.interactionEntityId() + ": " + ex.getMessage());
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
            this.nmsAdapter.showFakeBlock(world, location, Material.BARRIER);
            entries.add(new CollisionEntry(block.uniqueId(), world.getName(), worldX, worldY, worldZ));
        }
        if (!entries.isEmpty()) {
            this.modelEngineCollisions.put(block.uniqueId(), entries);
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
        }
    }

    // -------------------------------------------------------------
    // BetterModel integration
    // -------------------------------------------------------------

    public void applyBetterModelModel(final PlacedBlockModel block, final BlockDefinitionModel definition, final World world) {
        if (definition == null || !definition.betterModelEnabled()) return;
        if (!BetterModelIntegration.isAvailable()) return;
        final Entity entity = world.getEntity(block.interactionEntityId());
        final Location location = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
        try {
            final boolean applied = BetterModelIntegration.showModel(
                    entity,
                    location,
                    definition.betterModelModelId(),
                    block.uniqueId(),
                    definition.betterModelModelSize()
            );
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[BetterModel] Failed to show model '" + definition.betterModelModelId()
                    + FOR_BLOCK_SUFFIX + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void clearBetterModelModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        if (!BetterModelIntegration.isAvailable()) return;
        try {
            BetterModelIntegration.removeModel(block.uniqueId());
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[BetterModel] Failed to remove model for block "
                    + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void playBetterModelAnimation(final PlacedBlockModel block, final BlockDefinitionModel definition, final String animationName) {
        if (block == null || animationName == null || animationName.isBlank()) return;
        if (definition == null || !definition.betterModelEnabled()) return;
        if (!BetterModelIntegration.isAvailable()) return;
        try {
            final boolean played = BetterModelIntegration.playAnimation(
                    block.uniqueId(), animationName
            );
            if (!played) {
                this.plugin.getLogger().warning("[BetterModel] Could not play animation '"
                        + animationName + FOR_BLOCK_SUFFIX + block.uniqueId()
                        + " — model '" + definition.betterModelModelId() + "' may not be attached");
            }
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("[BetterModel] Failed to play animation '" + animationName
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
            this.nmsAdapter.showFakeBlock(world, location, Material.BARRIER);
            entries.add(new CollisionEntry(block.uniqueId(), world.getName(), worldX, worldY, worldZ));
        }
        if (!entries.isEmpty()) {
            this.betterModelCollisions.put(block.uniqueId(), entries);
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
                }
            }
        }
        this.modelEngineCollisions.clear();
        for (final Map.Entry<UUID, List<CollisionEntry>> entry : new ArrayList<>(this.betterModelCollisions.entrySet())) {
            final World world = this.plugin.getServer().getWorld(entry.getValue().isEmpty() ? "" : entry.getValue().get(0).worldName());
            if (world != null) {
                for (final CollisionEntry c : entry.getValue()) {
                    this.nmsAdapter.clearFakeBlock(world, new Location(world, c.x(), c.y(), c.z()));
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
