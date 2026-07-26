package me.chyxelmc.mmoblock.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom colored logger for MMOBlock based on {@link ColorLogger}.
 * <p>
 * Provides colored console output with configurable prefix, log level filtering,
 * and message deduplication. Designed as a singleton initialized during plugin startup.
 * </p>
 * <p>
 * Usage:
 * <pre>{@code
 *   // Initialize in onEnable():
 *   MMOBlockLogger.init(this);
 *
 *   // Static convenience methods:
 *   MMOBlockLogger.info("Something happened");
 *   MMOBlockLogger.warning("Something might be wrong");
 *   MMOBlockLogger.error("Something failed: " + ex.getMessage());
 *   MMOBlockLogger.debug("Debug info (only shown when debug=true)");
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

    private static JavaPlugin plugin;
    private static boolean debug;
    private static String parsedPrefix;
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
                "<cyan>MMO</cyan><yellow>Block</yellow> <reset>|</reset> "
        );
        parsedPrefix = ColorLogger.parseColorTags(rawPrefix);
        clearHistory();
    }

    // -------------------------------------------------------------
    // Convenience methods
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
            case DEBUG -> colorLogger.brightBlack("[≠] " + message);
            case INFO -> colorLogger.brightBlue("[+] " + message);
            case WARNING -> colorLogger.brightYellow("[!] " + message);
            case ERROR, SEVERE -> colorLogger.brightRed("[×] " + message);
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
            info("Debug mode enabled");
        } else {
            info("Debug mode disabled");
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
        loggedMessages.clear();
    }
}
