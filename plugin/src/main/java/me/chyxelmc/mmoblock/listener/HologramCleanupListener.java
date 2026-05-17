package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class HologramCleanupListener implements Listener {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final BlockRuntimeService runtimeService;
    private final me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService;
    private final java.util.function.Consumer<java.util.UUID> onPlayerQuit;

    public HologramCleanupListener(final Plugin plugin, final Scheduler scheduler, final BlockRuntimeService runtimeService, final me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService) {
        this(plugin, scheduler, runtimeService, nodeRuntimeService, null);
    }

    public HologramCleanupListener(final Plugin plugin, final Scheduler scheduler, final BlockRuntimeService runtimeService, final me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService, final java.util.function.Consumer<java.util.UUID> onPlayerQuit) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.runtimeService = runtimeService;
        this.nodeRuntimeService = nodeRuntimeService;
        this.onPlayerQuit = onPlayerQuit;
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        this.scheduler.runAtLocationLater(player.getLocation(), () -> {
            this.runtimeService.syncFakeBlocksForPlayer(player);
        }, 1L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        this.runtimeService.handlePlayerQuit(playerId);
        if (this.nodeRuntimeService != null) {
            this.nodeRuntimeService.handlePlayerQuit(playerId);
        }
        if (this.onPlayerQuit != null) {
            try {
                this.onPlayerQuit.accept(playerId);
            } catch (final Throwable ignored) {
            }
        }
        // Uninject netty handler for this player to avoid leaks (best-effort)
        try {
            if (this.plugin instanceof MMOBlock mmob) {
                final String clsName = mmob.fakePacketHandlerClassName();
                if (clsName != null) {
                    final Class<?> cls = Class.forName(clsName);
                    final java.lang.reflect.Method uninject = cls.getMethod("uninject", org.bukkit.entity.Player.class);
                    uninject.invoke(null, event.getPlayer());
                }
            }
        } catch (final Throwable ignored) {
        }
    }
}
