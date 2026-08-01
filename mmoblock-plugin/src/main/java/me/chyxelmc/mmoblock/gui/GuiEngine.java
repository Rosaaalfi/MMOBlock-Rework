package me.chyxelmc.mmoblock.gui;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.chyxelmc.mmoblock.gui.inventory.InventoryTransactions;
import me.chyxelmc.mmoblock.gui.inventory.InventoryUpdateReason;
import me.chyxelmc.mmoblock.gui.item.GuiItem;
import me.chyxelmc.mmoblock.gui.event.GuiEventBus;
import me.chyxelmc.mmoblock.gui.event.GuiEvents;
import me.chyxelmc.mmoblock.gui.event.GuiItemClickEvent;
import me.chyxelmc.mmoblock.gui.event.GuiRenderEvent;
import me.chyxelmc.mmoblock.gui.event.WindowCloseEvent;
import me.chyxelmc.mmoblock.gui.event.WindowOpenEvent;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalization;
import me.chyxelmc.mmoblock.gui.platform.GuiPlatformAdapter;
import me.chyxelmc.mmoblock.gui.platform.NmsGuiPlatformAdapter;
import me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/** Central event bridge and active-window registry; GUI state lives in dedicated abstractions. */
public final class GuiEngine implements Listener, AutoCloseable {
    private final Scheduler scheduler;
    private final GuiPlatformAdapter platform;
    private final GuiEventBus eventBus;
    private final GuiLocalization localization;
    private final Map<UUID, GuiWindow> windows = new ConcurrentHashMap<>();
    private final Map<GuiWindow, Runnable> observers = java.util.Collections.synchronizedMap(new IdentityHashMap<>());

    public GuiEngine(final Plugin plugin, final Scheduler scheduler, final GuiInventoryAccess inventoryAccess) {
        Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.platform = new NmsGuiPlatformAdapter(inventoryAccess);
        this.eventBus = GuiEvents.global();
        this.localization = new GuiLocalization();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public GuiView open(final Player player, final Gui gui) {
        final GuiWindow window = GuiWindow.builder().viewer(player).gui(gui).build();
        open(window);
        return new GuiView(this, window);
    }

    public void open(final GuiWindow window) {
        Objects.requireNonNull(window, "window");
        if (this.eventBus.publish(new WindowOpenEvent(window)).cancelled()) return;
        final var context = this.localization.context(window.viewer());
        final Inventory inventory = this.platform.createInventory(window, window.title(context));
        window.attachInventory(inventory);
        final GuiWindow previous = this.windows.put(window.viewer().getUniqueId(), window);
        if (previous != null) detach(previous);
        final Runnable observer = () -> this.scheduler.runForEntity(window.viewer(), () -> {
            if (this.windows.get(window.viewer().getUniqueId()) == window) refresh(window);
        }, () -> this.windows.remove(window.viewer().getUniqueId(), window));
        this.observers.put(window, observer);
        window.gui().addUpdateHandler(observer);
        refresh(window);
        this.platform.openInventory(window.viewer(), inventory, window.title(context));
        window.fireOpen();
    }

    public GuiView view(final Player player) {
        final GuiWindow window = this.windows.get(player.getUniqueId());
        return window == null ? null : new GuiView(this, window);
    }

    public GuiWindow window(final Player player) { return this.windows.get(player.getUniqueId()); }
    public boolean supports(final me.chyxelmc.mmoblock.gui.window.WindowType type) { return this.platform.supports(type); }
    public GuiPlatformAdapter platform() { return this.platform; }
    public GuiEventBus events() { return this.eventBus; }
    public GuiLocalization localization() { return this.localization; }

    public void refresh(final GuiWindow window) {
        final Inventory inventory = window.inventory();
        if (inventory == null) return;
        final var context = this.localization.context(window.viewer());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final SlotElement element = resolve(window.gui().getSlotElement(slot));
            final var rendered = element == null ? renderBackground(window, context) : cloneItem(element.render(context));
            final GuiRenderEvent renderEvent = this.eventBus.publish(new GuiRenderEvent(window, slot, rendered));
            inventory.setItem(slot, renderEvent.item());
        }
        if (this.platform.isViewing(window.viewer(), inventory)) this.platform.sendContent(window.viewer());
    }

