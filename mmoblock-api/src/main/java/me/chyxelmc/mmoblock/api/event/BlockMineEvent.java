package me.chyxelmc.mmoblock.api.event;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class BlockMineEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final PlacedBlock placedBlock;
    private final BlockDefinition definition;
    private final String clickType;
    private final int currentProgress;
    private final int maxProgress;
    private final boolean completed;

    public BlockMineEvent(
            @NotNull final Player player,
            @NotNull final PlacedBlock placedBlock,
            @NotNull final BlockDefinition definition,
            @NotNull final String clickType,
            final int currentProgress,
            final int maxProgress,
            final boolean completed
    ) {
        super(player);
        this.placedBlock = placedBlock;
        this.definition = definition;
        this.clickType = clickType;
        this.currentProgress = currentProgress;
        this.maxProgress = maxProgress;
        this.completed = completed;
    }

    @NotNull
    public PlacedBlock getPlacedBlock() {
        return this.placedBlock;
    }

    @NotNull
    public BlockDefinition getDefinition() {
        return this.definition;
    }

    @NotNull
    public String getClickType() {
        return this.clickType;
    }

    public int getCurrentProgress() {
        return this.currentProgress;
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    public boolean isCompleted() {
        return this.completed;
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
