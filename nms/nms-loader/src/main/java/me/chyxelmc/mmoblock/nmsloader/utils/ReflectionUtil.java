package me.chyxelmc.mmoblock.nmsloader.utils;

import java.lang.reflect.AccessibleObject;
import java.util.logging.Logger;

import org.bukkit.Bukkit;

/**
 * Utility for performing reflective access in a way that is auditable in server logs.
 * All reflection bypasses in the plugin should go through this helper so server
 * administrators can monitor which plugin internals are being accessed.
 */
public final class ReflectionUtil {

    private static Logger logger;

    private ReflectionUtil() {
    }

    /**
     * Initializes the logger used by {@link #safeSetAccessible(AccessibleObject, String)}.
     * Should be called once during plugin startup for the most descriptive logger.
     * If not called, falls back to {@link Bukkit#getLogger()}.
     */
    public static void init(final Logger pluginLogger) {
        logger = pluginLogger;
    }

    /**
     * Safely makes an {@link AccessibleObject} accessible and logs a warning with the
     * reason so every reflection bypass is auditable.
     *
     * @param target the accessible object (field, method, constructor) to make accessible
     * @param reason a human-readable explanation of why reflection bypass is necessary
     */
    @SuppressWarnings({"java:S3011", "squid:S3011"})
    // SECURITY: intentional reflection - auditable via server log warning
    public static void safeSetAccessible(final AccessibleObject target, final String reason) {
        target.setAccessible(true);
        final Logger log = logger != null ? logger : (Bukkit.getServer() != null ? Bukkit.getLogger() : null);
        if (log != null) {
            log.warning("[MMOBlock] Reflection access: " + reason + " on " + target);
        }
    }
}
