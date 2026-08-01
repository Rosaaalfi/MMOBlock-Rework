package me.chyxelmc.mmoblock.gui.animation;

import java.util.ArrayList;
import java.util.List;

import me.chyxelmc.mmoblock.gui.Gui;
import me.chyxelmc.mmoblock.gui.SlotElement;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;

/** Reveals a snapshot of GUI slots in an animation-defined order. */
public final class AnimationPlayer implements AutoCloseable {
    private final List<SchedulerTask> tasks = new ArrayList<>();

    public AnimationPlayer(final Gui gui, final GuiAnimation animation, final long tickDelay, final Scheduler scheduler, final Runnable completion) {
        if (tickDelay < 1) throw new IllegalArgumentException("Tick delay must be positive");
        final List<SlotElement> snapshot = gui.slotElements();
        final List<Integer> order = animation.slotOrder(gui.width(), gui.height());
        gui.setFrozen(true);
        for (final int slot : order) gui.setSlotElement(slot, null);
        for (int step = 0; step < order.size(); step++) {
            final int slot = order.get(step);
            this.tasks.add(scheduler.runLater(() -> gui.setSlotElement(slot, snapshot.get(slot)), tickDelay * (step + 1L)));
        }
        this.tasks.add(scheduler.runLater(() -> { gui.setFrozen(false); completion.run(); }, tickDelay * (order.size() + 1L)));
    }

    @Override public void close() { this.tasks.forEach(SchedulerTask::cancel); this.tasks.clear(); }
}
