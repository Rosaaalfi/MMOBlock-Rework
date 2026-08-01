package me.chyxelmc.mmoblock.gui;

import java.util.List;

/** A GUI whose content is supplied by one nested GUI tab at a time. */
public final class TabGui extends Gui {
    private final List<Gui> tabs;
    private int currentTab;

    public TabGui(final int width, final int height, final List<Gui> tabs) {
        super(width, height);
        this.tabs = List.copyOf(tabs);
        if (this.tabs.isEmpty()) throw new IllegalArgumentException("Tab GUI requires tabs");
        if (this.tabs.stream().anyMatch(tab -> tab.width() != width || tab.height() != height)) throw new IllegalArgumentException("Every tab must match parent dimensions");
        applyTab();
    }

    public int currentTab() { return this.currentTab; }
    public int tabCount() { return this.tabs.size(); }

    public void setTab(final int tab) {
        if (tab < 0 || tab >= this.tabs.size()) throw new IndexOutOfBoundsException("Tab " + tab);
        if (this.currentTab == tab) return;
        this.currentTab = tab;
        applyTab();
    }

    private void applyTab() {
        final Gui selected = this.tabs.get(this.currentTab);
        for (int slot = 0; slot < size(); slot++) setSlotElement(slot, new SlotElement.LinkedElement(selected, slot));
    }
}
