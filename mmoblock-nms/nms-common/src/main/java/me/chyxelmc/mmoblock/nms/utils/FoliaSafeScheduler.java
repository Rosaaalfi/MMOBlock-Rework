package me.chyxelmc.mmoblock.nms.utils;

import org.bukkit.plugin.Plugin;

/**
 * @deprecated Moved to {@code me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler}
 *             in the platform-scheduler module. This class now delegates to the new location.
 */
@Deprecated(forRemoval = true)
public final class FoliaSafeScheduler {

    private FoliaSafeScheduler() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void runTask(final Plugin plugin, final Runnable task) {
        me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler.runTask(plugin, task);
    }

    public static void runTaskLater(final Plugin plugin, final Runnable task, final long delayTicks) {
        me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler.runTaskLater(plugin, task, delayTicks);
    }

    public static boolean isFolia() {
        return me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler.isFolia();
    }
}
