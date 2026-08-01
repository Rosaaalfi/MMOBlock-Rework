package me.chyxelmc.mmoblock.gui;

import java.util.ArrayList;
import java.util.List;

import me.chyxelmc.mmoblock.gui.item.GuiItem;

abstract class AbstractContentGui extends Gui {
    private final List<Integer> contentSlots;
    private List<GuiItem> content = List.of();
    private int offset;

    protected AbstractContentGui(final int width, final int height, final List<Integer> contentSlots) {
        super(width, height);
        this.contentSlots = List.copyOf(contentSlots);
        if (this.contentSlots.isEmpty()) throw new IllegalArgumentException("Content GUI needs at least one content slot");
        this.contentSlots.forEach(slot -> { if (slot < 0 || slot >= size()) throw new IndexOutOfBoundsException("Content slot " + slot); });
    }

    public final List<GuiItem> content() { return this.content; }
    public final int offset() { return this.offset; }
    public final List<Integer> contentSlots() { return this.contentSlots; }

    public final void setContent(final List<? extends GuiItem> content) {
        this.content = List.copyOf(content);
        this.offset = Math.min(this.offset, maximumOffset());
        renderContent();
    }

    protected final void setOffset(final int offset) {
        final int normalized = Math.max(0, Math.min(maximumOffset(), offset));
        if (normalized == this.offset) return;
        this.offset = normalized;
        renderContent();
    }

    protected abstract int maximumOffset();

    protected final void renderContent() {
        for (int index = 0; index < this.contentSlots.size(); index++) {
            final int contentIndex = this.offset + index;
            setItem(this.contentSlots.get(index), contentIndex < this.content.size() ? this.content.get(contentIndex) : null);
        }
    }

    protected static List<Integer> allSlots(final int size) {
        final List<Integer> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) slots.add(slot);
        return slots;
    }
}
