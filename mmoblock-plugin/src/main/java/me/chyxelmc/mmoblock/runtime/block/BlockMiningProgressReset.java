package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import me.chyxelmc.mmoblock.ecs.system.BlockMiningSystem;
import me.chyxelmc.mmoblock.ecs.system.LifecycleSystem;
import me.chyxelmc.mmoblock.ecs.system.VisualSyncSystem;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public final class BlockMiningProgressReset {

    private static final long RESET_TIMEOUT_MS = 5000L;
    private static final long RESET_CHECK_TICKS = 20L;

    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockConfigLoader blockConfigService;
    private final BlockEcsState ecsState;
    private final BlockMiningSystem miningSystem;
    private final LifecycleSystem lifecycleSystem;
    private final VisualSyncSystem visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private SchedulerTask task;

    public BlockMiningProgressReset(
            final MMOBlock plugin,
            final Scheduler scheduler,
            final BlockConfigLoader blockConfigService,
            final BlockEcsState ecsState,
            final BlockMiningSystem miningSystem,
            final LifecycleSystem lifecycleSystem,
            final VisualSyncSystem visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.blockConfigService = blockConfigService;
        this.ecsState = ecsState;
        this.miningSystem = miningSystem;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
    }

    public void start() {
        stop();
        this.task = this.scheduler.runTimer(this::resetInactiveProgress, RESET_CHECK_TICKS, RESET_CHECK_TICKS);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void resetInactiveProgress() {
        final long now = System.currentTimeMillis();
        for (final UUID blockId : this.miningSystem.activeMiningBlockIds()) {
            final PlacedBlockModel block = this.ecsState.getBlock(blockId);
            if (block == null) {
                this.miningSystem.clearAllProgress(blockId);
                continue;
            }
            if (!this.lifecycleSystem.isActive(block)) {
                continue;
            }
            if (this.miningSystem.evictInactiveProgress(block.uniqueId(), now, RESET_TIMEOUT_MS).isEmpty()) {
                continue;
            }
            if (this.miningSystem.hasAnyProgress(block.uniqueId())) {
                continue;
            }

            final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
            final World world = this.plugin.getServer().getWorld(block.world());
            if (world != null && definition != null) {
                final Location location = new Location(world, block.x(), block.y(), block.z());
                this.scheduler.runAtLocation(location, () -> {
                    try {
                        this.visualSyncSystem.clearBreakAnimation(world, block);
                    } catch (final Exception ignored) {
                        // expected - reflection fallback
                    }
                    this.hologramRuntimeService.showActive(block, definition);
                });
            }
        }
    }
}
