package me.chyxelmc.mmoblock.nmsloader;

public interface NmsAdapterProvider {

    String targetMinecraftVersion();

    NmsAdapter create();
}
