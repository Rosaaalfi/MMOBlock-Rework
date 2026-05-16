package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import org.bukkit.Bukkit;

import java.util.UUID;

public final class PersistenceSystem {

    private final MMOBlock plugin;
    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;
    private final DataCache dataCache;

    public PersistenceSystem(
        final MMOBlock plugin,
        final BlockRepository blockRepository,
        final RespawnRepository respawnRepository,
        final DataCache dataCache
    ) {
        this.plugin = plugin;
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
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.blockRepository.upsert(snapshot);
        });
    }

    public void deleteBlockAsync(final UUID uniqueId) {
        this.dataCache.removeBlock(uniqueId);
        this.dataCache.removeRespawn(uniqueId);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.blockRepository.delete(uniqueId));
    }

    public void upsertRespawnAsync(final UUID uniqueId, final long respawnAt) {
        this.dataCache.cacheRespawn(uniqueId, respawnAt);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.respawnRepository.upsert(uniqueId, respawnAt));
    }

    public void deleteRespawnAsync(final UUID uniqueId) {
        this.dataCache.removeRespawn(uniqueId);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.respawnRepository.delete(uniqueId));
    }
}

