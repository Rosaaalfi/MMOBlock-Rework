package me.chyxelmc.mmoblock.runtime.visual;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ToolAction;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
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
            if (!activeStatus.equalsIgnoreCase(placedBlock.status())) {
                continue;
            }
            if (!placedBlock.world().equals(world.getName())) {
                continue;
            }

            final BlockDefinitionModel definition = definitionLookup.apply(placedBlock.type());
            if (!usesRealBlockModel(definition)) {
                continue;
            }
            if (definition.itemsAdderBlockId() != null || definition.craftEngineBlockId() != null) {
                continue;
            }

            final Location location = blockBaseLocation(placedBlock);
            if (location.getWorld() == null || location.distanceSquared(player.getLocation()) > syncRadiusSquared) {
                continue;
            }
            this.nmsAdapter.showFakeBlock(world, location, definition.realBlockMaterial());
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
        this.nmsAdapter.showFakeBlock(world, loc, definition.realBlockMaterial());
        try {
            final int bx = (int) Math.floor(placedBlock.x());
            final int by = (int) Math.floor(placedBlock.y());
            final int bz = (int) Math.floor(placedBlock.z());
            me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.add(world.getName(), bx, by, bz, definition.realBlockMaterial().name());
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
        try {
            this.plugin.scheduler().runAtLocationLater(loc, () -> {
                try {
                    this.nmsAdapter.showFakeBlock(world, loc, definition.realBlockMaterial());
                } catch (final Exception e) {
                    MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
                }
            }, 1L);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
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
        this.nmsAdapter.clearFakeBlock(world, loc);
    }

    private void saveOriginalMaterial(final UUID blockUniqueId, final Location location) {
        if (this.originalMaterials.containsKey(blockUniqueId)) {
            return;
        }
        try {
            final Material material = location.getBlock().getType();
            if (isLiquidMaterial(material)) {
                this.originalMaterials.put(blockUniqueId, material);
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

    private int breakAnimationEntityId(final PlacedBlockModel block) {
        return Math.abs(block.uniqueId().hashCode());
    }
}
