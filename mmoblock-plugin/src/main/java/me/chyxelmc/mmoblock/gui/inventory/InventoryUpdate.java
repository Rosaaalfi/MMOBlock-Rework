package me.chyxelmc.mmoblock.gui.inventory;

import org.bukkit.inventory.ItemStack;

public record InventoryUpdate(
        VirtualInventory inventory,
        int slot,
        ItemStack previousItem,
        ItemStack newItem,
        InventoryUpdateReason reason
) {
}
