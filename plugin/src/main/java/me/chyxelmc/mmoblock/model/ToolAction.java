package me.chyxelmc.mmoblock.model;

import org.bukkit.Material;

import java.util.List;

public record ToolAction(
    Material material,
    int clickNeeded,
    int decreaseDurability,
    List<String> allowedDrops,
    String clickType
) {
}

