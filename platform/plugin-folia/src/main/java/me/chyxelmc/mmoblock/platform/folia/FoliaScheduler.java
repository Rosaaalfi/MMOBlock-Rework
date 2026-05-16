package me.chyxelmc.mmoblock.platform.folia;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Folia-aware implementation of {@link Scheduler} that routes work to the
 * appropriate regionized scheduler (global, region, entity, or async).
 *
 * <p>This class compiles only against the Folia/Canvas API; it must never be
 * loaded on a non-Folia server. The {@code PlatformSchedulerProvider} is
 * responsible for selecting between {@code PaperScheduler} and this class
 * based on a class-presence check at runtime.</p>
 */
public final class FoliaScheduler implements Scheduler {

    private final Plugin plugin;

    public FoliaScheduler(final Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public SchedulerTask run(final Runnable task) {
        final GlobalRegionScheduler global = Bukkit.getGlobalRegionScheduler();
        return wrap(global.run(plugin, t -> task.run()));
    }

    @Override
    public SchedulerTask runLater(final Runnable task, final long delayTicks) {
        final GlobalRegionScheduler global = Bukkit.getGlobalRegionScheduler();
        return wrap(global.runDelayed(plugin, t -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public SchedulerTask runTimer(final Runnable task, final long delayTicks, final long periodTicks) {
        final GlobalRegionScheduler global = Bukkit.getGlobalRegionScheduler();
        return wrap(global.runAtFixedRate(plugin, t -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public SchedulerTask runAsync(final Runnable task) {
        final AsyncScheduler async = Bukkit.getAsyncScheduler();
        return wrap(async.runNow(plugin, t -> task.run()));
    }

    @Override
    public SchedulerTask runAtLocation(final Location location, final Runnable task) {
        if (location == null || location.getWorld() == null) {
            return run(task);
        }
        final RegionScheduler region = Bukkit.getRegionScheduler();
        return wrap(region.run(plugin, location, t -> task.run()));
    }

    @Override
    public SchedulerTask runAtLocationLater(final Location location, final Runnable task, final long delayTicks) {
        if (location == null || location.getWorld() == null) {
            return runLater(task, delayTicks);
        }
        final RegionScheduler region = Bukkit.getRegionScheduler();
        return wrap(region.runDelayed(plugin, location, t -> task.run(), Math.max(1L, delayTicks)));
    }

    @Override
    public SchedulerTask runForEntity(final Entity entity, final Runnable task, final Runnable retired) {
        if (entity == null) {
            return run(task);
        }
        final ScheduledTask scheduled = entity.getScheduler().run(
                plugin,
                t -> task.run(),
                retired == null ? null : retired
        );
        if (scheduled == null) {
            // entity is invalid / retired immediately
            if (retired != null) {
                try {
                    retired.run();
                } catch (final Throwable ignored) {
                }
            }
            return new NoopTask();
        }
        return wrap(scheduled);
    }

    private static SchedulerTask wrap(final ScheduledTask task) {
        return new FoliaSchedulerTask(task);
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
