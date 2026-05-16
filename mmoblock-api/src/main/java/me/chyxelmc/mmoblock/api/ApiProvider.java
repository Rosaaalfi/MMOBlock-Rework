package me.chyxelmc.mmoblock.api;

import org.jetbrains.annotations.Nullable;

public final class ApiProvider {

    private static MMOBlockApi api;

    private ApiProvider() {
    }

    public static void register(@Nullable final MMOBlockApi instance) {
        api = instance;
    }

    @Nullable
    public static MMOBlockApi getApi() {
        return api;
    }
}
