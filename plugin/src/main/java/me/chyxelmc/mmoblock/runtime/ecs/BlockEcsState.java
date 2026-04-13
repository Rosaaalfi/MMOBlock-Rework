package me.chyxelmc.mmoblock.runtime.ecs;

import me.chyxelmc.mmoblock.model.PlacedBlock;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private final Map<PositionKey, UUID> blockIdByPosition = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Set<UUID>> blocksByChunk = new ConcurrentHashMap<>();
    private final Set<UUID> activeMiningBlockIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MiningProgressComponent> miningComponents = new ConcurrentHashMap<>();
    private final Map<UUID, ClickThrottleComponent> throttleComponents = new ConcurrentHashMap<>();
    private final Map<UUID, RespawnComponent> respawnComponents = new ConcurrentHashMap<>();

    public void putBlock(final PlacedBlock block) {
        final PlacedBlock previous = this.blocks.put(block.uniqueId(), block);
        if (previous != null) {
            removeFromChunkIndex(previous);
            this.blockIdByPosition.remove(positionKeyFor(previous));
        }
        addToChunkIndex(block);
        this.blockIdByPosition.put(positionKeyFor(block), block.uniqueId());
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
        final PlacedBlock removed = this.blocks.remove(uniqueId);
        if (removed != null) {
            removeFromChunkIndex(removed);
            this.blockIdByPosition.remove(positionKeyFor(removed));
        }
        this.activeMiningBlockIds.remove(uniqueId);
        this.miningComponents.remove(uniqueId);
        this.throttleComponents.remove(uniqueId);
        this.respawnComponents.remove(uniqueId);
    }

    public PlacedBlock blockAt(final String worldName, final double x, final double y, final double z) {
        final UUID id = this.blockIdByPosition.get(new PositionKey(worldName, toPositionBits(x), toPositionBits(y), toPositionBits(z)));
        return id == null ? null : this.blocks.get(id);
    }

    public boolean containsAt(final String worldName, final double x, final double y, final double z) {
        return this.blockIdByPosition.containsKey(new PositionKey(worldName, toPositionBits(x), toPositionBits(y), toPositionBits(z)));
    }

    public Collection<PlacedBlock> blocksInChunk(final String worldName, final int chunkX, final int chunkZ) {
        final Set<UUID> ids = this.blocksByChunk.get(new ChunkKey(worldName, chunkX, chunkZ));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        final List<PlacedBlock> result = new ArrayList<>(ids.size());
        for (final UUID id : ids) {
            final PlacedBlock block = this.blocks.get(id);
            if (block != null) {
                result.add(block);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public Collection<PlacedBlock> blocksInChunkWindow(final String worldName, final int centerChunkX, final int centerChunkZ, final int radius) {
        final Set<UUID> ids = new HashSet<>();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                final Set<UUID> chunkIds = this.blocksByChunk.get(new ChunkKey(worldName, x, z));
                if (chunkIds != null) {
                    ids.addAll(chunkIds);
                }
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        final List<PlacedBlock> result = new ArrayList<>(ids.size());
        for (final UUID id : ids) {
            final PlacedBlock block = this.blocks.get(id);
            if (block != null) {
                result.add(block);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void markMiningActive(final UUID blockId) {
        this.activeMiningBlockIds.add(blockId);
    }

    public void unmarkMiningActive(final UUID blockId) {
        this.activeMiningBlockIds.remove(blockId);
    }

    public Set<UUID> activeMiningBlockIdsSnapshot() {
        return Set.copyOf(this.activeMiningBlockIds);
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
        this.blockIdByPosition.clear();
        this.blocksByChunk.clear();
        this.activeMiningBlockIds.clear();
        this.miningComponents.clear();
        this.throttleComponents.clear();
        this.respawnComponents.clear();
    }

    private void addToChunkIndex(final PlacedBlock block) {
        this.blocksByChunk
            .computeIfAbsent(chunkKeyFor(block), ignored -> ConcurrentHashMap.newKeySet())
            .add(block.uniqueId());
    }

    private void removeFromChunkIndex(final PlacedBlock block) {
        final ChunkKey key = chunkKeyFor(block);
        final Set<UUID> ids = this.blocksByChunk.get(key);
        if (ids == null) {
            return;
        }
        ids.remove(block.uniqueId());
        if (ids.isEmpty()) {
            this.blocksByChunk.remove(key);
        }
    }

    private ChunkKey chunkKeyFor(final PlacedBlock block) {
        return new ChunkKey(block.world(), toChunkCoordinate(block.x()), toChunkCoordinate(block.z()));
    }

    private PositionKey positionKeyFor(final PlacedBlock block) {
        return new PositionKey(block.world(), toPositionBits(block.x()), toPositionBits(block.y()), toPositionBits(block.z()));
    }

    private int toChunkCoordinate(final double blockCoordinate) {
        return (int) Math.floor(blockCoordinate) >> 4;
    }

    private long toPositionBits(final double value) {
        return Double.doubleToLongBits(Math.rint(value * 1_000_000.0D) / 1_000_000.0D);
    }

    private record ChunkKey(String worldName, int chunkX, int chunkZ) {
    }

    private record PositionKey(String worldName, long xBits, long yBits, long zBits) {
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

