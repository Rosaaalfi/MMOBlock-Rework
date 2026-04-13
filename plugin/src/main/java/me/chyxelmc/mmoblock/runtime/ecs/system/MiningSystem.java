package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.runtime.ecs.BlockEcsState;

import java.util.Set;
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

    public int incrementProgress(final UUID blockId, final UUID playerId, final long nowMillis) {
        this.ecsState.markMiningActive(blockId);
        return this.ecsState.mining(blockId).increment(playerId, nowMillis);
    }

    public void clearProgress(final UUID blockId, final UUID playerId) {
        this.ecsState.mining(blockId).clear(playerId);
        if (!this.ecsState.mining(blockId).hasAnyProgress()) {
            this.ecsState.unmarkMiningActive(blockId);
        }
    }

    public void clearAllProgress(final UUID blockId) {
        this.ecsState.mining(blockId).clearAll();
        this.ecsState.unmarkMiningActive(blockId);
    }

    public boolean hasAnyProgress(final UUID blockId) {
        return this.ecsState.mining(blockId).hasAnyProgress();
    }

    public Set<UUID> evictInactiveProgress(final UUID blockId, final long nowMillis, final long timeoutMillis) {
        final Set<UUID> evicted = this.ecsState.mining(blockId).evictInactive(nowMillis, timeoutMillis);
        if (!this.ecsState.mining(blockId).hasAnyProgress()) {
            this.ecsState.unmarkMiningActive(blockId);
        }
        return evicted;
    }

    public Set<UUID> activeMiningBlockIds() {
        return this.ecsState.activeMiningBlockIdsSnapshot();
    }
}

