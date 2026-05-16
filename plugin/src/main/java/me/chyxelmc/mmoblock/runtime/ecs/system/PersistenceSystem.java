package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

import java.util.UUID;

public final class PersistenceSystem {

    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;
    private final DataCache dataCache;

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

    public void persistBlockAsync(final PlacedBlock block) {
        this.dataCache.cacheBlock(block);
        final PlacedBlock snapshot = new PlacedBlock(
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
        this.scheduler.runAsync(() -> this.blockRepository.upsert(snapshot));
    }

    public void deleteBlockAsync(final UUID uniqueId) {
        this.dataCache.removeBlock(uniqueId);
        this.dataCache.removeRespawn(uniqueId);
        this.scheduler.runAsync(() -> this.blockRepository.delete(uniqueId));
    }

    public void upsertRespawnAsync(final UUID uniqueId, final long respawnAt) {
        this.dataCache.cacheRespawn(uniqueId, respawnAt);
        this.scheduler.runAsync(() -> this.respawnRepository.upsert(uniqueId, respawnAt));
    }

    public void deleteRespawnAsync(final UUID uniqueId) {
        this.dataCache.removeRespawn(uniqueId);
        this.scheduler.runAsync(() -> this.respawnRepository.delete(uniqueId));
    }
}

