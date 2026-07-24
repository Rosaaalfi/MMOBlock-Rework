package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.ToolAction;
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
public final class VisualSyncSystem {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    /** Tracks the original block material before a custom block was placed in its position. */
    private final Map<UUID, Material> originalMaterials = new ConcurrentHashMap<>();

    public VisualSyncSystem(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
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
            // ItemsAdder/CraftEngine custom blocks are physically placed — no fake block packet needed
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

        // ItemsAdder custom block: place the real block in the world
        if (definition.itemsAdderBlockId() != null) {
            final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
            // Save original block material BEFORE placing custom block (for water/lava restoration)
            saveOriginalMaterial(placedBlock.uniqueId(), loc);
            me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.placeBlock(loc, definition.itemsAdderBlockId());

            // Schedule a chunk refresh 1 tick later to ensure the client re-syncs the
            // block data. During a config reload, the despawn+respawn cycle can cause
            // the client to lose the custom block visual even though the server-side
            // block remains intact. This matches the same pattern used for vanilla
            // fake blocks below.
            try {
                final int cx = (int) Math.floor(placedBlock.x()) >> 4;
                final int cz = (int) Math.floor(placedBlock.z()) >> 4;
                this.plugin.scheduler().runAtLocationLater(loc, () -> {
                    try {
                        world.refreshChunk(cx, cz);
                    } catch (final Exception ignored) {
                    // expected - reflection fallback
                    }
                }, 1L);
            } catch (final Exception ignored) {
            // expected - reflection fallback
            }
            return;
        }

        // CraftEngine custom block: place the real block in the world
        if (definition.craftEngineBlockId() != null) {
            final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
            // Save original block material BEFORE placing custom block (for water/lava restoration)
            saveOriginalMaterial(placedBlock.uniqueId(), loc);
            me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.placeBlock(loc, definition.craftEngineBlockId());

            // Schedule a chunk refresh 1 tick later to ensure the client re-syncs the
            // block data. During a config reload, the despawn+respawn cycle can cause
            // the client to lose the custom block visual even though the server-side
            // block remains intact.
            try {
                final int cx = (int) Math.floor(placedBlock.x()) >> 4;
                final int cz = (int) Math.floor(placedBlock.z()) >> 4;
                this.plugin.scheduler().runAtLocationLater(loc, () -> {
                    try {
                        world.refreshChunk(cx, cz);
                    } catch (final Exception ignored) {
                    // expected - reflection fallback
                    }
                }, 1L);
            } catch (final Exception ignored) {
            // expected - reflection fallback
            }
            return;
        }

        // Vanilla block: send fake block packet
        final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
        // Send immediately and schedule a follow-up send one tick later to avoid
        // client-side packet ordering issues that can cause the fake block to
        // not appear for nearby players at the exact moment of respawn.
        this.nmsAdapter.showFakeBlock(world, loc, definition.realBlockMaterial());
        // Register fake-block position for fast lookup by Netty handler to force-refresh visuals
            try {
                final int bx = (int) Math.floor(placedBlock.x());
                final int by = (int) Math.floor(placedBlock.y());
                final int bz = (int) Math.floor(placedBlock.z());
                // store material name so NMS handler can reconstruct fake BlockState
                me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.add(world.getName(), bx, by, bz, definition.realBlockMaterial().name());
            } catch (final Exception ignored) {
            // expected - reflection fallback
            }
        try {
            this.plugin.scheduler().runAtLocationLater(loc, () -> {
                try {
                    this.nmsAdapter.showFakeBlock(world, loc, definition.realBlockMaterial());
                } catch (final Exception ignored) {
                // expected - reflection fallback
                }
            }, 1L);
        } catch (final Exception ignored) {
        // expected - reflection fallback
        }
    }

    public void clearRealBlockModel(final PlacedBlockModel placedBlock, final BlockDefinitionModel definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }

        final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());

        // ItemsAdder/CraftEngine custom block: remove the real block from the world
        if (definition.itemsAdderBlockId() != null) {
            // If the position has already been restored to liquid (e.g. by a previous
            // unload+clear cycle), skip removeBlock to avoid destroying the restored water.
            if (!isLiquidMaterial(loc.getBlock().getType())) {
                me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.removeBlock(loc);
            }
            // Restore the original material (e.g. water/lava) from our tracking map
            restoreOriginalMaterial(placedBlock.uniqueId(), loc);
            return;
        }
        if (definition.craftEngineBlockId() != null) {
            // If the position has already been restored to liquid, skip removeBlock.
            if (!isLiquidMaterial(loc.getBlock().getType())) {
                me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.removeBlock(loc);
            }
            // Restore the original material (e.g. water/lava) from our tracking map
            restoreOriginalMaterial(placedBlock.uniqueId(), loc);
            return;
        }

        // Vanilla block: clear fake block packet
        try {
            // remove registry entry first so outbound correction packet will be allowed through
            final int bx = (int) Math.floor(placedBlock.x());
            final int by = (int) Math.floor(placedBlock.y());
            final int bz = (int) Math.floor(placedBlock.z());
            me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.remove(world.getName(), bx, by, bz);
        } catch (final Exception ignored) {
        // expected - reflection fallback
        }
        this.nmsAdapter.clearFakeBlock(world, loc);
    }

    /**
     * Saves the current block material at the given location before a custom block
     * (ItemsAdder/CraftEngine) is placed. This is used later to restore water/lava
     * when the block is removed.
     * <p>For cross-session persistence (server restart), also checks if the block
     * ABOVE is liquid — if so, this position was originally underwater and the
     * custom block replaced the water. Only the above check is reliable because
     * liquid above a solid block can only exist if it was replaced.</p>
     */
    private void saveOriginalMaterial(final UUID blockUniqueId, final Location location) {
        if (this.originalMaterials.containsKey(blockUniqueId)) {
            return; // Already saved, don't overwrite
        }
        try {
            final Material material = location.getBlock().getType();
            if (isLiquidMaterial(material)) {
                this.originalMaterials.put(blockUniqueId, material);
                return;
            }
            // After a server restart, the custom block already replaced the liquid.
            // Only check the block ABOVE — liquid can't float above a solid block,
            // so if the block above is liquid, this position was originally liquid.
            // (The below check is unreliable as it could be a water lake below a ledge.)
            final Block above = location.clone().add(0, 1, 0).getBlock();
            if (isLiquidMaterial(above.getType())) {
                this.originalMaterials.put(blockUniqueId, above.getType());
            }
        } catch (final Exception ignored) {
            // expected - reflection fallback
        }
    }

    /**
     * Restores the original material (water/lava) at the location if it was saved.
     * This must be called AFTER removing the custom block so the original liquid
     * replaces the air left by removeBlock.
     */
    private void restoreOriginalMaterial(final UUID blockUniqueId, final Location location) {
        final Material original = this.originalMaterials.remove(blockUniqueId);
        if (original != null) {
            try {
                location.getBlock().setType(original);
            } catch (final Exception ignored) {
                // expected - reflection fallback
            }
        }
    }

    /**
     * Clears all tracked original materials. Should be called on plugin shutdown
     * to prevent memory leaks.
     */
    public void clearOriginalMaterials() {
        this.originalMaterials.clear();
    }

    /**
     * Checks if the material is water or lava (for liquid restoration tracking).
     */
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
        // ItemsAdder/CraftEngine custom block: valid without a Bukkit Material
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
