package me.chyxelmc.mmoblock.nmsloader;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Logger;

public final class NmsAdapterRegistry {

    private NmsAdapterRegistry() {
    }

    public static NmsAdapter resolveCurrent(final Logger logger) {
        final String serverVersion = Bukkit.getMinecraftVersion();
        return resolve(serverVersion, logger);
    }

    public static NmsAdapter resolve(final String serverVersion, final Logger logger) {
        final List<NmsAdapterProvider> providers = loadProviders();
        if (providers.isEmpty()) {
            throw new IllegalStateException("No NMS adapter providers found on classpath.");
        }

        providers.sort(Comparator.comparing(NmsAdapterProvider::targetMinecraftVersion, NmsAdapterRegistry::compareVersions));

        for (final NmsAdapterProvider provider : providers) {
            if (provider.targetMinecraftVersion().equals(serverVersion)) {
                logger.info("Loaded exact NMS adapter: " + provider.targetMinecraftVersion());
                return provider.create();
            }
        }

        NmsAdapterProvider fallback = null;
        for (final NmsAdapterProvider provider : providers) {
            if (compareVersions(provider.targetMinecraftVersion(), serverVersion) <= 0) {
                fallback = provider;
            }
        }

        if (fallback != null) {
            logger.warning("No exact NMS adapter for " + serverVersion + ", using fallback " + fallback.targetMinecraftVersion());
            return fallback.create();
        }

        throw new IllegalStateException("No compatible NMS adapter for " + serverVersion);
    }

    private static List<NmsAdapterProvider> loadProviders() {
        final ServiceLoader<NmsAdapterProvider> loader = ServiceLoader.load(
                NmsAdapterProvider.class,
                NmsAdapterProvider.class.getClassLoader()
        );
        final List<NmsAdapterProvider> providers = new ArrayList<>();
        for (final NmsAdapterProvider provider : loader) {
            providers.add(provider);
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
