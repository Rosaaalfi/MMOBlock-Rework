package me.chyxelmc.mmoblock.runtime.visual;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ToolAction;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.runtime.block.BlockLifecycleState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Handles visual packet sync concerns (fake blocks and break animation).
 * <p>Tracks original block materials for ItemsAdder/CraftEngine blocks placed
 * over water or lava so the liquid is properly restored on removal.</p>
 */
public final class BlockVisualSyncService {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    /** Tracks the original block material before a custom block was placed in its position. */
    private final Map<UUID, Material> originalMaterials = new ConcurrentHashMap<>();

    public BlockVisualSyncService(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
    }

    public void syncFakeBlocksForPlayer(
        final Player player,
        final Collection<PlacedBlockModel> blocks,
        final Function<String, BlockDefinitionModel> definitionLookup,
        final String activeStatus,
        final double syncRadiusSquared
    ) {
        final World world = player.getWorld();
        for (final PlacedBlockModel placedBlock : blocks) {
            if (!placedBlock.world().equals(world.getName())) {
                continue;
            }

            final BlockDefinitionModel definition = definitionLookup.apply(placedBlock.type());
            if (definition == null) continue;

            if (activeStatus.equalsIgnoreCase(placedBlock.status())) {
                // Active block — sync the real block model
                if (!usesRealBlockModel(definition)) continue;
                if (definition.itemsAdderBlockId() != null || definition.craftEngineBlockId() != null) continue;

                final Location location = blockBaseLocation(placedBlock);
                if (location.getWorld() == null || location.distanceSquared(player.getLocation()) > syncRadiusSquared) {
                    continue;
                }
                if (!me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.contains(
                        world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                    continue;
                }
                this.nmsAdapter.showFakeBlock(world, location, definition.realBlockMaterial());
            } else if (BlockLifecycleState.STATUS_RESPAWNING.equalsIgnoreCase(placedBlock.status())) {
                // Respawning (dead) block — sync the dead block model if configured
                // Only vanilla fake blocks need syncing; ItemsAdder/CraftEngine are world-level
                if (!hasDeadBlockModel(definition)) continue;
                if (definition.realDeadBlockMaterial() == null) continue;

                // Dead block is shown at the ORIGIN (same as the dead hologram), not at
                // the block's current position (which may have moved due to randomLocation).
                final Location location = originBaseLocation(placedBlock);
                if (location.getWorld() == null || location.distanceSquared(player.getLocation()) > syncRadiusSquared) {
                    continue;
                }
                if (!me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.contains(
                        world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                    continue;
                }
                this.nmsAdapter.showFakeBlock(world, location, definition.realDeadBlockMaterial());
            }
        }
    }

    public void applyRealBlockModel(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }

        if (definition.itemsAdderBlockId() != null) {
            final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
            saveOriginalMaterial(placedBlock.uniqueId(), loc);
            try {
                me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.placeBlock(loc, definition.itemsAdderBlockId());
            } catch (final Throwable ignored) {
            }

            try {
                final int cx = (int) Math.floor(placedBlock.x()) >> 4;
                final int cz = (int) Math.floor(placedBlock.z()) >> 4;
                this.plugin.scheduler().runAtLocationLater(loc, () -> {
                    try {
                        world.refreshChunk(cx, cz);
                    } catch (final Exception e) {
                        MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                    }
                }, 1L);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
            return;
        }

        if (definition.craftEngineBlockId() != null) {
            final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
            saveOriginalMaterial(placedBlock.uniqueId(), loc);
            try {
                me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.placeBlock(loc, definition.craftEngineBlockId());
            } catch (final Throwable ignored) {
            }

            try {
                final int cx = (int) Math.floor(placedBlock.x()) >> 4;
                final int cz = (int) Math.floor(placedBlock.z()) >> 4;
                this.plugin.scheduler().runAtLocationLater(loc, () -> {
                    try {
                        world.refreshChunk(cx, cz);
                    } catch (final Exception e) {
                        MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                    }
                }, 1L);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
            return;
        }

        final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
        // Save the original block material BEFORE changing it, so clearRealBlockModel
        // can restore it fully. Without this, blocks restored from persistence would
        // leave permanent debris (the definition material) when removed.
        saveOriginalMaterial(placedBlock.uniqueId(), loc);
        showFakeBlockAndRegister(placedBlock, world, loc, definition.realBlockMaterial());
    }

    public void clearRealBlockModel(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }

        final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());

        if (definition.itemsAdderBlockId() != null) {
            if (!isLiquidMaterial(loc.getBlock().getType())) {
                try {
                    me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.removeBlock(loc);
                } catch (final Throwable ignored) {
                }
            }
            restoreOriginalMaterial(placedBlock.uniqueId(), loc);
            return;
        }
        if (definition.craftEngineBlockId() != null) {
            if (!isLiquidMaterial(loc.getBlock().getType())) {
                try {
                    me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.removeBlock(loc);
                } catch (final Throwable ignored) {
                }
            }
            restoreOriginalMaterial(placedBlock.uniqueId(), loc);
            return;
        }

        try {
            final int bx = (int) Math.floor(placedBlock.x());
            final int by = (int) Math.floor(placedBlock.y());
            final int bz = (int) Math.floor(placedBlock.z());
            me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.remove(world.getName(), bx, by, bz);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
        restoreOriginalMaterial(placedBlock.uniqueId(), loc);
        this.nmsAdapter.clearFakeBlock(world, loc);
    }

    /**
     * Apply the dead-state block model for a respawning (mined) block.
     * Shows a different material (e.g. bedrock) instead of clearing the block entirely.
     */
    public void applyDeadBlockModel(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }

        // Always clear the active block at its current position FIRST,
        // regardless of whether a dead block model is configured.
        clearRealBlockModel(placedBlock, definition, world);

        if (!hasDeadBlockModel(definition)) {
            // No dead state configured — block is now cleared; nothing more to do.
            return;
        }

        // Place the dead block at the ORIGIN position (same as where the dead hologram is shown),
        // so it appears centered even when randomLocation has moved the block away from origin.
        final Location loc = new Location(world, placedBlock.originX(), placedBlock.originY(), placedBlock.originZ());

        if (definition.itemsAdderDeadBlockId() != null) {
            try {
                me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.placeBlock(loc, definition.itemsAdderDeadBlockId());
            } catch (final Throwable ignored) {
            }
            scheduleChunkRefresh(world, loc);
            return;
        }

        if (definition.craftEngineDeadBlockId() != null) {
            try {
                me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.placeBlock(loc, definition.craftEngineDeadBlockId());
            } catch (final Throwable ignored) {
            }
            scheduleChunkRefresh(world, loc);
            return;
        }

        // Vanilla dead block material
        if (definition.realDeadBlockMaterial() != null) {
            showFakeBlockAndRegister(placedBlock, world, loc, definition.realDeadBlockMaterial());
        }
    }

    /**
     * Check whether a block definition has a dead-state block model configured.
     */
    public boolean hasDeadBlockModel(final BlockDefinitionModel definition) {
        if (definition == null) return false;
        if (!usesRealBlockModel(definition)) return false;
        if (definition.realDeadBlockMaterial() != null) return true;
        if (definition.itemsAdderDeadBlockId() != null) return true;
        if (definition.craftEngineDeadBlockId() != null) return true;
        return false;
    }

    private void saveOriginalMaterial(final UUID blockUniqueId, final Location location) {
        if (this.originalMaterials.containsKey(blockUniqueId)) {
            return;
        }
        try {
            final Material material = location.getBlock().getType();
            this.originalMaterials.put(blockUniqueId, material);
            // For liquid blocks at or above this position, also save the liquid material
            // to restore ItemsAdder/CraftEngine waterlogging correctly.
            if (isLiquidMaterial(material)) {
                return;
            }
            final Block above = location.clone().add(0, 1, 0).getBlock();
            if (isLiquidMaterial(above.getType())) {
                this.originalMaterials.put(blockUniqueId, above.getType());
            }
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    private void restoreOriginalMaterial(final UUID blockUniqueId, final Location location) {
        final Material original = this.originalMaterials.remove(blockUniqueId);
        if (original != null) {
            try {
                location.getBlock().setType(original);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
        }
    }

    public void clearOriginalMaterials() {
        this.originalMaterials.clear();
    }

    private static boolean isLiquidMaterial(final Material material) {
        if (material == Material.WATER || material == Material.LAVA) {
            return true;
        }
        final String name = material.name();
        return name.equals("WATER") || name.equals("LAVA")
                || name.equals("LEGACY_WATER") || name.equals("LEGACY_LAVA")
                || name.equals("FLOWING_WATER") || name.equals("FLOWING_LAVA");
    }

    public boolean usesRealBlockModel(final BlockDefinitionModel definition) {
        if (definition == null) return false;
        if (definition.schematicsEnabled()) return false;
        if (definition.bdengineEnabled()) return false;
        if (!definition.useRealBlockModel()) return false;
        if (definition.itemsAdderBlockId() != null) return true;
        if (definition.craftEngineBlockId() != null) return true;
        return definition.realBlockMaterial() != null
            && definition.realBlockMaterial().isBlock();
    }

    public Material resolveParticleMaterial(final BlockDefinitionModel definition) {
        if (definition.particleMaterial() != null) {
            return definition.particleMaterial();
        }
        if (usesRealBlockModel(definition)) {
            return definition.realBlockMaterial() != null ? definition.realBlockMaterial() : Material.STONE;
        }
        return Material.STONE;
    }

    public void sendBreakAnimation(final PlacedBlockModel block, final ToolAction action, final int progress, final boolean clear) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }

        final int stage;
        if (clear) {
            stage = -1;
        } else {
            final int needed = Math.max(1, action.clickNeeded());
            stage = Math.min(9, Math.max(0, (int) Math.floor((progress / (double) needed) * 10.0D)));
        }
        this.nmsAdapter.sendBreakAnimation(world, blockBaseLocation(block), breakAnimationEntityId(block), stage);
    }

    public void clearBreakAnimation(final World world, final PlacedBlockModel block) {
        this.nmsAdapter.sendBreakAnimation(world, blockBaseLocation(block), breakAnimationEntityId(block), -1);
    }

    private Location blockBaseLocation(final PlacedBlockModel block) {
        final World world = this.plugin.getServer().getWorld(block.world());
        return new Location(world, block.x(), block.y(), block.z());
    }

    private Location originBaseLocation(final PlacedBlockModel block) {
        final World world = this.plugin.getServer().getWorld(block.world());
        return new Location(world, block.originX(), block.originY(), block.originZ());
    }

    private int breakAnimationEntityId(final PlacedBlockModel block) {
        return Math.abs(block.uniqueId().hashCode());
    }

    /**
     * Show a fake block and register it in the FakeBlockRegistry.
     */
    private void showFakeBlockAndRegister(
        final PlacedBlockModel placedBlock,
        final World world,
        final Location loc,
        final Material material
    ) {
        this.nmsAdapter.showFakeBlock(world, loc, material);
        // Use the loc coordinates (not placedBlock.x/y/z) so the FakeBlockRegistry
        // entry matches where the fake block was actually shown. This is critical
        // when the dead block is placed at the origin vs the block's current position.
        try {
            final int bx = loc.getBlockX();
            final int by = loc.getBlockY();
            final int bz = loc.getBlockZ();
            me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.add(world.getName(), bx, by, bz, material.name());
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
        try {
            this.plugin.scheduler().runAtLocationLater(loc, () -> {
                try {
                    if (!me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.contains(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                        return;
                    }
                    this.nmsAdapter.showFakeBlock(world, loc, material);
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }, 1L);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    /**
     * Schedule a delayed chunk refresh after placing an ItemsAdder/CraftEngine block.
     */
    private void scheduleChunkRefresh(final World world, final Location loc) {
        try {
            final int cx = (int) Math.floor(loc.getBlockX()) >> 4;
            final int cz = (int) Math.floor(loc.getBlockZ()) >> 4;
            this.plugin.scheduler().runAtLocationLater(loc, () -> {
                try {
                    world.refreshChunk(cx, cz);
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }, 1L);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }
}
