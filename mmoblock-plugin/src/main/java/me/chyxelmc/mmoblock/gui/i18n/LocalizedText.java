package me.chyxelmc.mmoblock.gui.i18n;

import java.util.Map;
import java.util.Objects;

@FunctionalInterface
public interface LocalizedText {
    String resolve(GuiLocalizationContext context);
    static LocalizedText literal(final String value) { return context -> value; }
    static LocalizedText key(final String key, final String fallback) { return key(key, fallback, Map.of()); }
    static LocalizedText key(final String key, final String fallback, final Map<String, String> placeholders) {
        Objects.requireNonNull(key); Objects.requireNonNull(fallback); final Map<String, String> values = Map.copyOf(placeholders);
        return context -> context.translate(key, fallback, values);
    }
}
