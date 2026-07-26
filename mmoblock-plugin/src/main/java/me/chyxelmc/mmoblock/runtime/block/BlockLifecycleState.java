package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.model.PlacedBlockModel;

/**
 * Centralized lifecycle transitions and checks for block runtime state.
 */
public final class BlockLifecycleState {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RESPAWNING = "respawning";

    public boolean isActive(final PlacedBlockModel block) {
        return STATUS_ACTIVE.equalsIgnoreCase(block.status());
    }

    public boolean isRespawning(final PlacedBlockModel block) {
        return STATUS_RESPAWNING.equalsIgnoreCase(block.status());
    }

    public void markActive(final PlacedBlockModel block) {
        block.setStatus(STATUS_ACTIVE);
    }

    public void markRespawning(final PlacedBlockModel block) {
        block.setStatus(STATUS_RESPAWNING);
    }
}
