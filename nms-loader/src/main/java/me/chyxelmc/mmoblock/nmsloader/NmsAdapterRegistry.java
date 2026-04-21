package me.chyxelmc.mmoblock.nmsloader;

import org.bukkit.Bukkit;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Logger;

public final class NmsAdapterRegistry {

    public enum MappingType {
        MOJANG,
        SPIGOT
    }

    private NmsAdapterRegistry() {
    }

    public static MappingType detectMappingType() {
        try {
            Class.forName("net.minecraft.network.chat.ChatModifier");
            return MappingType.SPIGOT;
        } catch (final ClassNotFoundException ignored) {
        }

        try {
            Class.forName("net.minecraft.network.chat.Component");
            return MappingType.MOJANG;
        } catch (final ClassNotFoundException ignored) {
        }

        return MappingType.MOJANG;
    }

    public static MappingType detectMappingType(final Logger logger) {
        final MappingType type = detectMappingType();
        logger.info("Detected server mapping type: " + type);
        return type;
    }

    public static NmsAdapter resolveCurrent(final Logger logger) {
        final MappingType mappingType = detectMappingType(logger);
        final String serverVersion = Bukkit.getMinecraftVersion();
        return resolve(serverVersion, logger, mappingType);
    }

    public static NmsAdapter resolve(final String serverVersion, final Logger logger) {
        return resolve(serverVersion, logger, detectMappingType(logger));
    }

    public static NmsAdapter resolve(final String serverVersion, final Logger logger, final MappingType mappingType) {
        final List<NmsAdapterProvider> providers = loadProviders(mappingType);
        if (providers.isEmpty()) {
            throw new IllegalStateException("No NMS adapter providers found on classpath for version: " + serverVersion + ", mapping: " + mappingType);
        }

        providers.sort(Comparator.comparing(provider -> {
            try {
                return provider.targetMinecraftVersion();
            } catch (NoClassDefFoundError e) {
                return "0.0.0";
            }
        }, NmsAdapterRegistry::compareVersions));

        for (final NmsAdapterProvider provider : providers) {
            String version;
            try {
                version = provider.targetMinecraftVersion();
            } catch (NoClassDefFoundError e) {
                continue;
            }
            if (version.equals(serverVersion)) {
                try {
                    logger.info("Loaded NMS adapter: " + version + " (" + mappingType + ")");
                    return provider.create();
                } catch (NoClassDefFoundError e) {
                    logger.warning("Skipping NMS adapter for " + version + " due to missing classes: " + e.getMessage());
                    continue;
                }
            }
        }

        NmsAdapterProvider fallback = null;
        for (final NmsAdapterProvider provider : providers) {
            String version;
            try {
                version = provider.targetMinecraftVersion();
            } catch (NoClassDefFoundError e) {
                continue;
            }
            if (compareVersions(version, serverVersion) <= 0) {
                fallback = provider;
            }
        }

        if (fallback != null) {
            try {
                logger.warning("No exact NMS adapter for " + serverVersion + ", using fallback " + fallback.targetMinecraftVersion());
                return fallback.create();
            } catch (NoClassDefFoundError e) {
                logger.warning("Fallback NMS adapter failed to load due to missing classes: " + e.getMessage());
            }
        }

        throw new IllegalStateException("No compatible NMS adapter for " + serverVersion);
    }

    private static List<NmsAdapterProvider> loadProviders(final MappingType mappingType) {
        final List<NmsAdapterProvider> providers = new ArrayList<>();
        final ClassLoader classLoader = NmsAdapterProvider.class.getClassLoader();

        java.util.Set<String> scannedJars = new java.util.HashSet<>();

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
        } catch (final Throwable ignored) {
        }

        try {
            final ClassLoader cl = classLoader;
            java.net.URL[] urls = null;
            if (cl instanceof java.net.URLClassLoader ucl) {
                urls = ucl.getURLs();
            } else {
                final java.lang.reflect.Field f = java.net.URLClassLoader.class.getDeclaredField("ucp");
                f.setAccessible(true);
                final Object ucp = f.get(cl);
                final java.lang.reflect.Field urlF = ucp.getClass().getDeclaredField("urls");
                urlF.setAccessible(true);
                urls = (java.net.URL[]) urlF.get(ucp);
            }
            if (urls != null) {
                for (final java.net.URL url : urls) {
                    final String path = url.getPath();
                    if (scannedJars.contains(path)) {
                        continue;
                    }
                    if (!path.endsWith(".jar")) {
                        continue;
                    }
                    scannedJars.add(path);
                }
            }
        } catch (final Throwable ignored) {
        }

        for (final String jarPath : scannedJars) {
            try {
                final java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath);
                final java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final java.util.jar.JarEntry entry = entries.nextElement();
                    final String name = entry.getName();
                    if (!name.startsWith("me/chyxelmc/mmoblock/nms/") || !name.endsWith("NmsProvider.class")) {
                        continue;
                    }
                    final String className = name.substring(0, name.length() - 6).replace('/', '.');
                    try {
                        final Class<?> providerClass = java.lang.Class.forName(className, false, classLoader);
                        if (NmsAdapterProvider.class.isAssignableFrom(providerClass)) {
                            final Object instance = providerClass.getDeclaredConstructor().newInstance();
                            final NmsAdapterProvider provider = (NmsAdapterProvider) instance;
                            final String pkg = providerClass.getPackageName();

                            if (mappingType == MappingType.MOJANG && pkg.contains(".mojang.")) {
                                providers.add(provider);
                            } else if (mappingType == MappingType.SPIGOT && pkg.contains(".spigot.")) {
                                providers.add(provider);
                            } else if (!pkg.contains(".mojang.") && !pkg.contains(".spigot.")) {
                                providers.add(provider);
                            }
                        }
                    } catch (final Throwable ignored) {
                    }
                }
                jar.close();
            } catch (final Throwable ignored) {
            }
        }

        if (providers.isEmpty()) {
            final ServiceLoader<NmsAdapterProvider> loader = ServiceLoader.load(
                    NmsAdapterProvider.class,
                    classLoader
            );
            for (final NmsAdapterProvider provider : loader) {
                final String className = provider.getClass().getName();
                if (mappingType == MappingType.MOJANG && className.contains(".mojang.")) {
                    providers.add(provider);
                } else if (mappingType == MappingType.SPIGOT && className.contains(".spigot.")) {
                    providers.add(provider);
                } else if (!className.contains(".mojang.") && !className.contains(".spigot.")) {
                    providers.add(provider);
                }
            }
        }

        if (mappingType == MappingType.SPIGOT) {
            providers.removeIf(p -> p.getClass().getName().contains(".mojang."));
        }
        return providers;
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
}