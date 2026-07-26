package me.chyxelmc.mmoblock.nms.utils;

import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Shared logging utility for NMS modules.
 * <p>
 * Wraps Bukkit's server logger with convenience methods so NMS code does not
 * need to import {@code java.util.logging.Logger} directly. All methods are
 * simple pass-throughs to the server logger — no color formatting or
 * deduplication (use {@code MMOBlockLogger} from the plugin module for that).
 * </p>
 * <p>
 * Usage:
 * <pre>{@code
 *   NmsLogger.debug("Debug message");
 *   NmsLogger.info("Info message");
 *   NmsLogger.warning("Warning message");
 *   NmsLogger.warning("Warning with exception", exception);
 * }</pre>
 * </p>
 */
public final class NmsLogger {

    private static final String PREFIX = "[MMOBlock] ";

    private NmsLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @return the Bukkit server logger (never null after server startup)
     */
    private static Logger logger() {
        return Bukkit.getLogger();
    }

    // -------------------------------------------------------------
    // Log methods
    // -------------------------------------------------------------

    /**
     * Log at FINE level (debug).
     */
    public static void debug(final String message) {
        logger().fine(PREFIX + message);
    }

    /**
     * Log at FINE level with an associated throwable.
     */
    public static void debug(final String message, final Throwable thrown) {
        logger().log(java.util.logging.Level.FINE, PREFIX + message, thrown);
    }

    /**
     * Log at INFO level.
     */
    public static void info(final String message) {
        logger().info(PREFIX + message);
    }

    /**
     * Log at WARNING level.
     */
    public static void warning(final String message) {
        logger().warning(PREFIX + message);
    }

    /**
     * Log at WARNING level with an associated throwable.
     */
    public static void warning(final String message, final Throwable thrown) {
        logger().log(java.util.logging.Level.WARNING, PREFIX + message, thrown);
    }
}
