package me.chyxelmc.mmoblock.platform;

import me.chyxelmc.mmoblock.platform.folia.FoliaScheduler;
import me.chyxelmc.mmoblock.platform.paper.PaperScheduler;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import org.bukkit.plugin.Plugin;

public final class PlatformSchedulerProvider {

    private static final boolean FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (final ClassNotFoundException ignored) {
        }
        FOLIA = folia;
    }

    public static Scheduler createScheduler(final Plugin plugin) {
        if (FOLIA) {
            return new FoliaScheduler(plugin);
        }
        return new PaperScheduler(plugin);
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private PlatformSchedulerProvider() {
    }
}
