package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.persistence.BlockRepository;
import me.chyxelmc.mmoblock.persistence.RespawnRepository;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * Async persistence gateway for block and respawn data.
 */
public final class PersistenceSystem {

    private final MMOBlock plugin;
    private final BlockRepository blockRepository;
    private final RespawnRepository respawnRepository;

    public PersistenceSystem(
        final MMOBlock plugin,
        final BlockRepository blockRepository,
        final RespawnRepository respawnRepository
    ) {
        this.plugin = plugin;
        this.blockRepository = blockRepository;
        this.respawnRepository = respawnRepository;
    }

    public void persistBlockAsync(final PlacedBlock block) {
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
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.blockRepository.upsert(snapshot));
    }

    public void deleteBlockAsync(final UUID uniqueId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.blockRepository.delete(uniqueId));
    }

    public void upsertRespawnAsync(final UUID uniqueId, final long respawnAt) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.respawnRepository.upsert(uniqueId, respawnAt));
    }

    public void deleteRespawnAsync(final UUID uniqueId) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> this.respawnRepository.delete(uniqueId));
    }
}

