package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

import java.util.List;

public interface ToolAction {
    Material material();
    int clickNeeded();
    int decreaseDurability();
    List<String> allowedDrops();
    String clickType();

    // ---- Third-party tool IDs ----

    /**
     * ItemsAdder namespaced item ID for this tool, e.g. {@code "itemsadder:iron_pick"}.
     * When non-null, tool matching is performed via ItemsAdder API instead of Bukkit {@link org.bukkit.Material} comparison.
     */
    default String itemsAdderId() {
        return null;
    }

    /**
     * CraftEngine namespaced item ID for this tool, e.g. {@code "craftengine:custom_pickaxe"}.
     * When non-null, tool matching is performed via CraftEngine API instead of Bukkit {@link org.bukkit.Material} comparison.
     */
    default String craftEngineId() {
        return null;
    }
}
