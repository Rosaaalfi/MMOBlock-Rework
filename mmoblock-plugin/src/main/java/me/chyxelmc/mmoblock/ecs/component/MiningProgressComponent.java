package me.chyxelmc.mmoblock.ecs.component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component that tracks per-player mining progress for a block entity.
 * Pure data storage — thinning logic is in {@code BlockMiningSystem}.
 */
public class MiningProgressComponent {

    private final Map<UUID, Integer> perPlayerProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractionByPlayer = new ConcurrentHashMap<>();

    public int increment(final UUID playerId, final long nowMillis) {
        this.lastInteractionByPlayer.put(playerId, nowMillis);
        return this.perPlayerProgress.merge(playerId, 1, Integer::sum);
    }

    public void clear(final UUID playerId) {
        this.perPlayerProgress.remove(playerId);
        this.lastInteractionByPlayer.remove(playerId);
    }

    public void clearAll() {
        this.perPlayerProgress.clear();
        this.lastInteractionByPlayer.clear();
    }

    public Map<UUID, Integer> perPlayerProgress() {
        return this.perPlayerProgress;
    }

    public boolean hasAnyProgress() {
        return !this.perPlayerProgress.isEmpty();
    }

    public Set<UUID> evictInactive(final long nowMillis, final long timeoutMillis) {
        final Set<UUID> evictedPlayers = new HashSet<>();
        for (final Map.Entry<UUID, Long> entry : this.lastInteractionByPlayer.entrySet()) {
            if ((nowMillis - entry.getValue()) < timeoutMillis) {
                continue;
            }
            final UUID playerId = entry.getKey();
            this.lastInteractionByPlayer.remove(playerId);
            this.perPlayerProgress.remove(playerId);
            evictedPlayers.add(playerId);
        }
        return evictedPlayers;
    }
}
