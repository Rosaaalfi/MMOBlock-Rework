package me.chyxelmc.mmoblock.runtime.ecs;

import me.chyxelmc.mmoblock.model.PlacedBlock;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS-like state registry for runtime block entities and their components.
 * Heavy mutable state is centralized here to keep runtime service orchestration focused.
 */
public final class BlockEcsState {

    private final Map<UUID, PlacedBlock> blocks = new ConcurrentHashMap<>();
    private final Map<UUID, MiningProgressComponent> miningComponents = new ConcurrentHashMap<>();
    private final Map<UUID, ClickThrottleComponent> throttleComponents = new ConcurrentHashMap<>();
    private final Map<UUID, RespawnComponent> respawnComponents = new ConcurrentHashMap<>();

    public void putBlock(final PlacedBlock block) {
        this.blocks.put(block.uniqueId(), block);
        this.miningComponents.putIfAbsent(block.uniqueId(), new MiningProgressComponent());
        this.throttleComponents.putIfAbsent(block.uniqueId(), new ClickThrottleComponent());
    }

    public PlacedBlock getBlock(final UUID uniqueId) {
        return this.blocks.get(uniqueId);
    }

    public Collection<PlacedBlock> blocks() {
        return Collections.unmodifiableCollection(this.blocks.values());
    }

    public Collection<PlacedBlock> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.blocks.values()));
    }

    public boolean containsBlock(final UUID uniqueId) {
        return this.blocks.containsKey(uniqueId);
    }

    public void removeBlock(final UUID uniqueId) {
        this.blocks.remove(uniqueId);
        this.miningComponents.remove(uniqueId);
        this.throttleComponents.remove(uniqueId);
        this.respawnComponents.remove(uniqueId);
    }

    public MiningProgressComponent mining(final UUID uniqueId) {
        return this.miningComponents.computeIfAbsent(uniqueId, ignored -> new MiningProgressComponent());
    }

    public ClickThrottleComponent throttle(final UUID uniqueId) {
        return this.throttleComponents.computeIfAbsent(uniqueId, ignored -> new ClickThrottleComponent());
    }

    public RespawnComponent respawn(final UUID uniqueId) {
        return this.respawnComponents.computeIfAbsent(uniqueId, ignored -> new RespawnComponent());
    }

    public RespawnComponent removeRespawnComponent(final UUID uniqueId) {
        return this.respawnComponents.remove(uniqueId);
    }

    public void clear() {
        this.blocks.clear();
        this.miningComponents.clear();
        this.throttleComponents.clear();
        this.respawnComponents.clear();
    }

    public static final class MiningProgressComponent {
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

    public static final class ClickThrottleComponent {
        private final Map<UUID, Long> lastClickByPlayer = new ConcurrentHashMap<>();

        public boolean isThrottled(final UUID playerId, final long now, final long minDelay) {
            final Long last = this.lastClickByPlayer.get(playerId);
            if (last != null && (now - last) < minDelay) {
                return true;
            }
            this.lastClickByPlayer.put(playerId, now);
            return false;
        }
    }

    public static final class RespawnComponent {
        private BukkitTask respawnTask;
        private BukkitTask countdownTask;

        public BukkitTask respawnTask() {
            return this.respawnTask;
        }

        public BukkitTask countdownTask() {
            return this.countdownTask;
        }

        public void setTasks(final BukkitTask respawnTask, final BukkitTask countdownTask) {
            this.respawnTask = respawnTask;
            this.countdownTask = countdownTask;
        }

        public void clearTasks() {
            this.respawnTask = null;
            this.countdownTask = null;
        }
    }
}

