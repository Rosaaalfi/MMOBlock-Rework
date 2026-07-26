package me.chyxelmc.mmoblock.nms;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.Bukkit;

import me.chyxelmc.mmoblock.nms.utils.NmsLogger;
import me.chyxelmc.mmoblock.nms.utils.ReflectionUtil;

public final class NmsAdapterRegistry {

    private static final String MOJANG_PACKAGE = ".mojang.";
    private static final String SPIGOT_PACKAGE = ".spigot.";

    public enum MappingType {
        MOJANG,
        SPIGOT
    }

    private NmsAdapterRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static MappingType detectMappingType() {
        try {
            Class.forName("net.minecraft.network.chat.ChatModifier");
            return MappingType.SPIGOT;
        } catch (final ClassNotFoundException ignored) {
            // Spigot mapping not found, continue
        }

        try {
            Class.forName("net.minecraft.network.chat.Component");
            return MappingType.MOJANG;
        } catch (final ClassNotFoundException ignored) {
            // Mojang mapping not found, default below
        }

        return MappingType.MOJANG;
    }

    public static void logMappingType() {
        final MappingType type = detectMappingType();
        NmsLogger.info("Detected server mapping type: " + type);
    }

    public static MappingType detectMappingType(final java.util.logging.Logger logger) {
        final MappingType type = detectMappingType();
        NmsLogger.info("Detected server mapping type: " + type);
        return type;
    }

    public static NmsAdapter resolveCurrent() {
        final MappingType mappingType = detectMappingType();
        final String serverVersion = Bukkit.getMinecraftVersion();
        return resolve(serverVersion, mappingType);
    }

    public static NmsAdapter resolveCurrent(final java.util.logging.Logger logger) {
        return resolveCurrent();
    }

    public static NmsAdapter resolve(final String serverVersion) {
        return resolve(serverVersion, detectMappingType());
    }

    public static NmsAdapter resolve(final String serverVersion, final java.util.logging.Logger logger) {
        return resolve(serverVersion, detectMappingType());
    }

    public static NmsAdapter resolve(final String serverVersion, final java.util.logging.Logger logger, final MappingType mappingType) {
        return resolve(serverVersion, mappingType);
    }

    public static NmsAdapter resolve(final String serverVersion, final MappingType mappingType) {
        final List<NmsAdapterProvider> providers = loadProviders(mappingType);
        if (providers.isEmpty()) {
            throw new IllegalStateException("No NMS adapter providers found on classpath for version: " + serverVersion + ", mapping: " + mappingType);
        }

        providers.sort(Comparator.comparing(provider -> {
            try {
                return getPrimaryVersion(provider.targetMinecraftVersion());
            } catch (NoClassDefFoundError e) {
                return "0.0.0";
            }
        }, NmsAdapterRegistry::compareVersions));

        // 1. Try exact match first
        for (final NmsAdapterProvider provider : providers) {
            final String versionRaw = safeGetVersion(provider);
            if (versionRaw == null) continue;

            if (matchesVersion(versionRaw, serverVersion)) {
                try {
                    NmsLogger.info("Loaded NMS adapter: " + serverVersion + " (Provider defined: [" + versionRaw + "])");
                    return provider.create();
                } catch (NoClassDefFoundError e) {
                    NmsLogger.warning("Skipping NMS adapter for " + versionRaw + " due to missing classes: " + e.getMessage());
                }
            }
        }

        // 2. Fallback: find the nearest version below server version
        final FallbackResult fallback = findFallbackProvider(providers, serverVersion);
        if (fallback != null) {
            try {
                NmsLogger.warning("No exact NMS adapter for " + serverVersion + ", using fallback via target " + fallback.usedVersion());
                return fallback.provider().create();
            } catch (NoClassDefFoundError e) {
                NmsLogger.warning("Fallback NMS adapter failed to load due to missing classes: " + e.getMessage());
            }
        }

        throw new IllegalStateException("No compatible NMS adapter for " + serverVersion);
    }

