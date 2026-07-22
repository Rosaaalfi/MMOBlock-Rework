package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import me.chyxelmc.mmoblock.ecs.BlockEcsState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Owns respawn task lifecycle for ECS entities.
 *
 * <p>Renamed from {@code RespawnSystem} to align with the naming convention
 * where systems that manage specific block lifecycle concerns are prefixed
 * with {@code Block}.</p>
 */
public final class BlockRespawnSystem {

    @SuppressWarnings("unused")
    private final MMOBlock plugin;
    private final Scheduler scheduler;
    private final BlockEcsState ecsState;

    public BlockRespawnSystem(final MMOBlock plugin, final Scheduler scheduler, final BlockEcsState ecsState) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.ecsState = ecsState;
    }

    public void schedule(final PlacedBlockModel block, final long delayMillis, final Runnable onCountdownTick, final Runnable onRespawn) {
        cancel(block.uniqueId());
        final long ticks = Math.max(1L, delayMillis / 50L);

        final SchedulerTask countdownTask = this.scheduler.runTimer(() -> {
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            this.scheduler.runAtLocation(blockLocation(block), () -> {
                if (!this.ecsState.containsBlock(block.uniqueId())) {
                    return;
                }
                onCountdownTick.run();
            });
        }, 0L, 20L);

        final SchedulerTask respawnTask = this.scheduler.runAtLocationLater(blockLocation(block), () -> {
            this.ecsState.respawn(block.uniqueId()).clearTasks();
            countdownTask.cancel();
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            onRespawn.run();
        }, ticks);

        this.ecsState.respawn(block.uniqueId()).setTasks(respawnTask, countdownTask);
    }

    private static Location blockLocation(final PlacedBlockModel block) {
        final World world = Bukkit.getWorld(block.world());
        if (world == null) {
            return null;
        }
        return new Location(world, block.x(), block.y(), block.z());
    }

    public void cancel(final UUID uniqueId) {
        final me.chyxelmc.mmoblock.ecs.component.RespawnTimerComponent respawnComponent = this.ecsState.removeRespawnComponent(uniqueId);
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
