package me.chyxelmc.mmoblock.gui.item;

import me.chyxelmc.mmoblock.gui.GuiClick;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;

public interface GuiItem {
    ItemStack render(Player viewer);

    default ItemStack render(final GuiLocalizationContext context) {
        return render(context.viewer());
    }

    void handleClick(GuiClick click);

    void addUpdateHandler(Runnable handler);

    void removeUpdateHandler(Runnable handler);
}
