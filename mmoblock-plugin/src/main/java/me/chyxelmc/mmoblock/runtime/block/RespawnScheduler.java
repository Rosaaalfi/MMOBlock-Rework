package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.ecs.component.RespawnTimerComponent;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Owns respawn task lifecycle for block entities.
 */
public final class RespawnScheduler {

    @SuppressWarnings("unused")
    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockStateRegistry stateRegistry;

    public RespawnScheduler(final MMOBlock plugin, final Scheduler scheduler, final BlockStateRegistry stateRegistry) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.stateRegistry = stateRegistry;
    }

    public void schedule(final PlacedBlockModel block, final long delayMillis, final Runnable onCountdownTick, final Runnable onRespawn) {
        cancel(block.uniqueId());
        final long ticks = Math.max(1L, delayMillis / 50L);

        final SchedulerTask countdownTask = this.scheduler.runTimer(() -> {
            if (!this.stateRegistry.containsBlock(block.uniqueId())) {
                return;
            }
            this.scheduler.runAtLocation(blockLocation(block), () -> {
                if (!this.stateRegistry.containsBlock(block.uniqueId())) {
                    return;
                }
                onCountdownTick.run();
            });
        }, 0L, 20L);

        final SchedulerTask respawnTask = this.scheduler.runAtLocationLater(blockLocation(block), () -> {
            this.stateRegistry.respawn(block.uniqueId()).clearTasks();
            countdownTask.cancel();
            if (!this.stateRegistry.containsBlock(block.uniqueId())) {
                return;
            }
            onRespawn.run();
        }, ticks);

        this.stateRegistry.respawn(block.uniqueId()).setTasks(respawnTask, countdownTask);
    }

    private static Location blockLocation(final PlacedBlockModel block) {
        final World world = Bukkit.getWorld(block.world());
        if (world == null) {
            return null;
        }
        return new Location(world, block.x(), block.y(), block.z());
    }

    public void cancel(final UUID uniqueId) {
        final RespawnTimerComponent respawnComponent = this.stateRegistry.removeRespawnComponent(uniqueId);
        if (respawnComponent == null) {
            return;
        }

        final SchedulerTask respawnTask = respawnComponent.respawnTask();
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        final SchedulerTask countdownTask = respawnComponent.countdownTask();
        if (countdownTask != null) {
            countdownTask.cancel();
        }
    }
}
