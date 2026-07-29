package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PersistenceSystem {

    @SuppressWarnings("unused")
    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;
    private final DataCache dataCache;
    private final Set<UUID> deletedBlockIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicLong> respawnVersions = new ConcurrentHashMap<>();

    public PersistenceSystem(
        final MMOBlock plugin,
        final Scheduler scheduler,
        final BlockRepository blockRepository,
        final RespawnRepository respawnRepository,
        final DataCache dataCache
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.blockRepository = blockRepository;
        this.respawnRepository = respawnRepository;
        this.dataCache = dataCache;
    }

    public void persistBlockAsync(final PlacedBlockModel block) {
        if (this.deletedBlockIds.contains(block.uniqueId())) {
            return;
        }
        this.dataCache.cacheBlock(block);
        final PlacedBlockModel snapshot = new PlacedBlockModel(
            block.uniqueId(),
            block.type(),
            block.world(),
            block.originX(),
            block.originY(),
            block.originZ(),
            block.x(),
            block.y(),
            block.z(),
            block.facing(),
            block.status()
        );
        this.scheduler.runAsync(() -> {
            if (!this.deletedBlockIds.contains(snapshot.uniqueId())) {
                this.blockRepository.upsert(snapshot);
            }
        });
    }

    public void deleteBlockAsync(final UUID uniqueId) {
        this.deletedBlockIds.add(uniqueId);
        this.dataCache.removeBlock(uniqueId);
        this.dataCache.removeRespawn(uniqueId);
        this.scheduler.runAsync(() -> this.blockRepository.delete(uniqueId));
    }

    public void upsertRespawnAsync(final UUID uniqueId, final long respawnAt) {
        if (this.deletedBlockIds.contains(uniqueId)) {
            return;
        }
        final long version = nextRespawnVersion(uniqueId);
        this.dataCache.cacheRespawn(uniqueId, respawnAt);
        this.scheduler.runAsync(() -> {
            if (!this.deletedBlockIds.contains(uniqueId) && currentRespawnVersion(uniqueId) == version) {
                this.respawnRepository.upsert(uniqueId, respawnAt);
            }
        });
    }

    public void deleteRespawnAsync(final UUID uniqueId) {
        final long version = nextRespawnVersion(uniqueId);
        this.dataCache.removeRespawn(uniqueId);
        this.scheduler.runAsync(() -> {
            if (currentRespawnVersion(uniqueId) == version) {
                this.respawnRepository.delete(uniqueId);
            }
        });
    }

    private long nextRespawnVersion(final UUID uniqueId) {
        return this.respawnVersions.computeIfAbsent(uniqueId, ignored -> new AtomicLong()).incrementAndGet();
    }

    private long currentRespawnVersion(final UUID uniqueId) {
        final AtomicLong version = this.respawnVersions.get(uniqueId);
        return version == null ? 0L : version.get();
    }
}
