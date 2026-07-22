package me.chyxelmc.mmoblock.platform.folia;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;

final class FoliaSchedulerTask implements SchedulerTask {

    private final ScheduledTask handle;

    FoliaSchedulerTask(final ScheduledTask handle) {
        this.handle = handle;
    }

    @Override
    public void cancel() {
        this.handle.cancel();
    }

    @Override
    public boolean isCancelled() {
        return this.handle.isCancelled();
    }
}
