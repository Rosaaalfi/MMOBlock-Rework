package me.chyxelmc.mmoblock.runtime.ecs.system;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlock;
import me.chyxelmc.mmoblock.runtime.ecs.BlockEcsState;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Owns respawn task lifecycle for ECS entities.
 */
public final class RespawnSystem {

    private final MMOBlock plugin;
    private final BlockEcsState ecsState;

    public RespawnSystem(final MMOBlock plugin, final BlockEcsState ecsState) {
        this.plugin = plugin;
        this.ecsState = ecsState;
    }

    public void schedule(final PlacedBlock block, final long delayMillis, final Runnable onCountdownTick, final Runnable onRespawn) {
        cancel(block.uniqueId());
        final long ticks = Math.max(1L, delayMillis / 50L);

        final BukkitTask countdownTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            onCountdownTick.run();
        }, 0L, 20L);

        final BukkitTask respawnTask = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.ecsState.respawn(block.uniqueId()).clearTasks();
            countdownTask.cancel();
            if (!this.ecsState.containsBlock(block.uniqueId())) {
                return;
            }
            onRespawn.run();
        }, ticks);

        this.ecsState.respawn(block.uniqueId()).setTasks(respawnTask, countdownTask);
    }

    public void cancel(final UUID uniqueId) {
        final BlockEcsState.RespawnComponent respawnComponent = this.ecsState.removeRespawnComponent(uniqueId);
        if (respawnComponent == null) {
            return;
        }

        final BukkitTask respawnTask = respawnComponent.respawnTask();
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        final BukkitTask countdownTask = respawnComponent.countdownTask();
        if (countdownTask != null) {
            countdownTask.cancel();
        }
    }
}

