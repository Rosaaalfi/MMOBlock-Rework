package me.chyxelmc.mmoblock.nms.utils;

import java.lang.reflect.AccessibleObject;

/**
 * Utility for performing reflective access in a way that is auditable in server logs.
 * All reflection bypasses in the plugin should go through this helper so server
 * administrators can monitor which plugin internals are being accessed.
 */
public final class ReflectionUtil {

    private ReflectionUtil() {
    }

    /**
     * Initialization is no longer required — NmsLogger is used automatically.
     * Kept for backward compatibility with existing callers.
     *
     * @deprecated No longer needed. Call can be safely removed.
     */
    @Deprecated
    public static void init() {
        // NmsLogger is self-initializing; this method is kept for compatibility.
    }

    /**
     * Safely makes an {@link AccessibleObject} accessible and logs a warning with the
     * reason so every reflection bypass is auditable.
     *
     * @param target the accessible object (field, method, constructor) to make accessible
     * @param reason a human-readable explanation of why reflection bypass is necessary
     */
    @SuppressWarnings({"all", "java:S3011", "squid:S3011", "PMD.AccessibilityAlteration"})
    // SECURITY: intentional reflection - auditable via server log warning
    public static void safeSetAccessible(final AccessibleObject target, final String reason) {
        target.setAccessible(true); // NOSONAR // NOPMD
        NmsLogger.warning("Reflection access: " + reason + " on " + target);
    }

    /**
     * Makes an {@link AccessibleObject} accessible without logging. Use this for
     * brute-force search loops where many fields are tried and failure is expected,
     * to avoid spamming the server log.
     *
     * @param target the accessible object (field, method, constructor) to make accessible
     */
    @SuppressWarnings({"all", "java:S3011", "squid:S3011", "PMD.AccessibilityAlteration"})
    public static void setAccessibleQuietly(final AccessibleObject target) {
        target.setAccessible(true); // NOSONAR // NOPMD
    }
}
