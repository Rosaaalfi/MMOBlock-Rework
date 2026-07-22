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

    /**
     * Whether the drop should only be visible to the player who broke the block.
     * Uses Paper API per-player entity visibility.
     */
    default boolean perPlayer() {
        return false;
    }

    /**
     * Whether the dropped item should receive an explosive velocity effect.
     */
    default boolean effectExplosion() {
        return false;
    }

    /**
     * Optional glow effect configuration for the dropped item.
     * When non-null and enabled, applies a colored glow outline via NMS.
     */
    default DropGlow effectGlow() {
        return null;
    }

    /**
     * Optional beam effect configuration for the dropped item.
     * When non-null and enabled, spawns a colored client-side particle beam
     * from the drop location down to the ground.
     */
    default DropBeam effectBeam() {
        return null;
    }

    // ---- Third-party item IDs ----

    /**
     * ItemsAdder namespaced item ID for this drop, e.g. {@code "itemsadder:iron_crumble"}.
     * When non-null, the drop will resolve via ItemsAdder API instead of Bukkit {@link org.bukkit.Material}.
     */
    default String itemsAdderId() {
        return null;
    }

    /**
     * CraftEngine namespaced item ID for this drop, e.g. {@code "craftengine:custom_drop"}.
     * When non-null, the drop will resolve via CraftEngine API instead of Bukkit {@link org.bukkit.Material}.
     */
    default String craftEngineId() {
        return null;
    }
}
