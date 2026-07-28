package me.chyxelmc.mmoblock.i18n;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;

/**
 * Internationalization (i18n) service for MMOBlock.
 *
 * <p>Loads language files from the {@code lang/} folder and resolves translated
 * messages based on the player's Minecraft client locale.</p>
 *
 * <h3>Locale Resolution</h3>
 * <ol>
 *   <li>Player's locale via {@link Player#getLocale()} (e.g. {@code "id_ID"})</li>
 *   <li>Normalized to lowercase with hyphen (e.g. {@code "id-id"})</li>
 *   <li>Look up key in the matching language file</li>
 *   <li>If not found, fall back to {@code en-us.yml}</li>
 *   <li>If still not found, return the {@code defaultMessage}</li>
 * </ol>
 */
public final class TranslationService {

    private static final String FALLBACK_LOCALE = "en-us";

    private final MMOBlock plugin;
    private final Map<String, Map<String, String>> languages = new ConcurrentHashMap<>();
    private String defaultLocale = FALLBACK_LOCALE;
    private boolean checkUserLanguage = true;

    public TranslationService(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    /**
     * Reload all language files from disk.
     */
    public int reload() {
        this.languages.clear();
        final File folder = new File(this.plugin.getDataFolder(), "lang");
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        saveDefaultLangResource("lang/lang.yml");
        saveDefaultLangResource("lang/en-us.yml");

        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        int count = 0;
        for (final File file : files) {
            if ("lang.yml".equalsIgnoreCase(file.getName())) {
                continue;
            }
            final String locale = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
            final Map<String, String> flat = loadYamlFlat(file);
            if (flat != null) {
                this.languages.put(locale, flat);
                count++;
            }
        }

        if (!this.languages.containsKey(FALLBACK_LOCALE)) {
            MMOBlockLogger.warning("integration.lang.fallback_missing",
                    "Fallback language 'en-us.yml' not found! Translations may be incomplete.");
        }
        loadLanguageSettings();
        return count;
    }

    private void saveDefaultLangResource(final String path) {
        final File target = new File(this.plugin.getDataFolder(), path);
        if (!target.exists()) {
            this.plugin.saveResource(path, false);
        }
    }

    private void loadLanguageSettings() {
        final File file = new File(this.plugin.getDataFolder(), "lang/lang.yml");
        final YamlConfiguration settings = YamlConfiguration.loadConfiguration(file);
        this.checkUserLanguage = settings.getBoolean("checkUserLanguage", true);
        final String configuredDefault = settings.getString("defaultLanguage", FALLBACK_LOCALE);
        final String normalized = normalizeLocale(configuredDefault);
        this.defaultLocale = this.languages.containsKey(normalized) ? normalized : FALLBACK_LOCALE;
        if (!this.languages.containsKey(this.defaultLocale) && !this.languages.isEmpty()) {
            this.defaultLocale = this.languages.keySet().stream().sorted().findFirst().orElse(FALLBACK_LOCALE);
        }
    }

    private Map<String, String> loadYamlFlat(final File file) {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (final IOException | InvalidConfigurationException e) {
            MMOBlockLogger.warning("integration.lang.load_failed",
                    "Failed to load language file '" + file.getName() + "': " + e.getMessage(),
                    java.util.Map.of("{file}", file.getName(), "{reason}", e.getMessage() != null ? e.getMessage() : "unknown"));
            return null;
        }
        // Use getValues(true) to deeply flatten the YAML tree into dot-notation keys.
        // Bukkit's YamlConfiguration returns ConfigurationSection objects (not Map)
        // for getValues(false), so manual instanceof Map recursion would silently skip
        // all nested keys.
        final Map<String, String> result = new HashMap<>();
        for (final Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            result.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    // ── Translation API ──

    public String translate(@Nullable final Player player, @NotNull final String key, @NotNull final String defaultMessage) {
        return translate(player, key, defaultMessage, Map.of());
    }

    public String translate(@Nullable final Player player, @NotNull final String key,
                            @NotNull final String defaultMessage,
                            @NotNull final Map<String, String> placeholders) {
        final String locale = resolveLocale(player);
        String message = lookup(key, locale);
        return getString(key, defaultMessage, placeholders, message);
    }

    public Component translateComponent(@Nullable final Player player, @NotNull final String key,
                                        @NotNull final String defaultMessage) {
        return TextColor.toComponent(translate(player, key, defaultMessage));
    }

    public Component translateComponent(@Nullable final Player player, @NotNull final String key,
                                        @NotNull final String defaultMessage,
                                        @NotNull final Map<String, String> placeholders) {
        return TextColor.toComponent(translate(player, key, defaultMessage, placeholders));
    }

    public String translateConsole(@NotNull final String key, @NotNull final String defaultMessage) {
        return translateConsole(key, defaultMessage, Map.of());
    }

    public String translateConsole(@NotNull final String key, @NotNull final String defaultMessage,
                                   @NotNull final Map<String, String> placeholders) {
        String msg = lookup(key, this.defaultLocale);
        return getString(key, defaultMessage, placeholders, msg);
    }

    @NonNull
    private String getString(@NotNull String key, @NotNull String defaultMessage, @NotNull Map<String, String> placeholders, String msg) {
        if (msg == null) msg = lookup(key, FALLBACK_LOCALE);
        if (msg == null) msg = defaultMessage;
        if (!placeholders.isEmpty()) {
            for (final Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace(e.getKey(), e.getValue());
            }
        }
        return msg;
    }

    public Component translateComponentConsole(@NotNull final String key, @NotNull final String defaultMessage) {
        return TextColor.toComponent(translateConsole(key, defaultMessage));
    }

    public Component translateComponentConsole(@NotNull final String key, @NotNull final String defaultMessage,
                                               @NotNull final Map<String, String> placeholders) {
        return TextColor.toComponent(translateConsole(key, defaultMessage, placeholders));
    }

    /**
     * Resolve a localized block/display name from config using dual-mode support.
     */
    @NotNull
    public String resolveLocalizedName(@Nullable final Player player,
                                       @Nullable final Object nameConfig,
                                       @NotNull final String fallback,
                                       @NotNull final String i18nKey) {
        return LocalizedString.resolve(this, player, nameConfig, fallback, i18nKey);
    }

    public boolean hasKey(@NotNull final String key) {
        for (final Map<String, String> lang : this.languages.values()) {
            if (lang.containsKey(key)) return true;
        }
        return false;
    }

    @NotNull
    public Set<String> getLoadedLocales() {
        return Collections.unmodifiableSet(this.languages.keySet());
    }

    @NotNull
    public String defaultLocale() {
        return this.defaultLocale;
    }

    @Nullable
    private String lookup(@NotNull final String key, @NotNull final String locale) {
        final Map<String, String> lang = this.languages.get(locale);
        return lang == null ? null : lang.get(key);
    }

    @NotNull
    private String resolveLocale(@Nullable final Player player) {
        if (player == null) return this.defaultLocale;
        if (!this.checkUserLanguage) return this.defaultLocale;
        try {
            final String raw = player.getLocale();
            if (raw == null || raw.isBlank()) return this.defaultLocale;
            final String normalized = normalizeLocale(raw);
            if (this.languages.containsKey(normalized)) return normalized;
            final String langPart = normalized.contains("-") ? normalized.substring(0, normalized.indexOf('-')) : normalized;
            if (this.languages.containsKey(langPart)) return langPart;
        } catch (final Exception ignored) {}
        return this.defaultLocale;
    }

    @NotNull
    private String normalizeLocale(@Nullable final String locale) {
        if (locale == null || locale.isBlank()) {
            return FALLBACK_LOCALE;
        }
        return locale.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
