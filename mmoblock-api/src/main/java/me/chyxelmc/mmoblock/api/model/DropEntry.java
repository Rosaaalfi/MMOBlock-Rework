package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

public interface DropEntry {
    DropType type();
    Material material();
    int min();
    int max();
    String command();
    double chance();
    String dropType();
}
