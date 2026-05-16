package me.chyxelmc.mmoblock.nmsloader;

import java.util.Set;

public interface NmsAdapterProvider {

    String targetMinecraftVersion();

    NmsAdapter create();
}