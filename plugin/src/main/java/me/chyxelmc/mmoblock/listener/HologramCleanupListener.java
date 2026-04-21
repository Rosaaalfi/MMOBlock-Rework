package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class HologramCleanupListener implements Listener {

    private final Plugin plugin;
    private final BlockRuntimeService runtimeService;

    public HologramCleanupListener(final Plugin plugin, final BlockRuntimeService runtimeService) {
        this.plugin = plugin;
        this.runtimeService = runtimeService;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.runtimeService.handlePlayerQuit(event.getPlayer().getUniqueId());
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

