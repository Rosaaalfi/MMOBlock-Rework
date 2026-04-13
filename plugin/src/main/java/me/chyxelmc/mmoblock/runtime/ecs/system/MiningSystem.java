package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.runtime.ecs.BlockEcsState;

import java.util.UUID;

/**
 * Handles mining-related ECS component mutations (throttle and click progress).
 */
public final class MiningSystem {

    private final BlockEcsState ecsState;

    public MiningSystem(final BlockEcsState ecsState) {
        this.ecsState = ecsState;
    }

    public boolean isThrottled(final UUID blockId, final UUID playerId, final long nowMillis, final long minDelayMillis) {
        return this.ecsState.throttle(blockId).isThrottled(playerId, nowMillis, minDelayMillis);
    }

    public int incrementProgress(final UUID blockId, final UUID playerId) {
        return this.ecsState.mining(blockId).increment(playerId);
    }

    public void clearProgress(final UUID blockId, final UUID playerId) {
        this.ecsState.mining(blockId).clear(playerId);
    }
}

