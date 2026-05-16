package me.chyxelmc.mmoblock.platform.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Platform-agnostic scheduler abstraction.
 *
 * <p>Two implementations are provided:</p>
 * <ul>
 *     <li>{@code PaperScheduler} – uses the classic {@link org.bukkit.scheduler.BukkitScheduler}
 *         which runs a single global main thread. Safe for Paper/Spigot.</li>
 *     <li>{@code FoliaScheduler} – uses Folia's regionized schedulers (global,
 *         region, entity, async) to honor Folia's per-region threading model.</li>
 * </ul>
 *
 * <p>For methods without an explicit location/entity, the global region scheduler
 * is used on Folia and the main thread scheduler on Paper.</p>
 */
public interface Scheduler {

    /**
     * Runs the task on the next main/global tick.
     */
    SchedulerTask run(Runnable task);

    /**
     * Runs the task after the given delay (in ticks) on the main/global thread.
     */
    SchedulerTask runLater(Runnable task, long delayTicks);

    /**
     * Runs the task repeatedly on the main/global thread.
     */
    SchedulerTask runTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs the task asynchronously on a worker thread.
     */
    SchedulerTask runAsync(Runnable task);

    /**
     * Runs the task on the region/thread that owns the given location.
     * On Paper this is equivalent to {@link #run(Runnable)}.
     */
    SchedulerTask runAtLocation(Location location, Runnable task);

    /**
     * Runs the task on the region/thread that owns the given location after the given delay.
     * On Paper this is equivalent to {@link #runLater(Runnable, long)}.
     */
    SchedulerTask runAtLocationLater(Location location, Runnable task, long delayTicks);

    /**
     * Runs the task on the region/thread that owns the given entity.
     * On Paper this is equivalent to {@link #run(Runnable)}.
     * The retired callback (Folia only) runs if the entity is removed before the task executes.
     */
    SchedulerTask runForEntity(Entity entity, Runnable task, Runnable retired);
}
