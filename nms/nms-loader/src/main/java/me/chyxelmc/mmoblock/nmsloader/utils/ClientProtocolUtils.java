package me.chyxelmc.mmoblock.nmsloader.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ClientProtocolUtils {

    // 1.19.4 uses protocol 762; anything below this is treated as legacy fallback target.
    private static final int PROTOCOL_1_19_4 = 762;

    private ClientProtocolUtils() {
    }

    public static boolean isLegacyClientBelow_1_19_4(final Player player) {
        final Integer protocol = resolveViaVersionProtocol(player);
        return protocol != null && protocol < PROTOCOL_1_19_4;
    }

    public static Integer resolveViaVersionProtocol(final Player player) {
        if (player == null) {
            return null;
        }
        try {
            final Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            final Object api = viaClass.getMethod("getAPI").invoke(null);
            if (api == null) {
                return null;
            }

            final Method getPlayerVersion = api.getClass().getMethod("getPlayerVersion", UUID.class);
            final Object result = getPlayerVersion.invoke(api, player.getUniqueId());
            if (result instanceof Integer protocol && protocol > 0) {
                return protocol;
            }
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // ViaVersion is optional; fallback path is disabled when API is not available.
        }
        return null;
    }
}
