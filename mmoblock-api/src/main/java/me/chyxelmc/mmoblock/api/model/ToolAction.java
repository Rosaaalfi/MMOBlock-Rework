package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

import java.util.List;

public interface ToolAction {
    Material material();
    int clickNeeded();
    int decreaseDurability();
    List<String> allowedDrops();
    String clickType();
}
