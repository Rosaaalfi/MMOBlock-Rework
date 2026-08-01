package me.chyxelmc.mmoblock.gui.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.entity.Player;

/** GUI-local translations with configurable player locale and an MMOBlock translation bridge. */
public final class GuiLocalization {
    private final Map<String, Map<String, String>> languages = new ConcurrentHashMap<>();
    private Function<Player, String> localeProvider = player -> player.getLocale();
    private TranslationResolver resolver = this::resolveLocal;
    private String fallbackLocale = "en-us";

    public GuiLocalizationContext context(final Player player) { return new GuiLocalizationContext(player, normalize(this.localeProvider.apply(player)), this); }
    public void addLanguage(final String locale, final Map<String, String> translations) { this.languages.put(normalize(locale), Map.copyOf(translations)); }
    public void removeLanguage(final String locale) { this.languages.remove(normalize(locale)); }
    public void setLocaleProvider(final Function<Player, String> provider) { this.localeProvider = Objects.requireNonNull(provider); }
    public void setFallbackLocale(final String locale) { this.fallbackLocale = normalize(locale); }
    public void setResolver(final TranslationResolver resolver) { this.resolver = Objects.requireNonNull(resolver); }

    public String translate(final Player player, final String key, final String fallback, final Map<String, String> placeholders) {
        return this.resolver.resolve(player, key, fallback, placeholders);
    }

    private String resolveLocal(final Player player, final String key, final String fallback, final Map<String, String> placeholders) {
        final String locale = normalize(this.localeProvider.apply(player));
        String value = this.languages.getOrDefault(locale, Map.of()).get(key);
        if (value == null) value = this.languages.getOrDefault(this.fallbackLocale, Map.of()).getOrDefault(key, fallback);
        for (final Map.Entry<String, String> placeholder : placeholders.entrySet()) value = value.replace(placeholder.getKey(), placeholder.getValue());
        return value;
    }

    private static String normalize(final String locale) { return locale == null ? "en-us" : locale.toLowerCase(Locale.ROOT).replace('_', '-'); }

    @FunctionalInterface
    public interface TranslationResolver {
        String resolve(Player player, String key, String fallback, Map<String, String> placeholders);
    }
}
