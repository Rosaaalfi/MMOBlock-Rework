package me.chyxelmc.mmoblock.persistence.cache;

import com.github.benmanes.caffeine.cache.Cache;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.model.PlacedNode;
import me.chyxelmc.mmoblock.utils.Caching;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataCache {

    private final Cache<UUID, PlacedBlock> blockCache;
    private final Cache<UUID, Long> respawnCache;
    private final Cache<String, List<UUID>> blocksByTypeCache;
    private final Cache<String, List<PlacedBlock>> allBlocksCache;
    private final Cache<String, List<PlacedNode>> allNodesCache;
    private final Map<String, Long> dbTimestamps;

    public DataCache() {
        this.blockCache = Caching.mediumCache();
        this.respawnCache = Caching.smallCache();
        this.blocksByTypeCache = Caching.smallCache();
        this.allBlocksCache = Caching.smallCache();
        this.allNodesCache = Caching.smallCache();
        this.dbTimestamps = new ConcurrentHashMap<>();
    }

    public void cacheBlock(final PlacedBlock block) {
        if (block != null) {
            this.blockCache.put(block.uniqueId(), block);
        }
    }

    public PlacedBlock getBlock(final UUID uniqueId) {
        return this.blockCache.getIfPresent(uniqueId);
    }

    public void removeBlock(final UUID uniqueId) {
        this.blockCache.invalidate(uniqueId);
        this.respawnCache.invalidate(uniqueId);
    }

    public void cacheBlocks(final List<PlacedBlock> blocks) {
        for (final PlacedBlock block : blocks) {
            cacheBlock(block);
        }
    }

    public void cacheRespawn(final UUID uniqueId, final long respawnAt) {
        if (uniqueId != null) {
            this.respawnCache.put(uniqueId, respawnAt);
        }
    }

    public Long getRespawn(final UUID uniqueId) {
        return this.respawnCache.getIfPresent(uniqueId);
    }

    public void removeRespawn(final UUID uniqueId) {
        this.respawnCache.invalidate(uniqueId);
    }

    public void cacheBlocksByType(final String type, final List<UUID> blockIds) {
        if (type != null) {
            this.blocksByTypeCache.put(type.toLowerCase(), blockIds);
        }
    }

    public List<UUID> getBlocksByType(final String type) {
        return this.blocksByTypeCache.getIfPresent(type != null ? type.toLowerCase() : "");
    }

    public void invalidateBlocksByType(final String type) {
        if (type != null) {
            this.blocksByTypeCache.invalidate(type.toLowerCase());
        }
    }

    public void markDbTimestamp(final String key) {
        this.dbTimestamps.put(key, System.currentTimeMillis());
    }

    public long getDbTimestamp(final String key) {
        return this.dbTimestamps.getOrDefault(key, 0L);
    }

    public boolean isStale(final String key, final long ttlMs) {
        return System.currentTimeMillis() - getDbTimestamp(key) > ttlMs;
    }

    public void cacheAllBlocks(final List<PlacedBlock> blocks) {
        this.allBlocksCache.put("_all", blocks);
        this.dbTimestamps.put("_all_blocks", System.currentTimeMillis());
    }

    public List<PlacedBlock> getAllBlocks() {
        return this.allBlocksCache.getIfPresent("_all");
    }

    public void invalidateAllBlocks() {
        this.allBlocksCache.invalidate("_all");
    }

    public boolean isAllBlocksStale(final long ttlMs) {
        return isStale("_all_blocks", ttlMs);
    }

    public void cacheNodes(final List<PlacedNode> nodes) {
        this.allNodesCache.put("_all", nodes);
        this.dbTimestamps.put("_all_nodes", System.currentTimeMillis());
    }

    public List<PlacedNode> getAllNodes() {
        return this.allNodesCache.getIfPresent("_all");
    }

    public void clear() {
        this.blockCache.invalidateAll();
        this.respawnCache.invalidateAll();
        this.blocksByTypeCache.invalidateAll();
        this.allBlocksCache.invalidateAll();
        this.allNodesCache.invalidateAll();
        this.dbTimestamps.clear();
    }

    public void clearAll() {
        clear();
    }
}
