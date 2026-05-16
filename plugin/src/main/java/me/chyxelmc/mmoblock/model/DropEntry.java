package me.chyxelmc.mmoblock.model;

import me.chyxelmc.mmoblock.api.model.DropType;
import org.bukkit.Material;

public record DropEntry(
    DropType type,
    Material material,
    int min,
    int max,
    String command,
    double chance,
    String dropType
) implements me.chyxelmc.mmoblock.api.model.DropEntry {
}

