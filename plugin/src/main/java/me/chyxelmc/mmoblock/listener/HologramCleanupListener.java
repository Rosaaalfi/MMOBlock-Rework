package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HologramCleanupListener implements Listener {

    private final BlockRuntimeService runtimeService;

    public HologramCleanupListener(final BlockRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.runtimeService.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}

