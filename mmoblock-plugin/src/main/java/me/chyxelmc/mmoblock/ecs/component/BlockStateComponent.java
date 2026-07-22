package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.domain.PlacedBlockModel;

/**
 * Component that wraps a {@link PlacedBlockModel} instance for ECS tracking.
 * This is a pure data holder — no business logic.
 */
public final class BlockStateComponent {

    private final PlacedBlockModel block;

    public BlockStateComponent(final PlacedBlockModel block) {
        this.block = block;
    }

    public PlacedBlockModel block() {
        return block;
    }
}
