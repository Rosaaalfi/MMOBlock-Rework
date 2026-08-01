package me.chyxelmc.mmoblock.gui.platform;

import java.util.Objects;

import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import me.chyxelmc.mmoblock.gui.window.WindowType;
import me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Plugin-side facade over the inventory-access implementation owned by the active NMS adapter. */
public final class NmsGuiPlatformAdapter implements GuiPlatformAdapter {
    private final GuiInventoryAccess access;

    public NmsGuiPlatformAdapter(final GuiInventoryAccess access) { this.access = Objects.requireNonNull(access, "access"); }

    @Override public boolean supports(final WindowType type) {
        if (type == WindowType.CHEST) return true;
        try { InventoryType.valueOf(type.bukkitName()); return true; }
        catch (final IllegalArgumentException exception) { return false; }
    }

    @Override public Inventory createInventory(final GuiWindow window, final String title) {
        if (window.type() == WindowType.CHEST) {
            if (window.gui().width() != 9 || window.gui().size() % 9 != 0) throw new IllegalArgumentException("Chest windows require a 9-wide GUI");
            return Bukkit.createInventory(null, window.gui().size(), title);
        }
        if (!supports(window.type())) throw new UnsupportedOperationException("Window type " + window.type() + " is unavailable on this server");
        final Inventory inventory = Bukkit.createInventory(null, InventoryType.valueOf(window.type().bukkitName()), title);
        if (window.gui().size() != inventory.getSize()) throw new IllegalArgumentException("Window " + window.type() + " exposes " + inventory.getSize() + " slots, GUI has " + window.gui().size());
        return inventory;
    }

    @Override public void openInventory(final Player player, final Inventory inventory, final String title) {
        if (!this.access.openCustomInventory(player, inventory, title)) throw new IllegalStateException("NMS rejected custom inventory open for " + player.getName());
    }
    @Override public boolean updateTitle(final Player player, final String title) { return this.access.updateTitle(player, title); }
    @Override public void sendContent(final Player player) { this.access.sendContent(player); }
    @Override public void sendSlot(final Player player, final int rawSlot, final ItemStack item) { this.access.sendSlot(player, rawSlot, item); }
    @Override public int activeContainerId(final Player player) { return this.access.activeContainerId(player); }
    @Override public boolean isViewing(final Player player, final Inventory inventory) { return this.access.isViewing(player, inventory); }
    @Override public Inventory topInventory(final InventoryView view) { return this.access.topInventory(view); }
    @Override public ItemStack itemFromView(final InventoryView view, final int rawSlot) { return this.access.itemFromView(view, rawSlot); }
}
