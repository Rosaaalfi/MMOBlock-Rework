package me.chyxelmc.mmoblock.gui.item;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import me.chyxelmc.mmoblock.gui.GuiAction;
import me.chyxelmc.mmoblock.gui.GuiClick;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

/** Loads a provider asynchronously and publishes it on the platform scheduler. */
public final class AsyncItem extends AbstractGuiItem {
    private volatile ItemProvider provider;
    private final GuiAction action;

    public AsyncItem(final ItemProvider placeholder, final Supplier<ItemProvider> loader, final GuiAction action, final Scheduler scheduler) {
        this.provider = Objects.requireNonNull(placeholder, "placeholder");
        this.action = Objects.requireNonNull(action, "action");
        CompletableFuture.supplyAsync(loader).thenAccept(loaded -> scheduler.run(() -> {
            this.provider = Objects.requireNonNull(loaded, "loaded provider");
            notifyUpdate();
        }));
    }

    @Override public ItemStack render(final Player viewer) { final ItemStack item = this.provider.provide(viewer); return item == null ? null : item.clone(); }
    @Override public ItemStack render(final GuiLocalizationContext context) { final ItemStack item = this.provider.provide(context); return item == null ? null : item.clone(); }
    @Override public void handleClick(final GuiClick click) { this.action.handle(click); }
}
