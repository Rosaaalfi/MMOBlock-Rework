package me.chyxelmc.mmoblock.platform.paper;

import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Single-threaded (Bukkit main thread) implementation of {@link Scheduler} for
 * Paper/Spigot servers. All synchronous variants are routed to the main
 * thread; async variants are routed to Bukkit's async worker pool.
 */
public final class PaperScheduler implements Scheduler {

    private final Plugin plugin;

    public PaperScheduler(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public SchedulerTask run(final Runnable task) {
        return wrap(Bukkit.getScheduler().runTask(plugin, task));
    }

    @Override
    public SchedulerTask runLater(final Runnable task, final long delayTicks) {
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks)));
    }

    @Override
    public SchedulerTask runTimer(final Runnable task, final long delayTicks, final long periodTicks) {
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public SchedulerTask runAsync(final Runnable task) {
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public SchedulerTask runAtLocation(final Location location, final Runnable task) {
        return run(task);
    }

    @Override
    public SchedulerTask runAtLocationLater(final Location location, final Runnable task, final long delayTicks) {
        return runLater(task, delayTicks);
    }

    @Override
    public SchedulerTask runForEntity(final Entity entity, final Runnable task, final Runnable retired) {
        if (entity == null || !entity.isValid()) {
            if (retired != null) {
                try {
                    retired.run();
                } catch (final Throwable ignored) {
                }
            }
            return new NoopTask();
        }
        return run(task);
    }

    private static SchedulerTask wrap(final BukkitTask task) {
        return new PaperSchedulerTask(task);
    }

    private static final class NoopTask implements SchedulerTask {
        private volatile boolean cancelled;

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }
    }
}
