package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;

public record DropEntry(
    DropType type,
    Material material,
    int min,
    int max,
    String command,
    double chance,
    String dropType
) {
    public enum DropType {
        MATERIAL,
        EXPERIENCE,
        COMMAND
    }
}

