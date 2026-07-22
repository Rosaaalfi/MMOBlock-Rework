package me.chyxelmc.mmoblock.platform.scheduler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Safe scheduler utility that works on both Bukkit/Paper (single-threaded)
 * and Folia (regionized threading model).
 *
 * <p>This class uses reflection to detect Folia at runtime and automatically
 * selects the appropriate scheduling mechanism. It is a legacy utility;
 * for new code, prefer the {@link Scheduler} interface with
 * {@code PaperScheduler} or {@code FoliaScheduler} implementations.</p>
 *
 * <p>Moved from nms-loader/utils to platform-scheduler in Phase 1 refactoring.</p>
 */
public final class FoliaSafeScheduler {

    private static final boolean FOLIA;
    private static final MethodHandle GLOBAL_SCHEDULER_RUN;
    private static final MethodHandle GLOBAL_SCHEDULER_RUN_DELAYED;
    private static final MethodHandle GET_GLOBAL_REGION_SCHEDULER;


    static {
        boolean folia = false;
        MethodHandle globalSchedulerRun = null;
        MethodHandle globalSchedulerRunDelayed = null;
        MethodHandle getGlobalRegionScheduler = null;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
            final Class<?> globalRegionSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            final Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            getGlobalRegionScheduler = lookup.findStatic(Bukkit.class, "getGlobalRegionScheduler", MethodType.methodType(globalRegionSchedulerClass));
            globalSchedulerRun = lookup.findVirtual(globalRegionSchedulerClass, "run",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class));
            globalSchedulerRunDelayed = lookup.findVirtual(globalRegionSchedulerClass, "runDelayed",
                    MethodType.methodType(scheduledTaskClass, Plugin.class, Consumer.class, long.class));
        } catch (final Exception ignored) {
            // Folia not available; falling back to standard Bukkit scheduler
        }
        FOLIA = folia;
        GET_GLOBAL_REGION_SCHEDULER = getGlobalRegionScheduler;
        GLOBAL_SCHEDULER_RUN = globalSchedulerRun;
        GLOBAL_SCHEDULER_RUN_DELAYED = globalSchedulerRunDelayed;
    }

    private FoliaSafeScheduler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void runTask(final Plugin plugin, final Runnable task) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (FOLIA) {
            runOnGlobalScheduler(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runTaskLater(final Plugin plugin, final Runnable task, final long delayTicks) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(task, "task");
        if (FOLIA) {
            runOnGlobalSchedulerDelayed(plugin, task, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    @SuppressWarnings("java:S1181") // MethodHandle.invoke() throws Throwable by design
    private static void runOnGlobalScheduler(final Plugin plugin, final Runnable task) {
        try {
            final Object globalScheduler = GET_GLOBAL_REGION_SCHEDULER.invoke();
            GLOBAL_SCHEDULER_RUN.invoke(globalScheduler, plugin, getWrapped(task));
        } catch (final Throwable ignored) {
            // Scheduler execution failed silently
        }
    }

    @SuppressWarnings("java:S1181") // MethodHandle.invoke() throws Throwable by design
    private static void runOnGlobalSchedulerDelayed(final Plugin plugin, final Runnable task, final long delayTicks) {
        try {
            final Object globalScheduler = GET_GLOBAL_REGION_SCHEDULER.invoke();
            GLOBAL_SCHEDULER_RUN_DELAYED.invoke(globalScheduler, plugin, getWrapped(task), delayTicks);
        } catch (final Throwable ignored) {
            // Scheduler execution failed silently
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static Consumer<Object> getWrapped(final Runnable task) {
        return ignored -> task.run();
    }
}
