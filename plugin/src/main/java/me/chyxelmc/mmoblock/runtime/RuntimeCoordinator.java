package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.runtime.ecs.system.PersistenceReadSystem;

import java.util.List;

/**
 * Coordinates runtime lifecycle entry points so MMOBlock and commands stay thin.
 */
public final class RuntimeCoordinator {

    private final PersistenceReadSystem persistenceReadSystem;
    private final BlockRuntimeService blockRuntimeService;
    private final NodeRuntimeService nodeRuntimeService;

    public RuntimeCoordinator(
        final PersistenceReadSystem persistenceReadSystem,
        final BlockRuntimeService blockRuntimeService,
        final NodeRuntimeService nodeRuntimeService
    ) {
        this.persistenceReadSystem = persistenceReadSystem;
        this.blockRuntimeService = blockRuntimeService;
        this.nodeRuntimeService = nodeRuntimeService;
    }

    public void restoreFromPersistence() {
        final List<PlacedBlock> persistedBlocks = this.persistenceReadSystem.findAllPlacedBlocks();
        this.blockRuntimeService.restoreFromPersistence(persistedBlocks);
        if (this.nodeRuntimeService != null) {
            this.nodeRuntimeService.restoreFromPersistence();
        }
    }

    public BlockRuntimeService.ReconcileResult reconcileAfterConfigReload(final boolean rebindActiveInteractions) {
        return this.blockRuntimeService.reconcileAfterConfigReload(rebindActiveInteractions);
    }

    public void shutdown() {
        if (this.nodeRuntimeService != null) {
            this.nodeRuntimeService.shutdown();
        }
        this.blockRuntimeService.shutdown();
    }
}
