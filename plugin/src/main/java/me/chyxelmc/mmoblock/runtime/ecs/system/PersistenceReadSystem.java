package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;

import java.util.List;
import java.util.UUID;

public final class PersistenceReadSystem {

    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;
    private final DataCache dataCache;

    public PersistenceReadSystem(
        final BlockRepository blockRepository,
        final RespawnRepository respawnRepository,
        final DataCache dataCache
    ) {
        this.blockRepository = blockRepository;
        this.respawnRepository = respawnRepository;
        this.dataCache = dataCache;
    }

    public List<PlacedBlock> findAllPlacedBlocks() {
        return this.blockRepository.findAll();
    }

    public Long findRespawnAt(final UUID uniqueId) {
        if (uniqueId == null) return null;
        return this.respawnRepository.findById(uniqueId);
    }

    public DataCache dataCache() {
        return this.dataCache;
    }
}

