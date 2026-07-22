package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BlockLookProtection implements Listener {

    private final BlockEcsState ecsState;
    private final Set<UUID> protectedPlayers = new HashSet<>();

    public BlockLookProtection(final BlockEcsState ecsState) {
        this.ecsState = ecsState;
    }

    public void protect(final UUID playerUniqueId) {
        this.protectedPlayers.add(playerUniqueId);
    }

    public void unprotect(final UUID playerUniqueId) {
        this.protectedPlayers.remove(playerUniqueId);
    }

    public void clear() {
        this.protectedPlayers.clear();
    }

    public boolean isProtected(final Player player) {
        return player != null && this.protectedPlayers.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(final BlockDamageEvent event) {
        cancelIfPlacedBlock(event.getBlock(), event::setCancelled);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        cancelIfPlacedBlock(event.getBlock(), event::setCancelled);
    }

    private void cancelIfPlacedBlock(final Block block, final CancelAction cancelAction) {
        try {
            if (this.ecsState.containsAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ())) {
                cancelAction.cancel(true);
            }
        } catch (final Exception ignored) {
            // expected - reflection fallback
        }
    }

    @FunctionalInterface
    private interface CancelAction {
        void cancel(boolean cancelled);
    }
}
