package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
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
public final class ReconcileSystem {

    public BlockRuntimeService.ReconcileResult reconcile(
        final List<PlacedBlock> snapshot,
        final boolean rebindActiveInteractions,
        final Function<String, BlockDefinition> definitionLookup,
        final Function<String, World> worldLookup,
        final Predicate<PlacedBlock> isActive,
        final Function<UUID, Long> respawnLookup,
        final Consumer<PlacedBlock> cleanupMissingDefinition,
        final Consumer<PlacedBlock> markActive,
        final Consumer<PlacedBlock> persistBlock,
        final SpawnOperation spawnOperation,
        final BiConsumer<PlacedBlock, BlockDefinition> showActive,
        final ShowDeadOperation showDead,
        final ScheduleRespawnOperation scheduleRespawn,
        final Consumer<PlacedBlock> despawnOperation
    ) {
        int rebound = 0;
        int cleaned = 0;
        int rescheduled = 0;
        int failed = 0;

        for (final PlacedBlock block : snapshot) {
            final BlockDefinition definition = definitionLookup.apply(block.type());
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

        return new BlockRuntimeService.ReconcileResult(rebound, cleaned, rescheduled, failed);
    }

    @FunctionalInterface
    public interface SpawnOperation {
        boolean spawn(PlacedBlock block, BlockDefinition definition, World world);
    }

    @FunctionalInterface
    public interface ShowDeadOperation {
        void show(PlacedBlock block, BlockDefinition definition, long delayMillis);
    }

    @FunctionalInterface
    public interface ScheduleRespawnOperation {
        void schedule(PlacedBlock block, World world, long delayMillis);
    }
}

