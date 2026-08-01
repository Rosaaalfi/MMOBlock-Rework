package me.chyxelmc.mmoblock.gui;

import java.util.function.Function;

import me.chyxelmc.mmoblock.gui.item.ItemProvider;
import me.chyxelmc.mmoblock.gui.item.SimpleItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Compatibility convenience; advanced code should use the item package abstractions. */
public final class GuiItem extends SimpleItem {
    private GuiItem(final ItemProvider provider, final GuiAction action) { super(provider, action); }
    public static GuiItem of(final ItemStack item) { return new GuiItem(ItemProvider.constant(item), GuiAction.NONE); }
    public static GuiItem of(final ItemStack item, final GuiAction action) { return new GuiItem(ItemProvider.constant(item), action); }
    public static GuiItem dynamic(final Function<Player, ItemStack> renderer, final GuiAction action) { return new GuiItem(renderer::apply, action); }
}
