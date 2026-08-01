package me.chyxelmc.mmoblock.gui.item;

import java.util.List;
import java.util.Objects;

import me.chyxelmc.mmoblock.gui.GuiAction;
import me.chyxelmc.mmoblock.gui.GuiClick;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

public final class AutoCycleItem extends AbstractGuiItem implements AutoCloseable {
    private final List<ItemProvider> states;
    private final GuiAction action;
    private final SchedulerTask task;
    private volatile int state;

    public AutoCycleItem(final List<ItemProvider> states, final long periodTicks, final GuiAction action, final Scheduler scheduler) {
        this.states = List.copyOf(states);
        if (this.states.isEmpty()) throw new IllegalArgumentException("Auto-cycle item requires states");
        if (periodTicks < 1) throw new IllegalArgumentException("Period must be positive");
        this.action = Objects.requireNonNull(action, "action");
        this.task = scheduler.runTimer(() -> { this.state = (this.state + 1) % this.states.size(); notifyUpdate(); }, periodTicks, periodTicks);
    }

    @Override public ItemStack render(final Player viewer) { final ItemStack item = this.states.get(this.state).provide(viewer); return item == null ? null : item.clone(); }
    @Override public ItemStack render(final GuiLocalizationContext context) { final ItemStack item = this.states.get(this.state).provide(context); return item == null ? null : item.clone(); }
    @Override public void handleClick(final GuiClick click) { this.action.handle(click); }
    @Override public void close() { this.task.cancel(); }
}
