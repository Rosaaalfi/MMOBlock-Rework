package me.chyxelmc.mmoblock.gui.platform;

import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import me.chyxelmc.mmoblock.gui.window.WindowType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Stable boundary for all server-version-dependent inventory access. */
public interface GuiPlatformAdapter {
    boolean supports(WindowType type);
    Inventory createInventory(GuiWindow window, String localizedTitle);
    void openInventory(Player player, Inventory inventory, String localizedTitle);
    boolean updateTitle(Player player, String title);
    void sendContent(Player player);
    void sendSlot(Player player, int rawSlot, ItemStack item);
    int activeContainerId(Player player);
    boolean isViewing(Player player, Inventory inventory);
    Inventory topInventory(InventoryView view);
    ItemStack itemFromView(InventoryView view, int rawSlot);
}
