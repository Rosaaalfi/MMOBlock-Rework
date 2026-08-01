package me.chyxelmc.mmoblock.gui.event;
public abstract class AbstractCancellableGuiEvent implements CancellableGuiEvent {
    private boolean cancelled;
    @Override public final boolean cancelled() { return this.cancelled; }
    @Override public final void setCancelled(final boolean cancelled) { this.cancelled = cancelled; }
}
