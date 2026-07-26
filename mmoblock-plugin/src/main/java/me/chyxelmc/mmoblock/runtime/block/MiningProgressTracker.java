package me.chyxelmc.mmoblock.runtime.block;

import java.util.Set;
import java.util.UUID;

import me.chyxelmc.mmoblock.ecs.component.MiningProgressComponent;

/**
 * Handles mining-related state mutations (throttle and click progress).
 */
public final class MiningProgressTracker {

    private final BlockStateRegistry stateRegistry;

    public MiningProgressTracker(final BlockStateRegistry stateRegistry) {
        this.stateRegistry = stateRegistry;
    }

    public boolean isThrottled(final UUID blockId, final UUID playerId, final long nowMillis, final long minDelayMillis) {
        return this.stateRegistry.throttle(blockId).isThrottled(playerId, nowMillis, minDelayMillis);
    }

    public int incrementProgress(final UUID blockId, final UUID playerId, final long nowMillis) {
        this.stateRegistry.markMiningActive(blockId);
        return this.stateRegistry.mining(blockId).increment(playerId, nowMillis);
    }

    public void clearProgress(final UUID blockId, final UUID playerId) {
        final MiningProgressComponent component = this.stateRegistry.mining(blockId);
        component.clear(playerId);
        if (!component.hasAnyProgress()) {
            this.stateRegistry.unmarkMiningActive(blockId);
        }
    }

    public void clearAllProgress(final UUID blockId) {
        this.stateRegistry.mining(blockId).clearAll();
        this.stateRegistry.unmarkMiningActive(blockId);
    }

    public boolean hasAnyProgress(final UUID blockId) {
        final MiningProgressComponent component = this.stateRegistry.mining(blockId);
        return component.hasAnyProgress();
    }

    public Set<UUID> evictInactiveProgress(final UUID blockId, final long nowMillis, final long timeoutMillis) {
        final MiningProgressComponent component = this.stateRegistry.mining(blockId);
        final Set<UUID> evicted = component.evictInactive(nowMillis, timeoutMillis);
        if (!component.hasAnyProgress()) {
            this.stateRegistry.unmarkMiningActive(blockId);
        }
        return evicted;
    }

    public Set<UUID> activeMiningBlockIds() {
        return this.stateRegistry.activeMiningBlockIdsSnapshot();
    }
}
