package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;

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

    /**
     * MMOItems item ID for this drop, e.g. {@code "custom_drop"}.
     * When non-null, the drop will resolve via MMOItems API instead of Bukkit {@link org.bukkit.Material}.
     */
    default String mmoItemsId() {
        return null;
    }

    // ---- Experience configuration ----

    /**
     * Source of experience for EXPERIENCE-type drops.
     * Supported values: {@code "vanilla"} (default) or {@code "mmocore"}.
     *
     * @return the experience source string, or {@code "vanilla"} if not set
     */
    default String experienceSource() {
        return "vanilla";
    }

    /**
     * MMOCore profession ID for EXPERIENCE-type drops with mmocore experience source.
     * Default is {@code "main"} which gives class experience.
     * Can be a default MMOCore profession (alchemy, enchanting, farming, fishing, mining,
     * smelting, smithing, woodcutting, etc.) or a custom profession from MMOCore.
     *
     * @return the MMOCore profession ID, or {@code "main"} if not set
     */
    default String mmocoreProfession() {
        return "main";
    }

    // ---- Drop popup ----

    /**
     * Optional popup effect configuration for the drop.
     * When non-null and enabled, shows a floating text popup at the drop location.
     *
     * @return the drop popup configuration, or null if not configured
     */
    default DropPopup dropPopup() {
        return null;
    }

    // ---- Custom drop handler (third-party addon plugins) ----

    /**
     * The custom handler ID for {@link DropType#CUSTOM} drops.
     * This should match a handler ID registered in
     * {@link me.chyxelmc.mmoblock.api.registry.DropHandlerRegistry}.
     *
     * <p>When non-null and the drop type is {@link DropType#CUSTOM},
     * the registered {@link me.chyxelmc.mmoblock.api.drop.DropHandler}
     * is invoked instead of the built-in MATERIAL/EXPERIENCE/COMMAND logic.</p>
     *
     * @return the custom handler ID, or null if not set
     */
    default String customHandlerId() {
        return null;
    }

    /**
     * Custom configuration data for {@link DropType#CUSTOM} drops.
     * This is a free-form map specified in the drops YAML under {@code customData}.
     *
     * <p>Third-party drop handlers can use this data to configure their behavior
     * per drop entry (e.g., backpack type, slot count, GUI title).</p>
     *
     * @return an unmodifiable map of custom data, never null but may be empty
     */
    default Map<String, Object> customData() {
        return Collections.emptyMap();
    }
}
