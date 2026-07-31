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
import org.bukkit.entity.Interaction;

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

    // External engine host tracking by block UUID.
    private final Map<UUID, Interaction> modelEngineEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Interaction> betterModelEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Long> betterModelRecoverySequences = new ConcurrentHashMap<>();

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
                    block.x(),
                    block.y(),
                    block.z(),
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
            if (!ModelEngineIntegration.isAvailable()) {
                MMOBlockLogger.warning("[ModelEngine] Cannot apply model '" + definition.modelEngineModelId()
                        + "' for block " + block.uniqueId() + ": "
                        + ModelEngineIntegration.availabilityFailure());
                return;
            }
        } catch (final Throwable exception) {
            MMOBlockLogger.warning("[ModelEngine] Availability check failed for model '"
                    + definition.modelEngineModelId() + "' and block " + block.uniqueId(), exception);
            return;
        }

        // Remove any existing engine host for this block before creating a new one.
        clearModelEngineModel(block, world);

        final Location location = modelLocation(world, block);
        final Interaction marker = spawnModelHost(location);
        try {
            final ModelEngineIntegration.ModelApplyResult result = ModelEngineIntegration.applyModel(
                    marker,
                    definition.modelEngineModelId(),
                    definition.modelEngineModelSize()
            );
            if (!result.applied()) {
                marker.remove();
                MMOBlockLogger.warning("[ModelEngine] Model ID '" + definition.modelEngineModelId()
                        + "' could not be applied for block " + block.uniqueId() + " at "
                        + formatLocation(location) + ": " + result.detail());
                return;
            }
            this.modelEngineEntities.put(block.uniqueId(), marker);
            MMOBlockLogger.debug("[ModelEngine] Applied model '" + definition.modelEngineModelId()
                    + "' (size=" + definition.modelEngineModelSize() + ") for block "
                    + block.uniqueId() + " at " + formatLocation(location) + "; " + result.detail());
        } catch (final Throwable ex) {
            marker.remove();
            MMOBlockLogger.warning("[ModelEngine] Failed to show model '" + definition.modelEngineModelId()
                    + "' for block " + block.uniqueId() + " at " + formatLocation(location), ex);
        }
    }

    public void clearModelEngineModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        final Interaction marker = this.modelEngineEntities.remove(block.uniqueId());
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
        final Interaction marker = this.modelEngineEntities.get(block.uniqueId());
        if (marker == null || !marker.isValid()) {
            MMOBlockLogger.debug("[ModelEngine] Animation '" + animationName + "' skipped for block "
                    + block.uniqueId() + ": no valid model host is tracked");
            return;
        }
        try {
            if (!ModelEngineIntegration.isAvailable()) {
                MMOBlockLogger.warning("[ModelEngine] Animation '" + animationName + "' cannot run: "
                        + ModelEngineIntegration.availabilityFailure());
                return;
            }
        } catch (final Throwable exception) {
            MMOBlockLogger.warning("[ModelEngine] Availability check failed before animation '"
                    + animationName + "' for block " + block.uniqueId(), exception);
            return;
        }
        try {
            final boolean playing = ModelEngineIntegration.playAnimation(
                    marker,
                    definition.modelEngineModelId(),
                    animationName,
                    lerpIn,
                    lerpOut,
                    speed
            );
            if (!playing) {
                MMOBlockLogger.warning("[ModelEngine] Animation '" + animationName + "' was not started for model '"
                        + definition.modelEngineModelId() + "' on block " + block.uniqueId()
                        + "; the model or animation is not registered");
            }
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[ModelEngine] Failed to play animation '" + animationName
                    + "' for block " + block.uniqueId(), ex);
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
        clearBetterModelModel(block, world);
        final Location location = modelLocation(world, block);
        final Interaction modelHost = spawnModelHost(location);
        try {
            final boolean applied = BetterModelIntegration.showModel(
                    modelHost,
                    location,
                    definition.betterModelModelId(),
                    block.uniqueId(),
                    definition.betterModelModelSize()
            );
            if (!applied) {
                modelHost.remove();
                MMOBlockLogger.warning("[BetterModel] Model ID '" + definition.betterModelModelId()
                        + "' was not found or could not be attached for block " + block.uniqueId());
                return;
            }
            this.betterModelEntities.put(block.uniqueId(), modelHost);
        } catch (final Throwable ex) {
            modelHost.remove();
            MMOBlockLogger.warning("[BetterModel] Failed to show model '" + definition.betterModelModelId()
                    + FOR_BLOCK_SUFFIX + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    public void clearBetterModelModel(final PlacedBlockModel block, final World world) {
        if (block == null) return;
        this.betterModelRecoverySequences.remove(block.uniqueId());
        final Interaction modelHost = this.betterModelEntities.remove(block.uniqueId());
        try {
            if (BetterModelIntegration.isAvailable()) {
                BetterModelIntegration.removeModel(block.uniqueId());
            }
        } catch (final Throwable ignored) {
            // BetterModel can disappear during reload; the Bukkit host still needs cleanup.
        }
        if (modelHost != null && modelHost.isValid()) {
            modelHost.remove();
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
                return;
            }
            scheduleBetterModelRestPoseRecovery(block, definition, animationName);
        } catch (final Throwable ex) {
            MMOBlockLogger.warning("[BetterModel] Failed to play animation '" + animationName
                    + FOR_BLOCK_SUFFIX + block.uniqueId() + ": " + ex.getMessage());
        }
    }

    private void scheduleBetterModelRestPoseRecovery(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final String animationName
    ) {
        final Interaction expectedHost = this.betterModelEntities.get(block.uniqueId());
        if (expectedHost == null || !expectedHost.isValid()) {
            return;
        }
        final long sequence = this.betterModelRecoverySequences.merge(block.uniqueId(), 1L, Long::sum);
        final double duration = BetterModelIntegration.animationDurationSeconds(
                definition.betterModelModelId(),
                animationName
        );
        final double recoverySeconds = duration > 0.0D ? duration : 5.0D;
        final long delayTicks = Math.max(4L, (long) Math.ceil(recoverySeconds * 20.0D) + 4L);
        final Location location = modelLocation(expectedHost.getWorld(), block);
        this.plugin.scheduler().runAtLocationLater(location, () -> {
            if (!Long.valueOf(sequence).equals(this.betterModelRecoverySequences.get(block.uniqueId()))
                    || this.betterModelEntities.get(block.uniqueId()) != expectedHost) {
                return;
            }
            final PlacedBlockModel current = this.stateRegistry.getBlock(block.uniqueId());
            if (current == null || !expectedHost.isValid()) {
                return;
            }
            applyBetterModelModel(current, definition, expectedHost.getWorld());
            final Location refreshLocation = modelLocation(expectedHost.getWorld(), current);
            this.plugin.scheduler().runAtLocationLater(
                    refreshLocation,
                    () -> BetterModelIntegration.refreshViewers(current.uniqueId()),
                    2L
            );
        }, delayTicks);
    }

    private static Location modelLocation(final World world, final PlacedBlockModel block) {
        final Location location = new Location(world, block.x() + 0.5D, block.y(), block.z() + 0.5D);
        location.setYaw(BetterModelIntegration.facingToYaw(block.facing()));
        return location;
    }

    private static String formatLocation(final Location location) {
        return location.getWorld().getName() + "["
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ() + "]";
    }

    private static Interaction spawnModelHost(final Location location) {
        final Interaction interaction = location.getWorld().spawn(location, Interaction.class);
        interaction.setInteractionWidth(0.1F);
        interaction.setInteractionHeight(0.1F);
        interaction.setResponsive(false);
        interaction.setGravity(false);
        interaction.setInvulnerable(true);
        interaction.setSilent(true);
        interaction.setPersistent(false);
        return interaction;
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
