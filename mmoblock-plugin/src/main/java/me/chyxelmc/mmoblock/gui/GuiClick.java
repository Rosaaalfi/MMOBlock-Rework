package me.chyxelmc.mmoblock.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

/** Immutable context passed to a GUI item click handler. */
public record GuiClick(
        Player player,
        GuiView view,
        int slot,
        ClickType clickType,
        InventoryAction inventoryAction,
        ItemStack cursor
) {
}
