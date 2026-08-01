package me.chyxelmc.mmoblock.gui;

import java.util.List;
import java.util.Objects;
import me.chyxelmc.mmoblock.gui.item.GuiItem;

public final class ScrollGui extends AbstractContentGui {
    public ScrollGui(final int width, final int height, final List<Integer> contentSlots) { super(width, height, contentSlots); }
    public static ScrollGui full(final int width, final int height) { return new ScrollGui(width, height, allSlots(width * height)); }
    public boolean canScrollForward() { return offset() < maximumOffset(); }
    public boolean canScrollBackward() { return offset() > 0; }
    public void scroll(final int amount) { setOffset(offset() + amount); }
    @Override protected int maximumOffset() { return Math.max(0, content().size() - contentSlots().size()); }

    public static Builder scrollBuilder() { return new Builder(); }

    public static final class Builder {
        private Structure structure;
        private char contentMarker = 'x';
        private List<? extends GuiItem> content = List.of();
        public Builder structure(final Structure structure, final char contentMarker) { this.structure = Objects.requireNonNull(structure); this.contentMarker = contentMarker; return this; }
        public Builder content(final List<? extends GuiItem> content) { this.content = List.copyOf(content); return this; }
        public ScrollGui build() {
            if (this.structure == null) throw new IllegalStateException("Structure is required");
            final ScrollGui gui = new ScrollGui(this.structure.width(), this.structure.height(), this.structure.slots(this.contentMarker));
            this.structure.apply(gui);
            gui.setContent(this.content);
            return gui;
        }
    }
}
