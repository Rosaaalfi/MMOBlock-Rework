package me.chyxelmc.mmoblock.gui.i18n;

import java.util.Map;
import org.bukkit.entity.Player;

public record GuiLocalizationContext(Player viewer, String locale, GuiLocalization localization) {
    public String translate(final String key, final String fallback) { return this.localization.translate(this.viewer, key, fallback, Map.of()); }
    public String translate(final String key, final String fallback, final Map<String, String> placeholders) { return this.localization.translate(this.viewer, key, fallback, placeholders); }
}
