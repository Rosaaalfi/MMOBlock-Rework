package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.DisplayLine;

import java.util.List;
import java.util.UUID;

/**
 * Component that holds hologram display configuration for a block entity.
 * Pure data — no business logic.
 */
public final class HologramDisplayComponent {

    private final UUID hologramId;
    private final List<DisplayLine> lines;
    private final double displayHeight;

    public HologramDisplayComponent(final UUID hologramId, final List<DisplayLine> lines, final double displayHeight) {
        this.hologramId = hologramId;
        this.lines = List.copyOf(lines);
        this.displayHeight = displayHeight;
    }

    public UUID hologramId() {
        return hologramId;
    }

    public List<DisplayLine> lines() {
        return lines;
    }

    public double displayHeight() {
        return displayHeight;
    }
}
