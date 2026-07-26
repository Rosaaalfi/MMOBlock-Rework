package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.component.MiningProgressComponent;
import me.chyxelmc.mmoblock.ecs.component.RespawnTimerComponent;

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
 * Runtime state registry for block entities and their components.
 * Heavy mutable state is centralized here to keep runtime service orchestration focused.
 */
public final class BlockStateRegistry {

    private final Map<UUID, PlacedBlockModel> blocks = new ConcurrentHashMap<>();
    private final Map<PositionKey, UUID> blockIdByOriginPosition = new ConcurrentHashMap<>();
    private final Map<PositionKey, UUID> blockIdByCurrentPosition = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Set<UUID>> blocksByChunk = new ConcurrentHashMap<>();
    private final Set<UUID> activeMiningBlockIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MiningProgressComponent> miningComponents = new ConcurrentHashMap<>();
    private final Map<UUID, ClickThrottleComponent> throttleComponents = new ConcurrentHashMap<>();
    private final Map<UUID, RespawnTimerComponent> respawnComponents = new ConcurrentHashMap<>();

    public void putBlock(final PlacedBlockModel block) {
        final PlacedBlockModel previous = this.blocks.put(block.uniqueId(), block);
        if (previous != null) {
            removeFromChunkIndex(previous);
            this.blockIdByOriginPosition.remove(positionKeyFor(previous.originX(), previous.originY(), previous.originZ(), previous.world()));
            this.blockIdByCurrentPosition.remove(positionKeyFor(previous.x(), previous.y(), previous.z(), previous.world()));
        }
        addToChunkIndex(block);
        this.blockIdByOriginPosition.put(positionKeyFor(block.originX(), block.originY(), block.originZ(), block.world()), block.uniqueId());
        this.blockIdByCurrentPosition.put(positionKeyFor(block.x(), block.y(), block.z(), block.world()), block.uniqueId());
        // MiningProgressComponent, ClickThrottleComponent, and RespawnTimerComponent are
        // created lazily on first access via mining(), throttle(), and respawn() — no
        // need to eagerly allocate them here. Saves memory for blocks never mined/interacted.
    }

    public PlacedBlockModel getBlock(final UUID uniqueId) {
        return this.blocks.get(uniqueId);
    }

    public Collection<PlacedBlockModel> blocks() {
        return Collections.unmodifiableCollection(this.blocks.values());
    }

    public Collection<PlacedBlockModel> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(this.blocks.values()));
    }

    public boolean containsBlock(final UUID uniqueId) {
        return this.blocks.containsKey(uniqueId);
    }

    public void removeBlock(final UUID uniqueId) {
        final PlacedBlockModel removed = this.blocks.remove(uniqueId);
        if (removed != null) {
            removeFromChunkIndex(removed);
            this.blockIdByOriginPosition.remove(positionKeyFor(removed.originX(), removed.originY(), removed.originZ(), removed.world()));
            this.blockIdByCurrentPosition.remove(positionKeyFor(removed.x(), removed.y(), removed.z(), removed.world()));
        }
        this.activeMiningBlockIds.remove(uniqueId);
        this.miningComponents.remove(uniqueId);
        this.throttleComponents.remove(uniqueId);
        this.respawnComponents.remove(uniqueId);
    }

    public void updateBlockPosition(final PlacedBlockModel block, final double oldX, final double oldY, final double oldZ) {
        this.blockIdByCurrentPosition.remove(new PositionKey(block.world(), toPositionBits(oldX), toPositionBits(oldY), toPositionBits(oldZ)));
        this.blockIdByCurrentPosition.put(positionKeyFor(block.x(), block.y(), block.z(), block.world()), block.uniqueId());
        removeChunkMembership(block.world(), oldX, oldZ, block.uniqueId());
        addToChunkIndex(block);
    }

    public PlacedBlockModel blockAt(final String worldName, final double x, final double y, final double z) {
        final PositionKey key = new PositionKey(worldName, toPositionBits(x), toPositionBits(y), toPositionBits(z));
        UUID id = this.blockIdByOriginPosition.get(key);
        if (id == null) {
            id = this.blockIdByCurrentPosition.get(key);
        }
        return id == null ? null : this.blocks.get(id);
    }

    public boolean containsAt(final String worldName, final double x, final double y, final double z) {
        final PositionKey key = new PositionKey(worldName, toPositionBits(x), toPositionBits(y), toPositionBits(z));
        return this.blockIdByOriginPosition.containsKey(key) || this.blockIdByCurrentPosition.containsKey(key);
    }

    public Collection<PlacedBlockModel> blocksInChunk(final String worldName, final int chunkX, final int chunkZ) {
        final Set<UUID> ids = this.blocksByChunk.get(new ChunkKey(worldName, chunkX, chunkZ));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        final List<PlacedBlockModel> result = new ArrayList<>(ids.size());
        for (final UUID id : ids) {
            final PlacedBlockModel block = this.blocks.get(id);
            if (block != null) {
                result.add(block);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public Collection<PlacedBlockModel> blocksInChunkWindow(final String worldName, final int centerChunkX, final int centerChunkZ, final int radius) {
        final Set<UUID> ids = new HashSet<>();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                final ChunkKey key = new ChunkKey(worldName, x, z);
                final Set<UUID> chunkIds = this.blocksByChunk.get(key);
                if (chunkIds != null) {
                    ids.addAll(chunkIds);
                }
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        final List<PlacedBlockModel> result = new ArrayList<>(ids.size());
        for (final UUID id : ids) {
            final PlacedBlockModel block = this.blocks.get(id);
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

    public RespawnTimerComponent respawn(final UUID uniqueId) {
        return this.respawnComponents.computeIfAbsent(uniqueId, ignored -> new RespawnTimerComponent());
    }

    public RespawnTimerComponent removeRespawnComponent(final UUID uniqueId) {
        return this.respawnComponents.remove(uniqueId);
    }

    public void clear() {
        this.blocks.clear();
        this.blockIdByOriginPosition.clear();
        this.blockIdByCurrentPosition.clear();
        this.blocksByChunk.clear();
        this.activeMiningBlockIds.clear();
        this.miningComponents.clear();
        this.throttleComponents.clear();
        this.respawnComponents.clear();
    }

    private void addToChunkIndex(final PlacedBlockModel block) {
        this.blocksByChunk
            .computeIfAbsent(chunkKeyFor(block), ignored -> ConcurrentHashMap.newKeySet())
            .add(block.uniqueId());
    }

    private void removeFromChunkIndex(final PlacedBlockModel block) {
        removeChunkMembership(block.world(), block.x(), block.z(), block.uniqueId());
    }

    private void removeChunkMembership(final String worldName, final double x, final double z, final UUID blockId) {
        final ChunkKey key = new ChunkKey(worldName, toChunkCoordinate(x), toChunkCoordinate(z));
        final Set<UUID> ids = this.blocksByChunk.get(key);
        if (ids == null) {
            return;
        }
        ids.remove(blockId);
        if (ids.isEmpty()) {
            this.blocksByChunk.remove(key);
        }
    }

    private ChunkKey chunkKeyFor(final PlacedBlockModel block) {
        return new ChunkKey(block.world(), toChunkCoordinate(block.x()), toChunkCoordinate(block.z()));
    }

    private PositionKey positionKeyFor(final double x, final double y, final double z, final String worldName) {
        return new PositionKey(worldName, toPositionBits(x), toPositionBits(y), toPositionBits(z));
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
}
