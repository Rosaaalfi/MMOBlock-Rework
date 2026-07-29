package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

import java.util.List;

public interface ToolAction {
    Material material();
    int clickNeeded();
    int decreaseDurability();
    List<String> allowedDrops();
    String clickType();

    default boolean autoProgress() {
        return false;
    }

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

    /**
     * MMOItems item ID for this tool, e.g. {@code "custom_pickaxe"}.
     * When non-null, tool matching is performed via MMOItems API instead of Bukkit {@link org.bukkit.Material} comparison.
     */
    default String mmoItemsId() {
        return null;
    }
}
