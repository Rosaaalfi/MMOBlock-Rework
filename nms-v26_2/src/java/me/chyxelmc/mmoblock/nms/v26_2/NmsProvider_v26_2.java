package me.chyxelmc.mmoblock.nms.v26_2;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapterProvider;
import java.util.Set;

public final class NmsProvider_v26_2 implements NmsAdapterProvider {

    @Override
    public String targetMinecraftVersion() {
        return "26.2";
    }

    @Override
    public NmsAdapter create() {
        return new NmsAdapter_v26_2();
    }
}