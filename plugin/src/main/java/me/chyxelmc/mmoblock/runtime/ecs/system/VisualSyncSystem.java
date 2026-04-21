package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Function;

/**
 * Handles visual packet sync concerns (fake blocks and break animation).
 */
public final class VisualSyncSystem {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;

    public VisualSyncSystem(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
    }

    public void syncFakeBlocksForPlayer(
        final Player player,
        final Collection<PlacedBlock> blocks,
        final Function<String, BlockDefinition> definitionLookup,
        final String activeStatus,
        final double syncRadiusSquared
    ) {
        final World world = player.getWorld();
        for (final PlacedBlock placedBlock : blocks) {
            if (!activeStatus.equalsIgnoreCase(placedBlock.status())) {
                continue;
            }
            if (!placedBlock.world().equals(world.getName())) {
                continue;
            }

            final BlockDefinition definition = definitionLookup.apply(placedBlock.type());
            if (!usesRealBlockModel(definition)) {
                continue;
            }

            final Location location = blockBaseLocation(placedBlock);
            if (location.getWorld() == null || location.distanceSquared(player.getLocation()) > syncRadiusSquared) {
                continue;
            }
            this.nmsAdapter.showFakeBlock(world, location, definition.realBlockMaterial());
        }
    }

    public void applyRealBlockModel(final PlacedBlock placedBlock, final BlockDefinition definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }
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
            } catch (final Throwable ignored) {
            }
        try {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                try {
                    this.nmsAdapter.showFakeBlock(world, loc, definition.realBlockMaterial());
                } catch (final Throwable ignored) {
                }
            }, 1L);
        } catch (final Throwable ignored) {
        }
    }

    public void clearRealBlockModel(final PlacedBlock placedBlock, final BlockDefinition definition, final World world) {
        if (!usesRealBlockModel(definition)) {
            return;
        }
        final Location loc = new Location(world, placedBlock.x(), placedBlock.y(), placedBlock.z());
        try {
            // remove registry entry first so outbound correction packet will be allowed through
            final int bx = (int) Math.floor(placedBlock.x());
            final int by = (int) Math.floor(placedBlock.y());
            final int bz = (int) Math.floor(placedBlock.z());
            me.chyxelmc.mmoblock.runtime.FakeBlockRegistry.remove(world.getName(), bx, by, bz);
        } catch (final Throwable ignored) {
        }
        this.nmsAdapter.clearFakeBlock(world, loc);
    }

    public boolean usesRealBlockModel(final BlockDefinition definition) {
        return definition != null
            && definition.useRealBlockModel()
            && definition.realBlockMaterial() != null
            && definition.realBlockMaterial().isBlock();
    }

    public Material resolveParticleMaterial(final BlockDefinition definition) {
        return usesRealBlockModel(definition) ? definition.realBlockMaterial() : Material.STONE;
    }

    public void sendBreakAnimation(final PlacedBlock block, final ToolAction action, final int progress, final boolean clear) {
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

    public void clearBreakAnimation(final World world, final PlacedBlock block) {
        this.nmsAdapter.sendBreakAnimation(world, blockBaseLocation(block), breakAnimationEntityId(block), -1);
    }

    private Location blockBaseLocation(final PlacedBlock block) {
        final World world = this.plugin.getServer().getWorld(block.world());
        return new Location(world, block.x(), block.y(), block.z());
    }

    private int breakAnimationEntityId(final PlacedBlock block) {
        return Math.abs(block.uniqueId().hashCode());
    }
}

