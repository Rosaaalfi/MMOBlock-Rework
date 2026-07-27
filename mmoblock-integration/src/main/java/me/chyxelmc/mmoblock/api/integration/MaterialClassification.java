package me.chyxelmc.mmoblock.api.integration;

import org.bukkit.Material;

/**
 * Resolves a config material string into its typed classification:
 * vanilla {@link Material}, ItemsAdder custom ID, CraftEngine custom ID,
 * or MMOItems custom item ID.
 *
 * <p>Priority when no explicit {@code type} is set (legacy auto-detection):
 * MMOItems &gt; CraftEngine &gt; ItemsAdder &gt; vanilla Material.</p>
 *
 * @param material      the resolved vanilla material ({@code null} for custom items)
 * @param itemsAdderId  the ItemsAdder namespaced ID ({@code null} if not ItemsAdder)
 * @param craftEngineId the CraftEngine namespaced ID ({@code null} if not CraftEngine)
 * @param mmoItemsId    the MMOItems item ID ({@code null} if not MMOItems)
 */
public record MaterialClassification(
        Material material,
        String itemsAdderId,
        String craftEngineId,
        String mmoItemsId
) {

    /**
     * Classify a material string using the explicit type hint and fallback auto-detection.
     *
     * <p>Each integration call is wrapped in a try-catch(Throwable) to protect against
     * {@link NoClassDefFoundError} that can occur when the Paper classloader eagerly
     * resolves all class references during class loading. Since integrations are soft
     * dependencies, their absence must not crash the plugin.</p>
     *
     * @param explicitType    the explicit {@code type} value from config ({@code null} when absent)
     * @param materialString  the raw material / namespaced ID string from config
     * @return the classified result (all fields may be {@code null} if materialString is null/blank)
     */
    public static MaterialClassification resolve(final String explicitType, final String materialString) {
        if (materialString == null || materialString.isBlank()) {
            return new MaterialClassification(null, null, null, null);
        }

        // Explicit type takes precedence
        if (isType(explicitType, "mmoitems")) {
            return new MaterialClassification(null, null, null, materialString);
        }
        if (isType(explicitType, "craftengine")) {
            return new MaterialClassification(null, null, materialString, null);
        }
        if (isType(explicitType, "itemsadder")) {
            return new MaterialClassification(null, materialString, null, null);
        }

        // Legacy auto-detection when no explicit type is set
        if (explicitType == null) {
            // MMOItems IDs are simple strings without colons, check first
            try {
                if (MMOItemsIntegration.isMMOItemsId(materialString)) {
                    return new MaterialClassification(null, null, null, materialString);
                }
            } catch (final Throwable ignored) {
                // Integration not available — NoClassDefFoundError, etc.
            }
            try {
                if (CraftEngineIntegration.isCraftEngineConfigId(materialString)) {
                    return new MaterialClassification(null, null, materialString, null);
                }
            } catch (final Throwable ignored) {
                // Integration not available — NoClassDefFoundError, etc.
            }
            try {
                if (ItemsAdderIntegration.isItemsAdderId(materialString)) {
                    return new MaterialClassification(null, materialString, null, null);
                }
            } catch (final Throwable ignored) {
                // Integration not available — NoClassDefFoundError, etc.
            }
        }

        // Vanilla fallback — strip namespace prefix before matching
        final String normalized = materialString.contains(":")
                ? materialString.substring(materialString.indexOf(':') + 1)
                : materialString;
        return new MaterialClassification(Material.matchMaterial(normalized, false), null, null, null);
    }

    private static boolean isType(final String type, final String expected) {
        return type != null && expected.equalsIgnoreCase(type.trim());
    }
}