    private static String safeGetVersion(final NmsAdapterProvider provider) {
        try {
            return provider.targetMinecraftVersion();
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private static FallbackResult findFallbackProvider(final List<NmsAdapterProvider> providers, final String serverVersion) {
        NmsAdapterProvider best = null;
        String bestVersion = null;

        for (final NmsAdapterProvider provider : providers) {
            final String versionRaw = safeGetVersion(provider);
            if (versionRaw == null) continue;

            for (final String subVer : versionRaw.split(",")) {
                final String cleanVer = subVer.trim();
                if (compareVersions(cleanVer, serverVersion) <= 0) {
                    best = provider;
                    bestVersion = cleanVer;
                }
            }
        }

        return best != null ? new FallbackResult(best, bestVersion) : null;
    }

    private static boolean matchesVersion(String versionRaw, String serverVersion) {
        if (versionRaw == null || serverVersion == null) return false;
        for (String part : versionRaw.split(",")) {
            if (part.trim().equals(serverVersion)) {
                return true;
            }
        }
        return false;
    }

    private static String getPrimaryVersion(String versionRaw) {
        if (versionRaw == null) return "0.0.0";
        if (versionRaw.contains(",")) {
            return versionRaw.split(",")[0].trim();
        }
        return versionRaw.trim();
    }

    private static List<NmsAdapterProvider> loadProviders(final MappingType mappingType) {
        final List<NmsAdapterProvider> providers = new ArrayList<>();
        final ClassLoader classLoader = NmsAdapterProvider.class.getClassLoader();

        final java.util.Set<String> scannedJars = new java.util.HashSet<>();
        scanCodeSource(scannedJars);
        scanClassLoaderUrls(classLoader, scannedJars);

        scanJarsForProviders(scannedJars, mappingType, providers, classLoader);

        if (providers.isEmpty()) {
            loadProvidersFromServiceLoader(mappingType, providers, classLoader);
        }

        if (mappingType == MappingType.SPIGOT) {
            providers.removeIf(p -> p.getClass().getName().contains(MOJANG_PACKAGE));
        }
        return providers;
    }

    private static void scanCodeSource(final java.util.Set<String> scannedJars) {
        try {
            final ProtectionDomain pd = NmsAdapterProvider.class.getProtectionDomain();
            if (pd != null) {
                final java.security.CodeSource codeSource = pd.getCodeSource();
                if (codeSource != null) {
                    final java.net.URL location = codeSource.getLocation();
                    if (location != null && location.getPath().endsWith(".jar")) {
                        scannedJars.add(location.getPath());
                    }
                }
            }
        } catch (final Exception ignored) {
            // Code source not available; skip jar scanning
        }
    }

    private static void scanClassLoaderUrls(final ClassLoader classLoader, final java.util.Set<String> scannedJars) {
        try {
            java.net.URL[] urls = null;
            if (classLoader instanceof java.net.URLClassLoader ucl) {
                urls = ucl.getURLs();
            } else {
                urls = extractUrlsFromClassLoader(classLoader);
            }
            if (urls != null) {
                for (final java.net.URL url : urls) {
                    final String path = url.getPath();
                    if (!path.endsWith(".jar") || scannedJars.contains(path)) continue;
                    scannedJars.add(path);
                }
            }
        } catch (final Exception ignored) {
            // URL scanning not supported; skip
        }
    }

    private static java.net.URL[] extractUrlsFromClassLoader(final ClassLoader cl) throws Exception {
        final java.lang.reflect.Field f = java.net.URLClassLoader.class.getDeclaredField("ucp");
        ReflectionUtil.safeSetAccessible(f, "URLClassLoader ucp field for non-URLClassLoader fallback");
        final Object ucp = f.get(cl);
        final java.lang.reflect.Field urlF = ucp.getClass().getDeclaredField("urls");
        ReflectionUtil.safeSetAccessible(urlF, "URLClassLoader ucp.urls field for non-URLClassLoader fallback");
        return (java.net.URL[]) urlF.get(ucp);
    }

    private static void scanJarsForProviders(
            final java.util.Set<String> scannedJars,
            final MappingType mappingType,
            final List<NmsAdapterProvider> providers,
            final ClassLoader classLoader
    ) {
        for (final String jarPath : scannedJars) {
            try (JarFile jar = new JarFile(jarPath)) {
                final java.util.Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    final String name = entry.getName();
                    if (!name.startsWith("me/chyxelmc/mmoblock/nms/") || !name.endsWith("NmsProvider.class")) {
                        continue;
                    }
                    final String className = name.substring(0, name.length() - 6).replace('/', '.');
                    try {
                        final Class<?> providerClass = Class.forName(className, false, classLoader);
                        if (NmsAdapterProvider.class.isAssignableFrom(providerClass)) {
                            final Object instance = providerClass.getDeclaredConstructor().newInstance();
                            final NmsAdapterProvider provider = (NmsAdapterProvider) instance;
                            if (isMappingMatch(mappingType, providerClass.getPackageName())) {
                                providers.add(provider);
                            }
                        }
                    } catch (final Exception ignored) {
                        // Provider class not loadable; skip
                    }
                }
            } catch (final Exception ignored) {
                // Jar not readable; skip
            }
        }
    }

    private static boolean isMappingMatch(final MappingType mappingType, final String pkg) {
        return switch (mappingType) {
            case MOJANG -> pkg.contains(MOJANG_PACKAGE) || (!pkg.contains(MOJANG_PACKAGE) && !pkg.contains(SPIGOT_PACKAGE));
            case SPIGOT -> pkg.contains(SPIGOT_PACKAGE) || (!pkg.contains(MOJANG_PACKAGE) && !pkg.contains(SPIGOT_PACKAGE));
        };
    }

    private static void loadProvidersFromServiceLoader(
            final MappingType mappingType,
            final List<NmsAdapterProvider> providers,
            final ClassLoader classLoader
    ) {
        final ServiceLoader<NmsAdapterProvider> loader = ServiceLoader.load(NmsAdapterProvider.class, classLoader);
        for (final NmsAdapterProvider provider : loader) {
            final String className = provider.getClass().getName();
            if (isMappingMatch(mappingType, className)) {
                providers.add(provider);
            }
        }
    }

    private static int compareVersions(final String left, final String right) {
        final int[] a = parseVersion(left);
        final int[] b = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    private static int[] parseVersion(final String value) {
        final String[] split = value.split("\\.");
        final int[] out = new int[]{0, 0, 0};
        for (int i = 0; i < Math.min(3, split.length); i++) {
            out[i] = tryParse(split[i]);
        }
        return out;
    }

    private static int tryParse(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private record FallbackResult(NmsAdapterProvider provider, String usedVersion) {
    }
}