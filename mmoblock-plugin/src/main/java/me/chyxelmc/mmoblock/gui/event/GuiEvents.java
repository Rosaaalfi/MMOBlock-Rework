package me.chyxelmc.mmoblock.gui.event;
public final class GuiEvents {
    private static final GuiEventBus GLOBAL = new GuiEventBus();
    public static GuiEventBus global() { return GLOBAL; }
    private GuiEvents() { }
}
