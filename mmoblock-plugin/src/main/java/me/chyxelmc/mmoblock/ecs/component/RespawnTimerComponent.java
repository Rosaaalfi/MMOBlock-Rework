package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;

/**
 * Component that holds pending respawn task references for a block entity.
 * Pure data — the scheduling logic is in {@code BlockRespawnSystem}.
 */
public class RespawnTimerComponent {

    private SchedulerTask respawnTask;
    private SchedulerTask countdownTask;

    public SchedulerTask respawnTask() {
        return this.respawnTask;
    }

    public SchedulerTask countdownTask() {
        return this.countdownTask;
    }

    public void setTasks(final SchedulerTask respawnTask, final SchedulerTask countdownTask) {
        this.respawnTask = respawnTask;
        this.countdownTask = countdownTask;
    }

    public void clearTasks() {
        this.respawnTask = null;
        this.countdownTask = null;
    }
}
