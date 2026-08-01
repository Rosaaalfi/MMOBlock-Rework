package me.chyxelmc.mmoblock.gui;

import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Compatibility view facade over the layered {@link GuiWindow}. */
public final class GuiView {
    private final GuiEngine engine;
    private final GuiWindow window;

    GuiView(final GuiEngine engine, final GuiWindow window) { this.engine = engine; this.window = window; }
    public Player player() { return this.window.viewer(); }
    public Inventory inventory() { return this.window.inventory(); }
    public Gui gui() { return this.window.gui(); }
    public GuiWindow window() { return this.window; }
    public int offset() { return this.window.gui() instanceof AbstractContentGui content ? content.offset() : 0; }
    public int page() { return this.window.gui() instanceof PagedGui paged ? paged.page() : 0; }
    public void refresh() { this.engine.refresh(this.window); }
    public void nextPage() { if (gui() instanceof PagedGui paged) paged.nextPage(); }
    public void previousPage() { if (gui() instanceof PagedGui paged) paged.previousPage(); }
    public void scroll(final int amount) { if (gui() instanceof ScrollGui scroll) scroll.scroll(amount); }
    public void close() { this.window.close(); }
}
