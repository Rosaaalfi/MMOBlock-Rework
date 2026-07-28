package me.chyxelmc.mmoblock.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom colored logger for MMOBlock based on {@link ColorLogger}.
 * <p>
 * Provides colored console output with configurable prefix, log level filtering,
 * and message deduplication. Designed as a singleton initialized during plugin startup.
 * </p>
 * <p>
 * Supports i18n via {@link #setTranslator(Translator)}. When a translator is set,
 * overloaded {@code info(String key, String defaultMessage)} and similar methods
 * will translate the key through the translator using the configured console locale.
 * </p>
 * <p>
 * Usage:
 * <pre>{@code
 *   // Initialize in onEnable():
 *   MMOBlockLogger.init(this);
 *   MMOBlockLogger.setTranslator((key, def, placeholders) -> translationService.translateConsole(key, def, placeholders));
 *
 *   // Plain text:
 *   MMOBlockLogger.info("Something happened");
 *
 *   // I18n-aware:
 *   MMOBlockLogger.info("commands.reload.success", "&aReloaded successfully.");
 *   MMOBlockLogger.info("commands.reload.success", "&aReloaded successfully.", Map.of("{target}", "config"));
 * }</pre>
 * </p>
 */
public final class MMOBlockLogger {

    public enum Level {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        SEVERE
    }

    /**
     * Functional interface for translating log messages. Set via {@link #setTranslator(Translator)}.
     */
    @FunctionalInterface
    public interface Translator {
        /**
         * Translate a key into a localized message for the console's default locale.
         *
         * @param key            the translation key
         * @param defaultMessage the fallback message if the key is not found
         * @param placeholders   placeholder replacements (may be empty)
         * @return the translated message
         */
        String translate(String key, String defaultMessage, Map<String, String> placeholders);
    }

    private static JavaPlugin plugin;
    private static boolean debug;
    private static String parsedPrefix;
    private static Translator translator;
    private static final Set<String> loggedMessages = new HashSet<>();
    private static final ColorLogger colorLogger = new ColorLogger();
    private static boolean initialized;

    private MMOBlockLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Initialize the logger. Must be called early in {@link JavaPlugin#onEnable()}
     * before any logging methods are used.
     *
     * @param plugin the MMOBlock plugin instance
     */
    public static void init(final JavaPlugin plugin) {
        MMOBlockLogger.plugin = plugin;
        reload();
        initialized = true;
    }

    /**
     * Reload configuration (prefix, debug mode) from the plugin config.
     * Called automatically by {@link #init(JavaPlugin)} and can be called
     * again on /mmoblock reload to pick up config changes.
     */
    public static void reload() {
        if (plugin == null) return;
        debug = plugin.getConfig().getBoolean("debug", false);
        final String rawPrefix = plugin.getConfig().getString(
                "log-prefix",
                "[MMOBlock] "
        );
        parsedPrefix = ColorLogger.parseColorTags(rawPrefix);
        clearHistory();
    }

    // -------------------------------------------------------------
    // Translator
    // -------------------------------------------------------------

    /**
     * Set the translator for i18n-aware logging. When set, the overloaded
     * log methods (e.g. {@link #info(String, String)}) will use this
     * translator to resolve keys into localized console messages.
     *
     * @param translator the translator, or null to disable i18n logging
     */
    public static void setTranslator(final Translator translator) {
        MMOBlockLogger.translator = translator;
    }

    /**
     * @return the current translator, or null if none is set
     */
    public static Translator getTranslator() {
        return translator;
    }

    // -------------------------------------------------------------
    // Convenience methods — plain text
    // -------------------------------------------------------------

    /**
     * Log at DEBUG level. Only shown when {@code debug: true} in config.
     */
    public static void debug(final String message) {
        log(Level.DEBUG, message, null);
    }

    /**
     * Log at INFO level.
     */
    public static void info(final String message) {
        log(Level.INFO, message, null);
    }

    /**
     * Log at WARNING level.
     */
    public static void warning(final String message) {
        log(Level.WARNING, message, null);
    }

    /**
     * Log at WARNING level with an associated throwable (prints stack trace).
     */
    public static void warning(final String message, final Throwable thrown) {
        log(Level.WARNING, message, thrown);
    }

    /**
     * Log at ERROR level.
     */
    public static void error(final String message) {
        log(Level.ERROR, message, null);
    }

    /**
     * Log at ERROR level with an associated throwable (prints stack trace).
     */
    public static void error(final String message, final Throwable thrown) {
        log(Level.ERROR, message, thrown);
    }

    /**
     * Log at SEVERE level.
     */
    public static void severe(final String message) {
        log(Level.SEVERE, message, null);
    }

    /**
     * Log at SEVERE level with an associated throwable (prints stack trace).
     */
    public static void severe(final String message, final Throwable thrown) {
        log(Level.SEVERE, message, thrown);
    }

    // -------------------------------------------------------------
    // Convenience methods — i18n-aware (key + defaultMessage)
    // -------------------------------------------------------------

    /**
     * Log at DEBUG level using i18n translation. Falls back to {@code defaultMessage}
     * if the key is not found or no translator is set.
     */
    public static void debug(final String key, final String defaultMessage) {
        log(Level.DEBUG, translate(key, defaultMessage, Map.of()), null);
    }

    /**
     * Log at DEBUG level using i18n translation with placeholders.
     */
    public static void debug(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        log(Level.DEBUG, translate(key, defaultMessage, placeholders), null);
    }

    /**
     * Log at INFO level using i18n translation. Falls back to {@code defaultMessage}
     * if the key is not found or no translator is set.
     */
    public static void info(final String key, final String defaultMessage) {
        log(Level.INFO, translate(key, defaultMessage, Map.of()), null);
    }

    /**
     * Log at INFO level using i18n translation with placeholders.
     */
    public static void info(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        log(Level.INFO, translate(key, defaultMessage, placeholders), null);
    }

    /**
     * Log at WARNING level using i18n translation. Falls back to {@code defaultMessage}
     * if the key is not found or no translator is set.
     */
    public static void warning(final String key, final String defaultMessage) {
        log(Level.WARNING, translate(key, defaultMessage, Map.of()), null);
    }

    /**
     * Log at WARNING level using i18n translation with placeholders.
     */
    public static void warning(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        log(Level.WARNING, translate(key, defaultMessage, placeholders), null);
    }

    /**
     * Log at ERROR level using i18n translation. Falls back to {@code defaultMessage}
     * if the key is not found or no translator is set.
     */
    public static void error(final String key, final String defaultMessage) {
        log(Level.ERROR, translate(key, defaultMessage, Map.of()), null);
    }

    /**
     * Log at ERROR level using i18n translation with placeholders.
     */
    public static void error(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        log(Level.ERROR, translate(key, defaultMessage, placeholders), null);
    }

    /**
     * Log at SEVERE level using i18n translation. Falls back to {@code defaultMessage}
     * if the key is not found or no translator is set.
     */
    public static void severe(final String key, final String defaultMessage) {
        log(Level.SEVERE, translate(key, defaultMessage, Map.of()), null);
    }

    /**
     * Log at SEVERE level using i18n translation with placeholders.
     */
    public static void severe(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        log(Level.SEVERE, translate(key, defaultMessage, placeholders), null);
    }

    // -------------------------------------------------------------
    // Translation helper
    // -------------------------------------------------------------

    /**
     * Translate a key using the configured translator, or return the default message
     * if no translator is set.
     */
    private static String translate(final String key, final String defaultMessage, final Map<String, String> placeholders) {
        if (translator == null) {
            return defaultMessage;
        }
        try {
            return translator.translate(key, defaultMessage, placeholders);
        } catch (final Exception e) {
            return defaultMessage;
        }
    }

    // -------------------------------------------------------------
    // Core log method
    // -------------------------------------------------------------

    /**
     * Internal log dispatch. Formats the message with the configured prefix
     * and color, sends it to console, and optionally prints a stack trace.
     */
    private static void log(final Level level, final String message, final Throwable thrown) {
        if (!shouldLog(level, message)) {
            return;
        }

        final String colored = formatMessage(level, message);
        final String finalMessage = parsedPrefix + colored;

        Bukkit.getConsoleSender().sendMessage(finalMessage);

        if (thrown != null) {
            thrown.printStackTrace(); // Print stack trace to console
        }

        markAsLogged(message);
    }

    // -------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------

    private static String formatMessage(final Level level, final String message) {
        return switch (level) {
            case DEBUG -> colorLogger.brightBlack("[DEBUG] " + message);
            case INFO -> colorLogger.brightBlue("[+] " + message);
            case WARNING -> colorLogger.brightYellow("[!] " + message);
            case ERROR, SEVERE -> colorLogger.brightRed("[ERROR] " + message);
        };
    }

    // -------------------------------------------------------------
    // Filtering
    // -------------------------------------------------------------

    private static boolean shouldLog(final Level level, final String message) {
        // Always log ERROR and SEVERE
        if (level == Level.ERROR || level == Level.SEVERE) {
            return true;
        }

        // DEBUG requires debug mode to be enabled
        if (level == Level.DEBUG && !debug) {
            return false;
        }

        // De-duplicate: skip if we've already logged this exact message
        return !loggedMessages.contains(message);
    }

    private static void markAsLogged(final String message) {
        loggedMessages.add(message);
    }

    // -------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------

    /**
     * Clear the message deduplication history.
     */
    public static void clearHistory() {
        loggedMessages.clear();
    }

    /**
     * @return {@code true} if {@link #init(JavaPlugin)} has been called
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * @return whether debug mode is enabled
     */
    public static boolean isDebugEnabled() {
        return debug;
    }

    /**
     * Enable or disable debug mode at runtime.
     */
    public static void setDebugEnabled(final boolean enabled) {
        debug = enabled;
        if (enabled) {
            info("logger.debug_enabled", "Debug mode enabled");
        } else {
            info("logger.debug_disabled", "Debug mode disabled");
        }
    }

    /**
     * Reset the logger state. Useful for testing.
     */
    public static void reset() {
        initialized = false;
        plugin = null;
        debug = false;
        parsedPrefix = "";
        translator = null;
        loggedMessages.clear();
    }
}
