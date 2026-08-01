package me.chyxelmc.mmoblock.gui.event;
import me.chyxelmc.mmoblock.gui.inventory.InventoryUpdateReason;
import me.chyxelmc.mmoblock.gui.inventory.VirtualInventory;
import org.bukkit.inventory.ItemStack;
public final class InventoryPreUpdateEvent extends AbstractCancellableGuiEvent {
    private final VirtualInventory inventory; private final int slot; private final ItemStack previousItem; private ItemStack newItem; private final InventoryUpdateReason reason;
    public InventoryPreUpdateEvent(final VirtualInventory inventory, final int slot, final ItemStack previousItem, final ItemStack newItem, final InventoryUpdateReason reason) { this.inventory = inventory; this.slot = slot; this.previousItem = clone(previousItem); this.newItem = clone(newItem); this.reason = reason; }
    public VirtualInventory inventory() { return this.inventory; } public int slot() { return this.slot; } public ItemStack previousItem() { return clone(this.previousItem); } public ItemStack newItem() { return clone(this.newItem); } public void setNewItem(final ItemStack item) { this.newItem = clone(item); } public InventoryUpdateReason reason() { return this.reason; }
    private static ItemStack clone(final ItemStack item) { return item == null ? null : item.clone(); }
}
