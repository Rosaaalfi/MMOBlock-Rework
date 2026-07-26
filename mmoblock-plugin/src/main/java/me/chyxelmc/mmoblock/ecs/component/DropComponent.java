package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.model.BlockDefinitionModel.DropEntry;

import java.util.List;

/**
 * Component that holds drop configuration for a block entity.
 * Pure data — no business logic.
 */
public final class DropComponent {

    private final List<DropEntry> dropEntries;

    public DropComponent(final List<DropEntry> dropEntries) {
        this.dropEntries = List.copyOf(dropEntries);
    }

    public List<DropEntry> dropEntries() {
        return dropEntries;
    }
}
