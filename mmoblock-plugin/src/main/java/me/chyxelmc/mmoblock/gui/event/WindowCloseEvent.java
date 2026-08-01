package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
public final class WindowCloseEvent extends AbstractCancellableGuiEvent {
    private final GuiWindow window;
    public WindowCloseEvent(final GuiWindow window) { this.window = window; }
    public GuiWindow window() { return this.window; }
}
