package me.chyxelmc.mmoblock.gui.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

@FunctionalInterface
public interface ItemProvider {
    ItemStack provide(Player viewer);

    default ItemStack provide(final GuiLocalizationContext context) {
        return provide(context.viewer());
    }

    static ItemProvider constant(final ItemStack item) {
        return viewer -> item == null ? null : item.clone();
    }
}
