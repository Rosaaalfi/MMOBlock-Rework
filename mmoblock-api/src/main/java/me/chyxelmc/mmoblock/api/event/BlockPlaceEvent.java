package me.chyxelmc.mmoblock.api.event;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockPlaceEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedBlock placedBlock;
    private final BlockDefinition definition;
    private boolean cancelled;

    public BlockPlaceEvent(
            @Nullable final Player player,
            @NotNull final PlacedBlock placedBlock,
            @NotNull final BlockDefinition definition
    ) {
        super(player);
        this.placedBlock = placedBlock;
        this.definition = definition;
    }

    @NotNull
    public PlacedBlock getPlacedBlock() {
        return this.placedBlock;
    }

    @NotNull
    public BlockDefinition getDefinition() {
        return this.definition;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
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
