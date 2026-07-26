package me.chyxelmc.mmoblock.platform.paper;

import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.scheduler.BukkitTask;

final class PaperSchedulerTask implements SchedulerTask {

    private final BukkitTask handle;

    PaperSchedulerTask(final BukkitTask handle) {
        this.handle = handle;
    }

    @Override
    public void cancel() {
        if (this.handle == null) {
            return;
        }
        try {
            this.handle.cancel();
        } catch (final Exception ignored) {
            // expected - task may already be cancelled
        }
    }

    @Override
    public boolean isCancelled() {
        if (this.handle == null) {
            return true;
        }
        try {
            return this.handle.isCancelled();
        } catch (final Exception ignored) {
            return true;
        }
    }
}
