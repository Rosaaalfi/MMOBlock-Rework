package me.chyxelmc.mmoblock.api;

import me.chyxelmc.mmoblock.api.service.BlockService;
import me.chyxelmc.mmoblock.api.service.NodeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MMOBlockApi {

    @NotNull
    BlockService getBlockService();

    @Nullable
    NodeService getNodeService();

    @Nullable
    static MMOBlockApi get() {
        return ApiProvider.getApi();
    }
}
