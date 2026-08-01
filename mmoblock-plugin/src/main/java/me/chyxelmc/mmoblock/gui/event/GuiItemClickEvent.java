package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.GuiClick;
import me.chyxelmc.mmoblock.gui.item.GuiItem;
public final class GuiItemClickEvent extends AbstractCancellableGuiEvent {
    private final GuiClick click;
    private final GuiItem item;
    public GuiItemClickEvent(final GuiClick click, final GuiItem item) { this.click = click; this.item = item; }
    public GuiClick click() { return this.click; }
    public GuiItem item() { return this.item; }
}
