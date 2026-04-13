package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.model.PlacedBlock;

/**
 * Centralized lifecycle transitions and checks for block runtime state.
 */
public final class LifecycleSystem {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_RESPAWNING = "respawning";

    public boolean isActive(final PlacedBlock block) {
        return STATUS_ACTIVE.equalsIgnoreCase(block.status());
    }

    public boolean isRespawning(final PlacedBlock block) {
        return STATUS_RESPAWNING.equalsIgnoreCase(block.status());
    }

    public void markActive(final PlacedBlock block) {
        block.setStatus(STATUS_ACTIVE);
    }

    public void markRespawning(final PlacedBlock block) {
        block.setStatus(STATUS_RESPAWNING);
    }
}

