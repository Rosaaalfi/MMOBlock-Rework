package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Isolated reconcile workflow for config reload runtime syncing.
 */
public final class ReconcileCoordinator {

    public ReconcileResult reconcile(
        final List<PlacedBlockModel> snapshot,
        final boolean rebindActiveInteractions,
        final Function<String, BlockDefinitionModel> definitionLookup,
        final Function<String, World> worldLookup,
        final Predicate<PlacedBlockModel> isActive,
        final Function<UUID, Long> respawnLookup,
        final Consumer<PlacedBlockModel> cleanupMissingDefinition,
        final Consumer<PlacedBlockModel> markActive,
        final Consumer<PlacedBlockModel> persistBlock,
        final SpawnOperation spawnOperation,
        final BiConsumer<PlacedBlockModel, BlockDefinitionModel> showActive,
        final ShowDeadOperation showDead,
        final ScheduleRespawnOperation scheduleRespawn,
        final Consumer<PlacedBlockModel> despawnOperation
    ) {
        int rebound = 0;
        int cleaned = 0;
        int rescheduled = 0;
        int failed = 0;

        for (final PlacedBlockModel block : snapshot) {
            final BlockDefinitionModel definition = definitionLookup.apply(block.type());
            if (definition == null) {
                cleanupMissingDefinition.accept(block);
                cleaned++;
                continue;
            }

            final World world = worldLookup.apply(block.world());
            if (world == null) {
                continue;
            }

            if (!isActive.test(block)) {
                final Long respawnAt = respawnLookup.apply(block.uniqueId());
                if (respawnAt == null) {
                    markActive.accept(block);
                    persistBlock.accept(block);
                    if (spawnOperation.spawn(block, definition, world)) {
                        rebound++;
                    } else {
                        failed++;
                    }
                    continue;
                }

                final long delay = Math.max(1L, respawnAt - System.currentTimeMillis());
                showDead.show(block, definition, delay);
                scheduleRespawn.schedule(block, world, delay);
                rescheduled++;
                continue;
            }

            if (!rebindActiveInteractions) {
                continue;
            }

            despawnOperation.accept(block);
            if (spawnOperation.spawn(block, definition, world)) {
                rebound++;
                showActive.accept(block, definition);
            } else {
                failed++;
            }
        }

        return new ReconcileResult(rebound, cleaned, rescheduled, failed);
    }

    @FunctionalInterface
    public interface SpawnOperation {
        boolean spawn(PlacedBlockModel block, BlockDefinitionModel definition, World world);
    }

    @FunctionalInterface
    public interface ShowDeadOperation {
        void show(PlacedBlockModel block, BlockDefinitionModel definition, long delayMillis);
    }

    @FunctionalInterface
    public interface ScheduleRespawnOperation {
        void schedule(PlacedBlockModel block, World world, long delayMillis);
    }
}
