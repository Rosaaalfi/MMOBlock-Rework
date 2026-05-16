package me.chyxelmc.mmoblock.api.event;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class BlockRespawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedBlock placedBlock;
    private final BlockDefinition definition;

    public BlockRespawnEvent(
            @NotNull final PlacedBlock placedBlock,
            @NotNull final BlockDefinition definition
    ) {
        super(true);
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
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
