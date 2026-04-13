package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeBlockSyncListener implements Listener {

    private static final long MOVE_SYNC_THROTTLE_MS = 100L;
    private static final double MOVE_SYNC_DISTANCE_SQUARED = 0.16D;

    private final BlockRuntimeService runtimeService;
    private final org.bukkit.plugin.Plugin plugin;
    private final Map<UUID, Long> lastChunkSyncAt = new ConcurrentHashMap<>();

    public FakeBlockSyncListener(final org.bukkit.plugin.Plugin plugin, final BlockRuntimeService runtimeService) {
        this.plugin = plugin;
        this.runtimeService = runtimeService;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        syncNowAndDelayed(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(final PlayerTeleportEvent event) {
        syncNowAndDelayed(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event) {
        syncNowAndDelayed(event.getPlayer());
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent event) {
        if (event.getFrom().getWorld() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            syncNowAndDelayed(event.getPlayer());
            return;
        }
        if (event.getFrom().distanceSquared(event.getTo()) < MOVE_SYNC_DISTANCE_SQUARED) {
            return;
        }

        final Player player = event.getPlayer();
        final long now = System.currentTimeMillis();
        final Long last = this.lastChunkSyncAt.get(player.getUniqueId());
        if (last != null && (now - last) < MOVE_SYNC_THROTTLE_MS) {
            return;
        }
        this.lastChunkSyncAt.put(player.getUniqueId(), now);
        this.runtimeService.syncFakeBlocksForPlayer(player);
    }

    private void syncNowAndDelayed(final Player player) {
        this.runtimeService.syncFakeBlocksForPlayer(player);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.runtimeService.syncFakeBlocksForPlayer(player), 2L);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.runtimeService.syncFakeBlocksForPlayer(player), 20L);
    }
}

