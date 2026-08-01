package me.chyxelmc.mmoblock.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import me.chyxelmc.mmoblock.gui.inventory.VirtualInventory;
import me.chyxelmc.mmoblock.gui.item.GuiItem;
import me.chyxelmc.mmoblock.gui.item.ItemProvider;

/** Mutable, window-independent grid of composable slot elements. */
public class Gui {
    private final int width;
    private final int height;
    private final SlotElement[] elements;
    private final Set<Runnable> updateHandlers = new CopyOnWriteArraySet<>();
    private final Map<Object, ObserverBinding> observerBindings = Collections.synchronizedMap(new IdentityHashMap<>());
    private ItemProvider background;
    private boolean frozen;

    public Gui(final int width, final int height) {
        if (width < 1 || width > 9 || height < 1 || height > 6) {
            throw new IllegalArgumentException("GUI dimensions must be width 1..9 and height 1..6");
        }
        this.width = width;
        this.height = height;
        this.elements = new SlotElement[width * height];
    }

    public static Builder builder() { return new Builder(); }
    public static Gui empty(final int width, final int height) { return new Gui(width, height); }

    public int width() { return this.width; }
    public int height() { return this.height; }
    public int size() { return this.elements.length; }
    public boolean frozen() { return this.frozen; }

    public void setFrozen(final boolean frozen) { this.frozen = frozen; }

    public SlotElement getSlotElement(final int slot) {
        checkSlot(slot);
        return this.elements[slot];
    }

    public SlotElement getSlotElement(final int x, final int y) { return getSlotElement(index(x, y)); }

    public void setSlotElement(final int slot, final SlotElement element) {
        checkSlot(slot);
        if (element instanceof SlotElement.LinkedElement linked && reaches(linked.gui(), this, java.util.Collections.newSetFromMap(new IdentityHashMap<>()))) {
            throw new IllegalArgumentException("Nested GUI link would create a cycle");
        }
        detach(this.elements[slot]);
        this.elements[slot] = element;
        attach(element);
        notifyUpdate();
    }

    public void setSlotElement(final int x, final int y, final SlotElement element) { setSlotElement(index(x, y), element); }
    public void setItem(final int slot, final GuiItem item) { setSlotElement(slot, item == null ? null : new SlotElement.ItemElement(item)); }
    public void setItem(final int x, final int y, final GuiItem item) { setItem(index(x, y), item); }
    public void setInventorySlot(final int slot, final VirtualInventory inventory, final int inventorySlot) { setSlotElement(slot, new SlotElement.InventoryElement(inventory, inventorySlot)); }
    public void setNestedGui(final int x, final int y, final Gui nested) { fillRectangle(x, y, nested, true); }

    public ItemProvider background() { return this.background; }

    public void setBackground(final ItemProvider background) {
        this.background = background;
        notifyUpdate();
    }

    public ItemProvider renderedProvider(final int slot) {
        checkSlot(slot);
        final SlotElement element = this.elements[slot];
        return element == null ? this.background : element::render;
    }

    public void fill(final GuiItem item, final boolean replaceExisting) { fill(0, size(), item, replaceExisting); }

    public void fill(final int start, final int end, final GuiItem item, final boolean replaceExisting) {
        if (start < 0 || end > size() || start > end) throw new IndexOutOfBoundsException("Invalid fill range");
        for (int slot = start; slot < end; slot++) if (replaceExisting || this.elements[slot] == null) setItem(slot, item);
    }

    public void fillRow(final int row, final GuiItem item, final boolean replaceExisting) { fill(row * this.width, (row + 1) * this.width, item, replaceExisting); }

    public void fillColumn(final int column, final GuiItem item, final boolean replaceExisting) {
        if (column < 0 || column >= this.width) throw new IndexOutOfBoundsException("Column " + column);
        for (int y = 0; y < this.height; y++) if (replaceExisting || this.elements[index(column, y)] == null) setItem(column, y, item);
    }

    public void fillBorders(final GuiItem item, final boolean replaceExisting) {
        fillRow(0, item, replaceExisting);
        if (this.height > 1) fillRow(this.height - 1, item, replaceExisting);
        for (int y = 1; y < this.height - 1; y++) {
            if (replaceExisting || this.elements[index(0, y)] == null) setItem(0, y, item);
            if (this.width > 1 && (replaceExisting || this.elements[index(this.width - 1, y)] == null)) setItem(this.width - 1, y, item);
        }
    }

