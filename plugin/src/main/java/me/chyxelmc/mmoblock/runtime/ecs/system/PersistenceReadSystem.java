package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read-side persistence gateway to decouple runtime orchestration from repositories.
 */
public final class PersistenceReadSystem {

    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;

    public PersistenceReadSystem(
        final BlockRepository blockRepository,
        final RespawnRepository respawnRepository
    ) {
        this.blockRepository = blockRepository;
        this.respawnRepository = respawnRepository;
    }

    public List<PlacedBlock> findAllPlacedBlocks() {
        return this.blockRepository.findAll();
    }

    public Long findRespawnAt(final UUID uniqueId) {
        return this.respawnRepository.findById(uniqueId);
    }
}

