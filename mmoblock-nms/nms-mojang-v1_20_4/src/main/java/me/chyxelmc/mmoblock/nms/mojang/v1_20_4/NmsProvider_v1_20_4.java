package me.chyxelmc.mmoblock.nms.mojang.v1_20_4;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapterProvider;

@SuppressWarnings("java:S101")
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
