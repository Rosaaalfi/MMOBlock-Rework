package me.chyxelmc.mmoblock.gui.inventory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import me.chyxelmc.mmoblock.gui.event.GuiEventBus;
import me.chyxelmc.mmoblock.gui.event.GuiEvents;
import me.chyxelmc.mmoblock.gui.event.InventoryPostUpdateEvent;
import me.chyxelmc.mmoblock.gui.event.InventoryPreUpdateEvent;

import org.bukkit.inventory.ItemStack;

/** Inventory state independent from Bukkit windows, suitable for sharing between viewers. */
public final class VirtualInventory {
    private final ItemStack[] items;
    private final List<Predicate<InventoryUpdate>> preUpdateHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<InventoryUpdate>> postUpdateHandlers = new CopyOnWriteArrayList<>();
    private int maxStackSize = 64;
    private final GuiEventBus eventBus;

    public VirtualInventory(final int size) {
        this(size, GuiEvents.global());
    }

    public VirtualInventory(final int size, final GuiEventBus eventBus) {
        if (size < 1) throw new IllegalArgumentException("Inventory size must be positive");
        this.items = new ItemStack[size];
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public int size() { return this.items.length; }

    public synchronized ItemStack get(final int slot) {
        checkSlot(slot);
        return cloneItem(this.items[slot]);
    }

    public synchronized ItemStack[] contents() {
        return Arrays.stream(this.items).map(VirtualInventory::cloneItem).toArray(ItemStack[]::new);
    }

    public synchronized boolean set(final int slot, final ItemStack item, final InventoryUpdateReason reason) {
        checkSlot(slot);
        Objects.requireNonNull(reason, "reason");
        ItemStack normalized = normalize(item);
        final InventoryPreUpdateEvent preEvent = this.eventBus.publish(new InventoryPreUpdateEvent(this, slot, cloneItem(this.items[slot]), cloneItem(normalized), reason));
        if (preEvent.cancelled()) return false;
        normalized = normalize(preEvent.newItem());
        final InventoryUpdate update = new InventoryUpdate(this, slot, cloneItem(this.items[slot]), cloneItem(normalized), reason);
        if (this.preUpdateHandlers.stream().anyMatch(handler -> !handler.test(update))) return false;
        this.items[slot] = cloneItem(normalized);
        this.postUpdateHandlers.forEach(handler -> handler.accept(update));
        this.eventBus.publish(new InventoryPostUpdateEvent(update));
        return true;
    }

    public void addPreUpdateHandler(final Predicate<InventoryUpdate> handler) { this.preUpdateHandlers.add(handler); }
    public void removePreUpdateHandler(final Predicate<InventoryUpdate> handler) { this.preUpdateHandlers.remove(handler); }
    public void addPostUpdateHandler(final Consumer<InventoryUpdate> handler) { this.postUpdateHandlers.add(handler); }
    public void removePostUpdateHandler(final Consumer<InventoryUpdate> handler) { this.postUpdateHandlers.remove(handler); }

    public int maxStackSize() { return this.maxStackSize; }

    public void setMaxStackSize(final int maxStackSize) {
        if (maxStackSize < 1 || maxStackSize > 127) throw new IllegalArgumentException("Max stack size must be 1..127");
        this.maxStackSize = maxStackSize;
    }

    private ItemStack normalize(final ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        final ItemStack copy = item.clone();
        copy.setAmount(Math.min(copy.getAmount(), Math.min(copy.getMaxStackSize(), this.maxStackSize)));
        return copy;
    }

    private void checkSlot(final int slot) {
        if (slot < 0 || slot >= this.items.length) throw new IndexOutOfBoundsException("Slot " + slot + " outside inventory size " + this.items.length);
    }

    private static ItemStack cloneItem(final ItemStack item) { return item == null ? null : item.clone(); }
}
