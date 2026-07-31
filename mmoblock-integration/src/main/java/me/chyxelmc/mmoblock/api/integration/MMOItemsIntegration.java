package me.chyxelmc.mmoblock.api.integration;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.inventory.ItemStack;

/**
 * Integration layer for <a href="https://www.spigotmc.org/resources/mmoitems.1481/">MMOItems</a>.
 * <p>
 * Uses direct MMOItems API references guarded by a static availability check.
 * If MMOItems is not installed the methods are no-ops and the class loads safely
 * because the JVM resolves class references lazily.
 * </p>
 * <p>
 * Provides utilities for resolving custom item stacks, matching held items against
 * MMOItems item IDs, managing custom durability, etc.
 * MMOItems items use a (Type, ID) pair where Type is the item category (e.g. SWORD, PICKAXE)
 * and ID is the unique item identifier. The config only specifies the ID; for item retrieval
 * the integration searches across all registered types.
 * </p>
 */
public final class MMOItemsIntegration {

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            // Verify ALL MMOItems API classes used in this integration are present.
            // This prevents NoClassDefFoundError at runtime when a specific class
            // exists in compile-time dependency but not in the installed plugin.
            Class.forName("net.Indyuce.mmoitems.MMOItems");
            Class.forName("net.Indyuce.mmoitems.api.Type");
            Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.MMOItem");
            Class.forName("net.Indyuce.mmoitems.ItemStats");
            Class.forName("net.Indyuce.mmoitems.stat.type.ItemStat");
            Class.forName("net.Indyuce.mmoitems.stat.data.type.StatData");
            Class.forName("net.Indyuce.mmoitems.stat.data.DoubleData");
            Class.forName("net.Indyuce.mmoitems.api.item.build.ItemStackBuilder");
            Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // MMOItems not installed, incompatible version, or class dependency missing
        }
        AVAILABLE = available;
    }

    private MMOItemsIntegration() {
    }

    // -------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------

    /**
     * @return {@code true} if MMOItems is installed and its API classes are resolvable
     */
    public static boolean isAvailable() {
        // Always check the thorough Class.forName check first.
        // If the classes couldn't be loaded, the integration is unavailable
        // regardless of whether Bukkit reports the plugin as enabled.
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isMMOItemsAvailable();
        }
        return true;
    }

    /**
     * Check whether a material string refers to an MMOItems custom item.
     * <p>
     * Unlike ItemsAdder/CraftEngine which use namespace:id format, MMOItems items
     * use a simple ID without namespace. Any string that does not contain a colon
     * and is not a valid Bukkit Material is considered a potential MMOItems ID
     * when MMOItems is installed.
     *
     * @param materialString the raw material string from config (e.g. {@code "custom_pickaxe"})
     * @return {@code true} if MMOItems is available and the string looks like an MMOItems item ID
     */
    public static boolean isMMOItemsId(final String materialString) {
        if (materialString == null || materialString.isBlank()) return false;
        if (!AVAILABLE) return false;
        if (materialString.contains(":")) return false;
        // If it's a valid Minecraft material, it's not MMOItems.
        return org.bukkit.Material.matchMaterial(materialString, false) == null;
    }

    /**
     * Check whether a Bukkit {@link ItemStack} is an MMOItems custom item (of any type).
     *
     * @param item the Bukkit ItemStack to check
     * @return {@code true} if MMOItems is available and the item is a custom MMOItems item
     */
    public static boolean isCustomItem(final ItemStack item) {
        if (!AVAILABLE || item == null || item.getType().isAir()) return false;
        try {
            final io.lumine.mythic.lib.api.item.NBTItem nbtItem =
                    io.lumine.mythic.lib.api.item.NBTItem.get(item);
            return hasText(nbtItem.getString("MMOITEMS_ITEM_TYPE"))
                    && hasText(nbtItem.getString("MMOITEMS_ITEM_ID"));
        } catch (final RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * Check whether a Bukkit {@link ItemStack} matches a specific MMOItems item ID.
     *
     * @param item       the Bukkit ItemStack to check (e.g. from a player's hand)
     * @param mmoItemsId the MMOItems item ID to match against (e.g. {@code "custom_pickaxe"})
     * @return {@code true} if MMOItems is available, the item is a custom MMOItems item,
     *         and its ID matches the given ID
     */
    public static boolean matchItem(final ItemStack item, final String mmoItemsId) {
        if (!AVAILABLE || item == null || item.getType().isAir()
                || mmoItemsId == null || mmoItemsId.isBlank()) return false;
        try {
            final io.lumine.mythic.lib.api.item.NBTItem nbtItem =
                    io.lumine.mythic.lib.api.item.NBTItem.get(item);
            final String type = nbtItem.getString("MMOITEMS_ITEM_TYPE");
            final String id = nbtItem.getString("MMOITEMS_ITEM_ID");
            return hasText(type) && hasText(id) && id.equalsIgnoreCase(mmoItemsId);
        } catch (final RuntimeException | LinkageError ex) {
            MMOBlockLogger.debug("[MMOItems] Failed to match item '" + mmoItemsId + "': " + ex.getMessage());
            return false;
        }
    }

    private static boolean hasText(final String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Get an {@link ItemStack} for an MMOItems custom item by its ID.
     * <p>
     * Searches across all registered MMOItems types to find the item by ID.
     *
     * @param mmoItemsId the MMOItems item ID (e.g. {@code "custom_drop"})
     * @return the ItemStack, or {@code null} if MMOItems is unavailable or the item does not exist
     */
    public static ItemStack getItemStack(final String mmoItemsId) {
        if (!AVAILABLE || mmoItemsId == null || mmoItemsId.isBlank()) return null;
        try {
            for (final net.Indyuce.mmoitems.api.Type type : net.Indyuce.mmoitems.MMOItems.plugin.getTypes().getAll()) {
                final ItemStack stack = net.Indyuce.mmoitems.MMOItems.plugin.getItem(type, mmoItemsId);
                if (stack != null) {
                    return stack;
                }
            }
        } catch (final Exception ex) {
            MMOBlockLogger.debug("[MMOItems] Failed to get item stack for '" + mmoItemsId + "': " + ex.getMessage());
        }
        return null;
    }

    /**
     * Get an {@link ItemStack} for an MMOItems custom item with a specific amount.
     *
     * @param mmoItemsId the MMOItems item ID (e.g. {@code "custom_drop"})
     * @param amount     the stack size
     * @return the ItemStack with the given amount, or {@code null} if unavailable
     */
    public static ItemStack getItemStack(final String mmoItemsId, final int amount) {
        final ItemStack stack = getItemStack(mmoItemsId);
        if (stack != null && amount > 0) {
            stack.setAmount(amount);
        }
        return stack;
    }

    /**
     * Apply (decrease) custom durability on an MMOItems custom item.
     * <p>
     * MMOItems items manage durability through their custom stat system.
     * This method follows the pattern from MMOItems' own API:
     * <ol>
     *   <li>Read max durability from {@code ItemStats.MAX_DURABILITY} stat</li>
     *   <li>Read current remaining durability from the {@code MMOITEMS_DURABILITY} NBT tag
     *       (defaults to max durability when absent)</li>
     *   <li>Subtract the decrease amount</li>
     *   <li>If remaining ≤ 0, reduce stack size; otherwise update via
     *       {@code MMOItem.setData(ItemStats.CUSTOM_DURABILITY, ...)} and rebuild the item</li>
     * </ol>
     * </p>
     *
     * @param item     the Bukkit ItemStack (must be an MMOItems custom item)
     * @param decrease the amount of durability to decrease (must be &gt; 0)
     * @return {@code true} if durability was applied, {@code false} if the item is not a
     *         custom MMOItems item or MMOItems is unavailable
     */
    public static boolean applyCustomDurability(final ItemStack item, final int decrease) {
        if (!AVAILABLE || item == null || decrease <= 0) return false;
        try {
            // Identify the MMOItems item using its NBT data (more reliable than
            // MMOItems.getType/ID which can fail on some server versions).
            final io.lumine.mythic.lib.api.item.NBTItem nbtItem =
                    io.lumine.mythic.lib.api.item.NBTItem.get(item);
            final String typeName = nbtItem.getString("MMOITEMS_ITEM_TYPE");
            final String id = nbtItem.getString("MMOITEMS_ITEM_ID");
            if (typeName == null || typeName.isEmpty()
                    || id == null || id.isEmpty()) return false;

            final net.Indyuce.mmoitems.api.Type type =
                    net.Indyuce.mmoitems.api.Type.get(typeName);
            if (type == null) return false;

            // 1. Get MMOItem object and read max durability from MAX_DURABILITY stat
            final net.Indyuce.mmoitems.api.item.mmoitem.MMOItem mmoItem =
                    net.Indyuce.mmoitems.MMOItems.plugin.getMMOItem(type, id);
            if (mmoItem == null) return false;

            int maxDurability = 0;
            final net.Indyuce.mmoitems.stat.type.ItemStat maxDurabilityStat =
                    net.Indyuce.mmoitems.ItemStats.MAX_DURABILITY;
            if (mmoItem.hasData(maxDurabilityStat)) {
                // Use Object to avoid forcing the JVM to resolve StatData/DoubleData
                // at method-entry time when MMOItems version differs from compile target.
                final Object durabilityData = mmoItem.getData(maxDurabilityStat);
                if (durabilityData instanceof net.Indyuce.mmoitems.stat.data.DoubleData doubleData) {
                    maxDurability = (int) doubleData.getValue();
                }
            }

            // 2. If MAX_DURABILITY stat has no data, fall back to vanilla
            if (maxDurability <= 0) {
                maxDurability = item.getType().getMaxDurability();
            }
            if (maxDurability <= 0) {
                // No durability system found — just reduce the stack by one
                item.setAmount(Math.max(0, item.getAmount() - 1));
                return true;
            }

            // 3. Read current REMAINING durability from NBT.
            //    MMOItems tracks REMAINING durability (not consumed damage).
            //    When no tag exists, default to maxDurability (fresh item).
            int currentRemaining = nbtItem.hasTag("MMOITEMS_DURABILITY")
                    ? nbtItem.getInteger("MMOITEMS_DURABILITY")
                    : maxDurability;

            // 4. Calculate new remaining durability (subtract, don't add)
            final int newRemaining = currentRemaining - decrease;

            if (newRemaining <= 0) {
                // Item broke — reduce stack size
                item.setAmount(Math.max(0, item.getAmount() - 1));
            } else {
                // Use MMOItems API to update durability and rebuild the item
                mmoItem.setData(
                        net.Indyuce.mmoitems.ItemStats.CUSTOM_DURABILITY,
                        new net.Indyuce.mmoitems.stat.data.DoubleData(newRemaining)
                );
                final net.Indyuce.mmoitems.api.item.build.ItemStackBuilder builder =
                        new net.Indyuce.mmoitems.api.item.build.ItemStackBuilder(mmoItem);
                final ItemStack rebuilt = builder.build();
                // Apply rebuilt item's meta to the original reference
                item.setItemMeta(rebuilt.getItemMeta());
            }
            return true;
        } catch (final Throwable ex) {
            // Catch Throwable (not just Exception) to safely handle NoClassDefFoundError
            // and LinkageError that MMOItems version mismatches can trigger.
            MMOBlockLogger.debug("[MMOItems] Failed to apply durability: " + ex.getMessage());
            return false;
        }
    }
}
