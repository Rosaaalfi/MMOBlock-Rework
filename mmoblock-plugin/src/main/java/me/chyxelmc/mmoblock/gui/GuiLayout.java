package me.chyxelmc.mmoblock.gui;

/** Backwards-compatible facade over the reusable {@link Structure} abstraction. */
public final class GuiLayout {
    private final Structure structure;
    private char contentMarker = 'x';

    private GuiLayout(final String... rows) { this.structure = new Structure(rows); }
    public static GuiLayout of(final String... rows) { return new GuiLayout(rows); }
    public GuiLayout bind(final char marker, final GuiItem item) { this.structure.item(marker, item); return this; }
    public GuiLayout content(final char marker) { this.contentMarker = marker; return this; }
    Structure structure() { return this.structure; }
    java.util.List<Integer> contentSlots() { return this.structure.slots(this.contentMarker); }
}