    public void reopenWithTitle(final GuiWindow window, final String title) {
        if (this.windows.get(window.viewer().getUniqueId()) != window) return;
        if (this.platform.updateTitle(window.viewer(), title)) return;
        final GuiWindow replacement = GuiWindow.builder().viewer(window.viewer()).gui(window.gui()).type(window.type()).title(title)
                .closeable(window.closeable()).playerInventoryMutable(window.playerInventoryMutable()).build();
        open(replacement);
    }

    private org.bukkit.inventory.ItemStack renderBackground(final GuiWindow window, final me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext context) {
        final var provider = window.gui().background();
        return provider == null ? null : cloneItem(provider.provide(context));
    }

    private SlotElement resolve(final SlotElement source) {
        SlotElement element = source;
        final java.util.Set<SlotElement.LinkedElement> visited = new java.util.HashSet<>();
        while (element instanceof SlotElement.LinkedElement linked) {
            if (!visited.add(linked)) throw new IllegalStateException("Cyclic nested GUI link");
            element = linked.gui().getSlotElement(linked.slot());
        }
        return element;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final GuiWindow window = this.windows.get(player.getUniqueId());
        if (window == null || this.platform.topInventory(event.getView()) != window.inventory()) return;
        final boolean top = event.getRawSlot() >= 0 && event.getRawSlot() < window.inventory().getSize();
        if (!top) {
            if (event.getRawSlot() < 0) window.fireOutsideClick(event);
            if (!window.playerInventoryMutable() || event.isShiftClick()) event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (window.gui().frozen()) return;
        final SlotElement element = resolve(window.gui().getSlotElement(event.getRawSlot()));
        final GuiView view = new GuiView(this, window);
        if (element instanceof SlotElement.ItemElement item) {
            final GuiClick click = new GuiClick(player, view, event.getRawSlot(), event.getClick(), event.getAction(), event.getCursor());
            if (!this.eventBus.publish(new GuiItemClickEvent(click, item.item())).cancelled()) item.item().handleClick(click);
        } else if (element instanceof SlotElement.InventoryElement inventoryElement) {
            final var result = InventoryTransactions.apply(inventoryElement.inventory(), inventoryElement.slot(), event.getCursor(), event.getAction(), new InventoryUpdateReason.PlayerAction(player, event));
            if (result.accepted()) event.setCursor(result.cursor());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        final GuiWindow window = this.windows.get(player.getUniqueId());
        if (window != null && this.platform.topInventory(event.getView()) == window.inventory() && event.getRawSlots().stream().anyMatch(slot -> slot < window.inventory().getSize())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(final InventoryCloseEvent event) {
        final GuiWindow window = this.windows.get(event.getPlayer().getUniqueId());
        if (window == null || event.getInventory() != window.inventory()) return;
        final WindowCloseEvent closeEvent = this.eventBus.publish(new WindowCloseEvent(window));
        if ((!window.closeable() || closeEvent.cancelled()) && event.getPlayer() instanceof Player player && player.isOnline()) {
            this.scheduler.runForEntityLater(player, () -> {
                if (this.windows.get(player.getUniqueId()) == window) this.platform.openInventory(player, window.inventory(), window.title(this.localization.context(player)));
            }, () -> detachAndRemove(window), 1L);
            return;
        }
        detachAndRemove(window);
        window.fireClose();
    }

    @EventHandler public void onQuit(final PlayerQuitEvent event) { final GuiWindow window = this.windows.remove(event.getPlayer().getUniqueId()); if (window != null) detach(window); }

    private void detachAndRemove(final GuiWindow window) { this.windows.remove(window.viewer().getUniqueId(), window); detach(window); }
    private void detach(final GuiWindow window) { final Runnable observer = this.observers.remove(window); if (observer != null) window.gui().removeUpdateHandler(observer); }
    private static org.bukkit.inventory.ItemStack cloneItem(final org.bukkit.inventory.ItemStack item) { return item == null ? null : item.clone(); }

    @Override public void close() { for (final GuiWindow window : this.windows.values()) { detach(window); if (window.viewer().isOnline()) window.viewer().closeInventory(); } this.windows.clear(); this.observers.clear(); }
}
