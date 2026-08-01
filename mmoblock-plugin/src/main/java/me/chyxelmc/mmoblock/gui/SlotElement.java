package me.chyxelmc.mmoblock.gui;

import java.util.HashSet;
import java.util.Set;

import me.chyxelmc.mmoblock.gui.inventory.VirtualInventory;
import me.chyxelmc.mmoblock.gui.item.GuiItem;
import me.chyxelmc.mmoblock.gui.item.ItemProvider;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

/** A GUI slot can hold an item, reference virtual inventory state, or link to a nested GUI. */
public sealed interface SlotElement permits SlotElement.ItemElement, SlotElement.InventoryElement, SlotElement.LinkedElement {
    ItemStack render(Player viewer);
    default ItemStack render(final GuiLocalizationContext context) { return render(context.viewer()); }

    record ItemElement(GuiItem item) implements SlotElement {
        @Override public ItemStack render(final Player viewer) { return this.item.render(viewer); }
        @Override public ItemStack render(final GuiLocalizationContext context) { return this.item.render(context); }
    }

    record InventoryElement(VirtualInventory inventory, int slot, ItemProvider background) implements SlotElement {
        public InventoryElement(final VirtualInventory inventory, final int slot) { this(inventory, slot, null); }

        public InventoryElement {
            if (slot < 0 || slot >= inventory.size()) throw new IndexOutOfBoundsException("Virtual inventory slot " + slot);
        }

        @Override
        public ItemStack render(final Player viewer) {
            final ItemStack item = this.inventory.get(this.slot);
            return item != null || this.background == null ? item : this.background.provide(viewer);
        }
        @Override public ItemStack render(final GuiLocalizationContext context) { final ItemStack item = this.inventory.get(this.slot); return item != null || this.background == null ? item : this.background.provide(context); }
    }

    record LinkedElement(Gui gui, int slot) implements SlotElement {
        @Override
        public ItemStack render(final Player viewer) {
            SlotElement element = this;
            final Set<LinkedElement> visited = new HashSet<>();
            while (element instanceof LinkedElement link) {
                if (!visited.add(link)) throw new IllegalStateException("Cyclic nested GUI link");
                element = link.gui().getSlotElement(link.slot());
                if (element == null) return null;
            }
            return element.render(viewer);
        }

        @Override
        public ItemStack render(final GuiLocalizationContext context) {
            SlotElement element = this;
            final Set<LinkedElement> visited = new HashSet<>();
            while (element instanceof LinkedElement link) {
                if (!visited.add(link)) throw new IllegalStateException("Cyclic nested GUI link");
                element = link.gui().getSlotElement(link.slot());
                if (element == null) return null;
            }
            return element.render(context);
        }
    }
}
