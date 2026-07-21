package me.chyxelmc.mmoblock.utils;

import java.util.List;

/**
 * Prevents unsafe dynamic class loading by restricting {@link Class#forName(String)}
 * usage to an allowlist of trusted package prefixes.
 * <p>
 * This mitigates arbitrary class-loading attacks where an attacker-controlled
 * string could be used to load dangerous classes.
 */
public final class SafeClassLoader {

    private static final List<String> ALLOWED_PREFIXES = List.of("me.chyxelmc.mmoblock.");

    private SafeClassLoader() {
    }

    /**
     * Loads a class by name only if it belongs to a trusted package.
     *
     * @param className the fully qualified class name to load
     * @return the loaded {@link Class}
     * @throws ClassNotFoundException if the class is not found
     * @throws SecurityException      if the class name is not in a trusted package
     */
    public static Class<?> loadTrusted(final String className) throws ClassNotFoundException {
        final boolean trusted = ALLOWED_PREFIXES.stream().anyMatch(className::startsWith);
        if (!trusted) {
            throw new SecurityException("Blocked untrusted class load attempt: " + className);
        }
        return SafeClassLoader.class.getClassLoader().loadClass(className);
    }
}
