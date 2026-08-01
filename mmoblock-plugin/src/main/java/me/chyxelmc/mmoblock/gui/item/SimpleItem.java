package me.chyxelmc.mmoblock.gui.item;

import java.util.Objects;

import me.chyxelmc.mmoblock.gui.GuiAction;
import me.chyxelmc.mmoblock.gui.GuiClick;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

public class SimpleItem extends AbstractGuiItem {
    private ItemProvider provider;
    private GuiAction action;

    public SimpleItem(final ItemProvider provider, final GuiAction action) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.action = Objects.requireNonNull(action, "action");
    }

    public SimpleItem(final ItemStack item) {
        this(ItemProvider.constant(item), GuiAction.NONE);
    }

    public SimpleItem(final ItemStack item, final GuiAction action) {
        this(ItemProvider.constant(item), action);
    }

    public void setProvider(final ItemProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        notifyUpdate();
    }

    public void setAction(final GuiAction action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public ItemStack render(final Player viewer) {
        final ItemStack item = this.provider.provide(viewer);
        return item == null ? null : item.clone();
    }

    @Override
    public ItemStack render(final GuiLocalizationContext context) {
        final ItemStack item = this.provider.provide(context);
        return item == null ? null : item.clone();
    }

    @Override
    public void handleClick(final GuiClick click) {
        this.action.handle(click);
    }
}
