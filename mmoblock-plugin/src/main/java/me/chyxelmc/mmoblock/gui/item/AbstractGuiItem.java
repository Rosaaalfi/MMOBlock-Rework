package me.chyxelmc.mmoblock.gui.item;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractGuiItem implements GuiItem {
    private final Set<Runnable> updateHandlers = new CopyOnWriteArraySet<>();

    @Override
    public final void addUpdateHandler(final Runnable handler) {
        this.updateHandlers.add(handler);
    }

    @Override
    public final void removeUpdateHandler(final Runnable handler) {
        this.updateHandlers.remove(handler);
    }

    protected final void notifyUpdate() {
        this.updateHandlers.forEach(Runnable::run);
    }
}
