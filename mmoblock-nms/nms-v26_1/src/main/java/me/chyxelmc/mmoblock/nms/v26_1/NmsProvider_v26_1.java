package me.chyxelmc.mmoblock.nms.v26_1;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapterProvider;
import java.util.Set;

@SuppressWarnings("java:S101")
public final class NmsProvider_v26_1 implements NmsAdapterProvider {

    @Override
    public String targetMinecraftVersion() {
        return "26,26.1,26.1.1,26.1.2";
    }

    @Override
    public NmsAdapter create() {
        return new NmsAdapter_v26_1();
    }
}