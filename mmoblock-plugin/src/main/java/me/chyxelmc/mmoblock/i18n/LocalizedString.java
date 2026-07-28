package me.chyxelmc.mmoblock.i18n;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves localized config values with dual-mode support.
 *
 * <h3>Mode 1: Plain String</h3>
 * <pre>{@code
 * name: "Example Block"
 * }</pre>
 *
 * <h3>Mode 2: I18n Key (with default fallback)</h3>
 * <pre>{@code
 * name:
 *   key: "block_config.example_name"
 *   default: "Example Block"
 * }</pre>
 *
 * <p>In Mode 2, the value is resolved through the {@link TranslationService}
 * using the player's locale. If the key is not found in any language file,
 * the {@code default} value is used as fallback.</p>
 */
public final class LocalizedString {

    private LocalizedString() {
    }

    /**
     * Resolve a localized string from a config value that may be either a plain
     * string or a map with {@code key} and {@code default} fields.
     *
     * @param translationService the translation service
     * @param player             the player (for locale detection, may be null)
     * @param value              the raw config value (String or Map/ConfigurationSection)
     * @param fallback           fallback if the value is null or empty
     * @param i18nKeyHint        hint for the translation key (used if value is a plain string that matches)
     * @return the resolved string
     */
    @NotNull
    public static String resolve(
            @NotNull final TranslationService translationService,
            @Nullable final Player player,
            @Nullable final Object value,
            @NotNull final String fallback,
            @NotNull final String i18nKeyHint
    ) {
        if (value == null) {
            // Try using the i18n key hint directly as a key lookup
            final String translated = translationService.translate(player, i18nKeyHint, fallback);
            if (!translated.equals(fallback) || translationService.hasKey(i18nKeyHint)) {
                return translated;
            }
            return fallback;
        }

        // Mode 2: Map with "key" and "default"
        if (value instanceof Map<?, ?> map) {
            final Object keyObj = map.get("key");
            final Object defaultObj = map.get("default");
            final String key = keyObj != null ? String.valueOf(keyObj) : i18nKeyHint;
            final String defaultStr = defaultObj != null ? String.valueOf(defaultObj) : fallback;
            return translationService.translate(player, key, defaultStr);
        }

        if (value instanceof ConfigurationSection section) {
            final String key = section.getString("key", i18nKeyHint);
            final String defaultStr = section.getString("default", fallback);
            return translationService.translate(player, key, defaultStr);
        }

        // Mode 1: Plain string
        final String str = String.valueOf(value);
        if (str.isBlank()) {
            return fallback;
        }
        return str;
    }

    /**
     * Check if a config value uses the i18n mode (is a map/section with a "key" field).
     */
    public static boolean isLocalized(@Nullable final Object value) {
        if (value == null) return false;
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("key");
        }
        return value instanceof ConfigurationSection section && section.contains("key");
    }
}
