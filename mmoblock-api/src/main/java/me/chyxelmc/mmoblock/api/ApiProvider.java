package me.chyxelmc.mmoblock.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider for the global {@link MMOBlockApi} instance.
 *
 * <p>Third-party addon plugins should use {@link MMOBlockApi#get()} instead of
 * accessing this class directly.</p>
 */
public final class ApiProvider {

    private static MMOBlockApi api;

    private ApiProvider() {
    }

    /**
     * Register (or clear) the API instance.
     * Called internally by MMOBlock on enable/disable.
     */
    public static void register(@Nullable final MMOBlockApi instance) {
        api = instance;
    }

    /**
     * Get the current API instance, or null if MMOBlock is not enabled.
     */
    @Nullable
    public static MMOBlockApi getApi() {
        return api;
    }

    /**
     * Get the current API instance, throwing if not available.
     *
     * @return the API instance
     * @throws IllegalStateException if MMOBlock is not enabled
     */
    @NotNull
    public static MMOBlockApi requireApi() {
        final MMOBlockApi instance = api;
        if (instance == null) {
            throw new IllegalStateException("MMOBlock API is not available. Is MMOBlock enabled?");
        }
        return instance;
    }
}
