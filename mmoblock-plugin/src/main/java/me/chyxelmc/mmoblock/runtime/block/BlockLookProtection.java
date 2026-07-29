package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.interaction.ServerSideFakeBlockService;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

public final class BlockLookProtection implements Listener {

    private final ServerSideFakeBlockService serverSideFakeBlockService;
    private final BlockStateRegistry stateRegistry;
    private final BlockMiningOrchestrator miningOrchestrator;

    public BlockLookProtection(
            final ServerSideFakeBlockService serverSideFakeBlockService,
            final BlockStateRegistry stateRegistry,
            final BlockMiningOrchestrator miningOrchestrator
    ) {
        this.serverSideFakeBlockService = serverSideFakeBlockService;
        this.stateRegistry = stateRegistry;
        this.miningOrchestrator = miningOrchestrator;
    }

    public boolean isProtected(final Player player) {
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(final BlockDamageEvent event) {
        final Block block = event.getBlock();
        if (!isPluginVisualBlock(block)) {
            return;
        }

        final PlacedBlockModel placedBlock = placedBlockAt(block);
        if (!this.miningOrchestrator.canProcessBlockBreak(placedBlock, event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (!isPluginVisualBlock(block)) {
            return;
        }

        event.setCancelled(true);
        final PlacedBlockModel placedBlock = placedBlockAt(block);
        if (placedBlock == null) {
            return;
        }
        final Component message = this.miningOrchestrator.processBlockBreak(placedBlock, event.getPlayer());
        if (message != null && !Component.empty().equals(message)) {
            event.getPlayer().sendMessage(message);
        }
    }

    private void cancelIfPlacedBlock(final Block block, final CancelAction cancelAction) {
        try {
            if (isPluginVisualBlock(block)) {
                cancelAction.cancel(true);
            }
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    private boolean isPluginVisualBlock(final Block block) {
        final String worldName = block.getWorld().getName();
        final int x = block.getX();
        final int y = block.getY();
        final int z = block.getZ();
        return FakeBlockRegistry.contains(worldName, x, y, z)
                || this.serverSideFakeBlockService.isPromoted(worldName, x, y, z);
    }

    private PlacedBlockModel placedBlockAt(final Block block) {
        return this.stateRegistry.blockAt(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ()
        );
    }

    @FunctionalInterface
    private interface CancelAction {
        void cancel(boolean cancelled);
    }
}
