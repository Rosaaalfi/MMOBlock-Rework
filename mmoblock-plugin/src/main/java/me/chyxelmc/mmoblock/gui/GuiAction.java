package me.chyxelmc.mmoblock.gui;

@FunctionalInterface
public interface GuiAction {
    GuiAction NONE = click -> { };

    void handle(GuiClick click);
}
