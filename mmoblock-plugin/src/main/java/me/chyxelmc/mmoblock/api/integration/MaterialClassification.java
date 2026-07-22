package me.chyxelmc.mmoblock.api.integration;

import org.bukkit.Material;

/**
 * Resolves a config material string into its typed classification:
 * vanilla {@link Material}, ItemsAdder custom ID, or CraftEngine custom ID.
 *
 * <p>Priority when no explicit {@code type} is set (legacy auto-detection):
 * CraftEngine &gt; ItemsAdder &gt; vanilla Material.</p>
 *
 * @param material     the resolved vanilla material ({@code null} for custom items)
 * @param itemsAdderId the ItemsAdder namespaced ID ({@code null} if not ItemsAdder)
 * @param craftEngineId the CraftEngine namespaced ID ({@code null} if not CraftEngine)
 */
public record MaterialClassification(
        Material material,
        String itemsAdderId,
        String craftEngineId
) {

    /**
     * Classify a material string using the explicit type hint and fallback auto-detection.
     *
     * @param explicitType    the explicit {@code type} value from config ({@code null} when absent)
     * @param materialString  the raw material / namespaced ID string from config
     * @return the classified result (all fields may be {@code null} if materialString is null/blank)
     */
    public static MaterialClassification resolve(final String explicitType, final String materialString) {
        if (materialString == null || materialString.isBlank()) {
            return new MaterialClassification(null, null, null);
        }

        // Explicit type takes precedence
        if (isType(explicitType, "craftengine")) {
            return new MaterialClassification(null, null, materialString);
        }
        if (isType(explicitType, "itemsadder")) {
            return new MaterialClassification(null, materialString, null);
        }

        // Legacy auto-detection when no explicit type is set
        if (explicitType == null) {
            if (CraftEngineIntegration.isCraftEngineConfigId(materialString)) {
                return new MaterialClassification(null, null, materialString);
            }
            if (ItemsAdderIntegration.isItemsAdderId(materialString)) {
                return new MaterialClassification(null, materialString, null);
            }
        }

        // Vanilla fallback — strip namespace prefix before matching
        final String normalized = materialString.contains(":")
                ? materialString.substring(materialString.indexOf(':') + 1)
                : materialString;
        return new MaterialClassification(Material.matchMaterial(normalized, false), null, null);
    }

    private static boolean isType(final String type, final String expected) {
        return type != null && expected.equalsIgnoreCase(type.trim());
    }
}
