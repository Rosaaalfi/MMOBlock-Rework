package me.chyxelmc.mmoblock.gui.event;
public interface CancellableGuiEvent extends GuiEvent {
    boolean cancelled();
    void setCancelled(boolean cancelled);
}
