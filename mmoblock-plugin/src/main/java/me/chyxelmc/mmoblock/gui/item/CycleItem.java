package me.chyxelmc.mmoblock.gui.item;

import java.util.List;
import java.util.Objects;

import me.chyxelmc.mmoblock.gui.GuiAction;
import me.chyxelmc.mmoblock.gui.GuiClick;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

public final class CycleItem extends AbstractGuiItem {
    private final List<ItemProvider> states;
    private final GuiAction action;
    private int state;

    public CycleItem(final List<ItemProvider> states, final GuiAction action) {
        this.states = List.copyOf(states);
        if (this.states.isEmpty()) throw new IllegalArgumentException("Cycle item requires states");
        this.action = Objects.requireNonNull(action, "action");
    }

    public int state() { return this.state; }
    public int stateCount() { return this.states.size(); }

    public void setState(final int state) {
        if (state < 0 || state >= this.states.size()) throw new IndexOutOfBoundsException("State " + state);
        this.state = state;
        notifyUpdate();
    }

    public void cycle() { setState((this.state + 1) % this.states.size()); }

    @Override public ItemStack render(final Player viewer) { final ItemStack item = this.states.get(this.state).provide(viewer); return item == null ? null : item.clone(); }
    @Override public ItemStack render(final GuiLocalizationContext context) { final ItemStack item = this.states.get(this.state).provide(context); return item == null ? null : item.clone(); }
    @Override public void handleClick(final GuiClick click) { this.action.handle(click); }
}