    public void fillRectangle(final int x, final int y, final Gui nested, final boolean replaceExisting) {
        Objects.requireNonNull(nested, "nested");
        if (x < 0 || y < 0 || x + nested.width > this.width || y + nested.height > this.height) throw new IndexOutOfBoundsException("Nested GUI outside parent");
        for (int nestedY = 0; nestedY < nested.height; nestedY++) for (int nestedX = 0; nestedX < nested.width; nestedX++) {
            final int target = index(x + nestedX, y + nestedY);
            if (replaceExisting || this.elements[target] == null) setSlotElement(target, new SlotElement.LinkedElement(nested, nested.index(nestedX, nestedY)));
        }
    }

    public void applyStructure(final Structure structure) { structure.apply(this); }
    public List<SlotElement> slotElements() { return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(this.elements))); }
    public void addUpdateHandler(final Runnable handler) { this.updateHandlers.add(handler); }
    public void removeUpdateHandler(final Runnable handler) { this.updateHandlers.remove(handler); }

    protected final void notifyUpdate() { this.updateHandlers.forEach(Runnable::run); }

    protected int index(final int x, final int y) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height) throw new IndexOutOfBoundsException("Coordinates " + x + "," + y);
        return y * this.width + x;
    }

    private void attach(final SlotElement element) {
        final Object source = source(element);
        if (source == null) return;
        synchronized (this.observerBindings) {
            final ObserverBinding existing = this.observerBindings.get(source);
            if (existing != null) {
                existing.references++;
                return;
            }
            final ObserverBinding binding = bind(source);
            this.observerBindings.put(source, binding);
        }
    }

    private void detach(final SlotElement element) {
        final Object source = source(element);
        if (source == null) return;
        synchronized (this.observerBindings) {
            final ObserverBinding binding = this.observerBindings.get(source);
            if (binding == null || --binding.references > 0) return;
            binding.detach.run();
            this.observerBindings.remove(source);
        }
    }

    private Object source(final SlotElement element) {
        if (element instanceof SlotElement.ItemElement item) return item.item();
        if (element instanceof SlotElement.InventoryElement inventory) return inventory.inventory();
        if (element instanceof SlotElement.LinkedElement linked) return linked.gui();
        return null;
    }

    private ObserverBinding bind(final Object source) {
        if (source instanceof GuiItem item) {
            final Runnable handler = this::notifyUpdate;
            item.addUpdateHandler(handler);
            return new ObserverBinding(() -> item.removeUpdateHandler(handler));
        }
        if (source instanceof VirtualInventory inventory) {
            final Consumer<me.chyxelmc.mmoblock.gui.inventory.InventoryUpdate> handler = update -> notifyUpdate();
            inventory.addPostUpdateHandler(handler);
            return new ObserverBinding(() -> inventory.removePostUpdateHandler(handler));
        }
        if (source instanceof Gui gui) {
            final Runnable handler = this::notifyUpdate;
            gui.addUpdateHandler(handler);
            return new ObserverBinding(() -> gui.removeUpdateHandler(handler));
        }
        throw new IllegalArgumentException("Unsupported observable source " + source.getClass().getName());
    }

    private void checkSlot(final int slot) { if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException("Slot " + slot + " outside GUI size " + size()); }

    private static boolean reaches(final Gui current, final Gui target, final Set<Gui> visited) {
        if (current == target) return true;
        if (!visited.add(current)) return false;
        for (final SlotElement element : current.elements) {
            if (element instanceof SlotElement.LinkedElement linked && reaches(linked.gui(), target, visited)) return true;
        }
        return false;
    }

    private static final class ObserverBinding {
        private final Runnable detach;
        private int references = 1;
        private ObserverBinding(final Runnable detach) { this.detach = detach; }
    }

    public static final class Builder {
        private int width = 9;
        private int height = 6;
        private Structure structure;
        private final List<java.util.function.Consumer<Gui>> modifiers = new ArrayList<>();

        public Builder dimensions(final int width, final int height) { this.width = width; this.height = height; return this; }
        public Builder size(final int size) { if (size < 9 || size > 54 || size % 9 != 0) throw new IllegalArgumentException("Chest size must be 9..54"); this.width = 9; this.height = size / 9; return this; }
        public Builder structure(final Structure structure) { this.structure = Objects.requireNonNull(structure); this.width = structure.width(); this.height = structure.height(); return this; }
        public Builder layout(final GuiLayout layout) { this.structure = layout.structure(); this.width = this.structure.width(); this.height = this.structure.height(); return this; }
        public Builder modify(final java.util.function.Consumer<Gui> modifier) { this.modifiers.add(modifier); return this; }
        public Builder item(final int slot, final me.chyxelmc.mmoblock.gui.GuiItem item) { return modify(gui -> gui.setItem(slot, item)); }
        public Gui build() { final Gui gui = new Gui(this.width, this.height); if (this.structure != null) gui.applyStructure(this.structure); this.modifiers.forEach(modifier -> modifier.accept(gui)); return gui; }
    }
}
