package me.chyxelmc.mmoblock.gui.item;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.chyxelmc.mmoblock.gui.i18n.GuiLocalizationContext;
import me.chyxelmc.mmoblock.gui.i18n.LocalizedText;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Applies viewer-language display data to a clone, leaving the canonical ItemStack untouched. */
public final class LocalizedItemProvider implements ItemProvider {
    private final ItemStack base;
    private final LocalizedText displayName;
    private final List<LocalizedText> lore;

    public LocalizedItemProvider(final ItemStack base, final LocalizedText displayName, final List<LocalizedText> lore) {
        this.base = Objects.requireNonNull(base).clone();
        this.displayName = displayName;
        this.lore = List.copyOf(lore);
    }

    public static LocalizedItemProvider of(final ItemStack base, final String nameKey, final String fallbackName) {
        return new LocalizedItemProvider(base, LocalizedText.key(nameKey, fallbackName), List.of());
    }

    @Override public ItemStack provide(final Player viewer) { return this.base.clone(); }

    @Override
    public ItemStack provide(final GuiLocalizationContext context) {
        final ItemStack rendered = this.base.clone();
        final ItemMeta meta = rendered.getItemMeta();
        if (meta == null) return rendered;
        if (this.displayName != null) meta.setDisplayName(this.displayName.resolve(context));
        if (!this.lore.isEmpty()) meta.setLore(this.lore.stream().map(line -> line.resolve(context)).toList());
        rendered.setItemMeta(meta);
        return rendered;
    }
}
