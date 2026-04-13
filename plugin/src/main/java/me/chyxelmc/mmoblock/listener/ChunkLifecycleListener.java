package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class ChunkLifecycleListener implements Listener {

    private final BlockRuntimeService runtimeService;

    public ChunkLifecycleListener(final BlockRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventHandler
    public void onChunkLoad(final ChunkLoadEvent event) {
        this.runtimeService.handleChunkLoad(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onChunkUnload(final ChunkUnloadEvent event) {
        this.runtimeService.handleChunkUnload(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }
}

