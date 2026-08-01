package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
public final class WindowOpenEvent extends AbstractCancellableGuiEvent {
    private final GuiWindow window;
    public WindowOpenEvent(final GuiWindow window) { this.window = window; }
    public GuiWindow window() { return this.window; }
}
