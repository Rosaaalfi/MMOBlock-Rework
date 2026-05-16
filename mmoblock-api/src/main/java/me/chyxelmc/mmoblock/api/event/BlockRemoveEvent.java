package me.chyxelmc.mmoblock.api.event;

import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockRemoveEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedBlock placedBlock;

    public BlockRemoveEvent(
            @Nullable final Player player,
            @NotNull final PlacedBlock placedBlock
    ) {
        super(player);
        this.placedBlock = placedBlock;
    }

    @NotNull
    public PlacedBlock getPlacedBlock() {
        return this.placedBlock;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
