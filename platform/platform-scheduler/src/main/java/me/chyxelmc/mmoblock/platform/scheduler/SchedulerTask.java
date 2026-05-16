package me.chyxelmc.mmoblock.platform.scheduler;

/**
 * Platform-agnostic scheduled task handle. Implementations wrap either a
 * Bukkit/Paper {@link org.bukkit.scheduler.BukkitTask} or a Folia
 * {@code ScheduledTask}, exposing only the operations the plugin needs.
 */
public interface SchedulerTask {

    /**
     * Cancels the scheduled task. Implementations must be safe to call even
     * if the task has already finished or been cancelled previously.
     */
    void cancel();

    /**
     * Returns whether this task has been cancelled.
     */
    boolean isCancelled();
}
