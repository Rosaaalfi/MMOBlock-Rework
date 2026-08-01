package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.inventory.InventoryUpdate;
public record InventoryPostUpdateEvent(InventoryUpdate update) implements GuiEvent { }
