package me.chyxelmc.mmoblock.nms.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Version-specific bridge to the active Minecraft container menu and its packets. */
public interface GuiInventoryAccess {
    boolean openCustomInventory(Player player, Inventory inventory, String title);
    boolean updateTitle(Player player, String title);
    void sendContent(Player player);
    void sendSlot(Player player, int rawSlot, ItemStack item);
    int activeContainerId(Player player);
    boolean isViewing(Player player, Inventory inventory);
    Inventory topInventory(InventoryView view);
    ItemStack itemFromView(InventoryView view, int rawSlot);
}
