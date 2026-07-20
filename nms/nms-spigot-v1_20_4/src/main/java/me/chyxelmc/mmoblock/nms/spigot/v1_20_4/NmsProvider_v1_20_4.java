package me.chyxelmc.mmoblock.nms.spigot.v1_20_4;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapterProvider;

public final class NmsProvider_v1_20_4 implements NmsAdapterProvider {

    @Override
    public String targetMinecraftVersion() {
        return "1.20.4";
    }

    @Override
    public NmsAdapter create() {
        return new NmsAdapter_v1_20_4();
    }
}
