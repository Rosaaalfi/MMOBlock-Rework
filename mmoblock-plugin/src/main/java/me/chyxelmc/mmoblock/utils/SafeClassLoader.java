package me.chyxelmc.mmoblock.utils;

/**
 * Utility for safe class resolution when interacting with optional
 * third-party plugins (e.g., ModelEngine, ItemsAdder, BetterModel).
 *
 * <p>This class provides methods that attempt to load a class by name
 * without throwing if it is not found, returning {@code null} instead.
 * This avoids the boilerplate of try-catch blocks throughout the codebase.</p>
 */
public final class SafeClassLoader {

    private SafeClassLoader() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Attempt to load a class by its fully qualified name.
     *
     * @param className the fully qualified class name to load
     * @param <T>       the expected type of the class
     * @return the Class object, or {@code null} if the class is not found
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(final String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Attempt to load a class by its fully qualified name, logging a
     * debug message if not found.
     *
     * @param className the fully qualified class name to load
     * @param context   a descriptive label for debug logging (e.g., plugin name)
     * @param <T>       the expected type of the class
     * @return the Class object, or {@code null} if the class is not found
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(final String className, final String context) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (final ClassNotFoundException e) {
            MMOBlockLogger.debug("[" + context + "] Class not available: " + className);
            return null;
        }
    }

    /**
     * Check whether a class is available on the classpath.
     *
     * @param className the fully qualified class name to check
     * @return {@code true} if the class is found, {@code false} otherwise
     */
    public static boolean isAvailable(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check whether a class is available, optionally logging a message if not.
     *
     * @param className the fully qualified class name to check
     * @param context   a descriptive label for debug logging
     * @return {@code true} if the class is found, {@code false} otherwise
     */
    public static boolean isAvailable(final String className, final String context) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            MMOBlockLogger.debug("[" + context + "] Plugin not available: " + className);
            return false;
        }
    }

    /**
     * Load a class by name. Unlike {@link #loadClass(String)}, this method
     * throws a runtime exception if the class is not found, making it suitable
     * for use in trusted contexts where the class is expected to be present.
     *
     * @param className the fully qualified class name to load
     * @param <T>       the expected type of the class
     * @return the Class object (never null)
     * @throws IllegalStateException if the class is not found
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadTrusted(final String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Required class not found: " + className, e);
        }
    }
}
