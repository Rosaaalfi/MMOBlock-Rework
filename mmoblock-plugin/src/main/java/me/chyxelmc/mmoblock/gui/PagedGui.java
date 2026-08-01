package me.chyxelmc.mmoblock.gui;

import java.util.List;
import java.util.Objects;
import me.chyxelmc.mmoblock.gui.item.GuiItem;

public final class PagedGui extends AbstractContentGui {
    public PagedGui(final int width, final int height, final List<Integer> contentSlots) { super(width, height, contentSlots); }
    public static PagedGui full(final int width, final int height) { return new PagedGui(width, height, allSlots(width * height)); }
    public int page() { return offset() / contentSlots().size(); }
    public int pageCount() { return Math.max(1, (content().size() + contentSlots().size() - 1) / contentSlots().size()); }
    public boolean hasNextPage() { return page() + 1 < pageCount(); }
    public boolean hasPreviousPage() { return page() > 0; }
    public void nextPage() { if (hasNextPage()) setOffset(offset() + contentSlots().size()); }
    public void previousPage() { if (hasPreviousPage()) setOffset(offset() - contentSlots().size()); }
    public void setPage(final int page) { setOffset(Math.max(0, page) * contentSlots().size()); }
    @Override protected int maximumOffset() { return Math.max(0, (pageCount() - 1) * contentSlots().size()); }

    public static Builder pagedBuilder() { return new Builder(); }

    public static final class Builder {
        private Structure structure;
        private char contentMarker = 'x';
        private List<? extends GuiItem> content = List.of();
        public Builder structure(final Structure structure, final char contentMarker) { this.structure = Objects.requireNonNull(structure); this.contentMarker = contentMarker; return this; }
        public Builder content(final List<? extends GuiItem> content) { this.content = List.copyOf(content); return this; }
        public PagedGui build() {
            if (this.structure == null) throw new IllegalStateException("Structure is required");
            final PagedGui gui = new PagedGui(this.structure.width(), this.structure.height(), this.structure.slots(this.contentMarker));
            this.structure.apply(gui);
            gui.setContent(this.content);
            return gui;
        }
    }
}
