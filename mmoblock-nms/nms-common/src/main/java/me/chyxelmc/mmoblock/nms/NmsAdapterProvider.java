package me.chyxelmc.mmoblock.nms;

public interface NmsAdapterProvider {

    String targetMinecraftVersion();

    NmsAdapter create();
}