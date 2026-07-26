package me.chyxelmc.mmoblock.runtime.block;

import org.bukkit.entity.Player;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.api.event.BlockMineEvent;
import me.chyxelmc.mmoblock.api.event.BlockPlaceEvent;
import me.chyxelmc.mmoblock.api.event.BlockRemoveEvent;
import me.chyxelmc.mmoblock.api.event.BlockRespawnEvent;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;

public final class BlockEventDispatcher {

    private final MMOBlock plugin;

    public BlockEventDispatcher(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    public void callPlace(final PlacedBlockModel block, final BlockDefinitionModel definition) {
        this.plugin.getServer().getPluginManager().callEvent(new BlockPlaceEvent(null, block, definition));
    }

    public void callRemove(final PlacedBlockModel block) {
        this.plugin.getServer().getPluginManager().callEvent(new BlockRemoveEvent(null, block));
    }

    public void callMineProgress(
            final Player player,
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final String clickType,
            final int progress,
            final int needed
    ) {
        this.plugin.getServer().getPluginManager().callEvent(new BlockMineEvent(
                player,
                block,
                definition,
                clickType,
                progress,
                needed,
                false
        ));
    }

    public void callMineComplete(
            final Player player,
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final String clickType,
            final int needed
    ) {
        this.plugin.getServer().getPluginManager().callEvent(new BlockMineEvent(
                player,
                block,
                definition,
                clickType,
                needed,
                needed,
                true
        ));
    }

    public void callRespawn(final PlacedBlockModel block, final BlockDefinitionModel definition) {
        this.plugin.getServer().getPluginManager().callEvent(new BlockRespawnEvent(block, definition));
    }
}
