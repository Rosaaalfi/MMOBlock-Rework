package me.chyxelmc.mmoblock.nms.v1_21_11;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapterProvider;

@SuppressWarnings("java:S101")
public final class NmsProvider_v1_21_11 implements NmsAdapterProvider {

    @Override
    public String targetMinecraftVersion() {
        return "1.21.11";
    }

    @Override
    public NmsAdapter create() {
        return new NmsAdapter_v1_21_11();
    }
}
