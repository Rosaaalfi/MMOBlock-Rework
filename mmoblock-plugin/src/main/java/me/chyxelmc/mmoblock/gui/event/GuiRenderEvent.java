package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import org.bukkit.inventory.ItemStack;
public final class GuiRenderEvent implements GuiEvent {
    private final GuiWindow window;
    private final int slot;
    private ItemStack item;
    public GuiRenderEvent(final GuiWindow window, final int slot, final ItemStack item) { this.window = window; this.slot = slot; this.item = item; }
    public GuiWindow window() { return this.window; }
    public int slot() { return this.slot; }
    public ItemStack item() { return this.item == null ? null : this.item.clone(); }
    public void setItem(final ItemStack item) { this.item = item == null ? null : item.clone(); }
}
