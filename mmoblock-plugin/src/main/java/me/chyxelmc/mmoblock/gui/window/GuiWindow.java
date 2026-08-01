package me.chyxelmc.mmoblock.gui.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import me.chyxelmc.mmoblock.gui.Gui;
import me.chyxelmc.mmoblock.gui.GuiEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;
import me.chyxelmc.mmoblock.gui.i18n.LocalizedText;

/** Per-viewer window binding a logical GUI to a concrete Bukkit inventory. */
public final class GuiWindow {
    private final Player viewer;
    private final Gui gui;
    private final WindowType type;
    private final LocalizedText title;
    private final boolean closeable;
    private final boolean playerInventoryMutable;
    private final List<Runnable> openHandlers;
    private final List<Runnable> closeHandlers;
    private final List<Consumer<InventoryClickEvent>> outsideClickHandlers;
    private Inventory inventory;

    private GuiWindow(final Builder builder) {
        this.viewer = Objects.requireNonNull(builder.viewer, "viewer");
        this.gui = Objects.requireNonNull(builder.gui, "gui");
        this.type = builder.type;
        this.title = builder.title;
        this.closeable = builder.closeable;
        this.playerInventoryMutable = builder.playerInventoryMutable;
        this.openHandlers = List.copyOf(builder.openHandlers);
        this.closeHandlers = List.copyOf(builder.closeHandlers);
        this.outsideClickHandlers = List.copyOf(builder.outsideClickHandlers);
    }

    public static Builder builder() { return new Builder(); }
    public Player viewer() { return this.viewer; }
    public Gui gui() { return this.gui; }
    public WindowType type() { return this.type; }
    public String title(final GuiLocalizationContext context) { return Objects.requireNonNull(this.title.resolve(context), "window title"); }
    public boolean closeable() { return this.closeable; }
    public boolean playerInventoryMutable() { return this.playerInventoryMutable; }
    public Inventory inventory() { return this.inventory; }
    public void open(final GuiEngine engine) { engine.open(this); }
    public void close() { this.viewer.closeInventory(); }
    public void refresh(final GuiEngine engine) { engine.refresh(this); }
    public void changeTitle(final GuiEngine engine, final String title) { engine.reopenWithTitle(this, title); }
    public void addCloseHandler(final Runnable handler) { throw new UnsupportedOperationException("Handlers are immutable; configure them in the builder"); }

    public void attachInventory(final Inventory inventory) { this.inventory = inventory; }
    public void fireOpen() { this.openHandlers.forEach(Runnable::run); }
    public void fireClose() { this.closeHandlers.forEach(Runnable::run); }
    public void fireOutsideClick(final InventoryClickEvent event) { this.outsideClickHandlers.forEach(handler -> handler.accept(event)); }

    public static final class Builder {
        private Player viewer;
        private Gui gui;
        private WindowType type = WindowType.CHEST;
        private LocalizedText title = LocalizedText.literal("GUI");
        private boolean closeable = true;
        private boolean playerInventoryMutable;
        private final List<Runnable> openHandlers = new ArrayList<>();
        private final List<Runnable> closeHandlers = new ArrayList<>();
        private final List<Consumer<InventoryClickEvent>> outsideClickHandlers = new ArrayList<>();

        public Builder viewer(final Player viewer) { this.viewer = viewer; return this; }
        public Builder gui(final Gui gui) { this.gui = gui; return this; }
        public Builder type(final WindowType type) { this.type = Objects.requireNonNull(type); return this; }
        public Builder title(final String title) { this.title = LocalizedText.literal(title); return this; }
        public Builder title(final Function<Player, String> title) { this.title = context -> title.apply(context.viewer()); return this; }
        public Builder localizedTitle(final LocalizedText title) { this.title = Objects.requireNonNull(title); return this; }
        public Builder closeable(final boolean closeable) { this.closeable = closeable; return this; }
        public Builder playerInventoryMutable(final boolean mutable) { this.playerInventoryMutable = mutable; return this; }
        public Builder onOpen(final Runnable handler) { this.openHandlers.add(handler); return this; }
        public Builder onClose(final Runnable handler) { this.closeHandlers.add(handler); return this; }
        public Builder onOutsideClick(final Consumer<InventoryClickEvent> handler) { this.outsideClickHandlers.add(handler); return this; }
        public GuiWindow build() { return new GuiWindow(this); }
        public GuiWindow open(final GuiEngine engine) { final GuiWindow window = build(); window.open(engine); return window; }
    }
}
