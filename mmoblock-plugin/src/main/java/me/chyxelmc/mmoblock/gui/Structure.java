package me.chyxelmc.mmoblock.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Supplier;

/** Reusable character matrix that supplies slot elements on each application. */
public final class Structure {
    private final List<String> rows;
    private final Map<Character, Supplier<SlotElement>> ingredients = new LinkedHashMap<>();

    public Structure(final String... rows) {
        this.rows = List.of(rows);
        if (this.rows.isEmpty()) throw new IllegalArgumentException("Structure requires rows");
        final int width = this.rows.getFirst().length();
        if (width < 1 || width > 9 || this.rows.size() > 6 || this.rows.stream().anyMatch(row -> row.length() != width)) throw new IllegalArgumentException("Structure must be rectangular, at most 9x6");
    }

    public int width() { return this.rows.getFirst().length(); }
    public int height() { return this.rows.size(); }

    public Structure ingredient(final char marker, final Supplier<SlotElement> supplier) { this.ingredients.put(marker, Objects.requireNonNull(supplier)); return this; }
    public Structure item(final char marker, final me.chyxelmc.mmoblock.gui.item.GuiItem item) { return ingredient(marker, () -> new SlotElement.ItemElement(item)); }
    public Structure inventory(final char marker, final VirtualInventoryIngredient ingredient) { return ingredient(marker, ingredient); }

    public List<Integer> slots(final char marker) {
        final List<Integer> slots = new ArrayList<>();
        for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) if (this.rows.get(y).charAt(x) == marker) slots.add(y * width() + x);
        return List.copyOf(slots);
    }

    public void apply(final Gui gui) {
        if (gui.width() != width() || gui.height() != height()) throw new IllegalArgumentException("Structure dimensions do not match GUI");
        for (int y = 0; y < height(); y++) for (int x = 0; x < width(); x++) {
            final Supplier<SlotElement> supplier = this.ingredients.get(this.rows.get(y).charAt(x));
            if (supplier != null) gui.setSlotElement(x, y, supplier.get());
        }
    }

    @FunctionalInterface public interface VirtualInventoryIngredient extends Supplier<SlotElement> { }
}
