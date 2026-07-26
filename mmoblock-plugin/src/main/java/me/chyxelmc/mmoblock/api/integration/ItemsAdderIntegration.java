package me.chyxelmc.mmoblock.api.integration;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * Integration layer for <a href="https://itemsadder.devs.beer/">ItemsAdder</a>.
 * <p>
 * Uses direct ItemsAdder API references guarded by a static availability check.
 * If ItemsAdder is not installed the methods are no-ops and the class loads safely
 * because the JVM resolves class references lazily.
 * </p>
 * <p>
 * Provides utilities for resolving custom item stacks, matching held items against
 * ItemsAdder namespaced IDs, managing custom durability, and placing/removing custom
 * blocks in the world.
 * </p>
 */
@SuppressWarnings("deprecation")
public final class ItemsAdderIntegration {

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
            // ItemsAdder not installed or incompatible
        }
        AVAILABLE = available;
    }

    private ItemsAdderIntegration() {
    }

    // -------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------

    /**
     * @return {@code true} if ItemsAdder is installed and its API classes are resolvable
     */
    public static boolean isAvailable() {
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isItemsAdderAvailable();
        }
        return true;
    }

    /**
     * Check whether a material string refers to an ItemsAdder custom item.
     * <p>
     * Any string with a namespace prefix (contains {@code :}) that is
     * <b>not</b> a valid {@link org.bukkit.Material} is treated as a
     * potential ItemsAdder ID. This allows arbitrary namespaces such as
     * {@code itemsadder:iron_pick}, {@code mmoblock:platina_ore}, etc.
     *
     * @param materialString the raw material string from config (e.g. {@code "itemsadder:iron_pick"})
     * @return {@code true} if the string looks like an ItemsAdder namespaced ID
     */
    public static boolean isItemsAdderId(final String materialString) {
        if (materialString == null || materialString.isBlank()) return false;
        if (!materialString.contains(":")) return false;
        // If it's a valid Minecraft material (e.g. "minecraft:stone"), it's not ItemsAdder.
        return org.bukkit.Material.matchMaterial(materialString, false) == null;
    }

    /**
     * Strip the namespace prefix from a namespaced ID, returning just the item/block name.
     * <p>
     * For example, both {@code "itemsadder:iron_pick"} and {@code "mmoblock:platina_ore"}
     * become {@code "iron_pick"} and {@code "platina_ore"} respectively.
     *
     * @param namespacedId the full namespaced ID (e.g. {@code "itemsadder:iron_pick"})
     * @return the item name without prefix, or the original string if no colon is present
     */
    public static String stripPrefix(final String namespacedId) {
        if (namespacedId == null) return null;
        final int colon = namespacedId.indexOf(':');
        if (colon < 0) return namespacedId;
        return namespacedId.substring(colon + 1);
    }

    /**
     * Get an {@link ItemStack} for an ItemsAdder custom item by its namespaced ID.
     *
     * @param itemsAdderId the namespaced item ID (e.g. {@code "mmoblock:platina_ore"} or just {@code "platina_ore"})
     * @return the ItemStack, or {@code null} if ItemsAdder is unavailable or the item does not exist
     */
    public static ItemStack getItemStack(final String itemsAdderId) {
        if (!AVAILABLE || itemsAdderId == null || itemsAdderId.isBlank()) return null;
        try {
            // Try the full namespaced string first (e.g. "mmoblock:platina_ore"),
            // then fall back to the stripped id (e.g. "platina_ore").
            final String stripped = stripPrefix(itemsAdderId);
            Object customStack = dev.lone.itemsadder.api.CustomStack.getInstance(itemsAdderId);
            if (customStack == null && !itemsAdderId.equals(stripped)) {
                customStack = dev.lone.itemsadder.api.CustomStack.getInstance(stripped);
            }
            if (customStack != null) {
                return ((dev.lone.itemsadder.api.CustomStack) customStack).getItemStack();
            }
        } catch (final Exception ex) {
            MMOBlockLogger.debug("[ItemsAdder] Failed to get item stack for '" + itemsAdderId + "': " + ex.getMessage());
        }
        return null;
    }

    /**
     * Get an {@link ItemStack} for an ItemsAdder custom item with a specific amount.
     *
     * @param itemsAdderId the namespaced item ID (e.g. {@code "itemsadder:iron_pick"})
     * @param amount       the stack size
     * @return the ItemStack with the given amount, or {@code null} if unavailable
     */
    public static ItemStack getItemStack(final String itemsAdderId, final int amount) {
        final ItemStack stack = getItemStack(itemsAdderId);
        if (stack != null && amount > 0) {
            stack.setAmount(amount);
        }
        return stack;
    }

    /**
     * Check whether a Bukkit {@link ItemStack} is an ItemsAdder custom item (of any type).
     *
     * @param item the Bukkit ItemStack to check
     * @return {@code true} if ItemsAdder is available and the item is a custom ItemsAdder item
     */
    public static boolean isCustomItem(final ItemStack item) {
        if (!AVAILABLE || item == null || item.getType().isAir()) return false;
        try {
            return dev.lone.itemsadder.api.CustomStack.byItemStack(item) != null;
        } catch (final Exception ignored) {
            try {
                return dev.lone.itemsadder.api.ItemsAdder.isCustomItem(item);
            } catch (final Exception ignored2) {
                return false;
            }
        }
    }

    /**
     * Check whether a Bukkit {@link ItemStack} matches a specific ItemsAdder namespaced ID.
     *
     * @param item         the Bukkit ItemStack to check (e.g. from a player's hand)
     * @param itemsAdderId the namespaced item ID to match against (e.g. {@code "mmoblock:platina_pickaxe"})
     * @return {@code true} if ItemsAdder is available, the item is a custom ItemsAdder item,
     *         and its config name matches the given ID
     */
    public static boolean matchItem(final ItemStack item, final String itemsAdderId) {
        if (!AVAILABLE || item == null || itemsAdderId == null || itemsAdderId.isBlank()) return false;
        try {
            final String stripped = stripPrefix(itemsAdderId);
            // Try the modern API first: CustomStack.byItemStack
            final Object customStack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (customStack != null) {
                final String stackId = ((dev.lone.itemsadder.api.CustomStack) customStack).getId();
                // Compare against both the full namespaced string (e.g. "mmoblock:platina_pickaxe")
                // and the stripped id (e.g. "platina_pickaxe") for maximum compatibility.
                if (itemsAdderId.equalsIgnoreCase(stackId) || stripped.equalsIgnoreCase(stackId)) {
                    return true;
                }
                // Also try matching with the namespace prefixed if stackId doesn't have one
                if (!stackId.contains(":")) {
                    final String prefixed = stripped + ":" + stackId;
                    if (itemsAdderId.equalsIgnoreCase(prefixed)) return true;
                }
            }
            // Fallback: use the legacy ItemsAdder.matchCustomItemName utility
            try {
                if (dev.lone.itemsadder.api.ItemsAdder.matchCustomItemName(item, stripped)) return true;
                if (!itemsAdderId.equals(stripped)) {
                    return dev.lone.itemsadder.api.ItemsAdder.matchCustomItemName(item, itemsAdderId);
                }
            } catch (final Exception ignored) {
                // Fall through
            }
            return false;
        } catch (final Exception ex) {
            MMOBlockLogger.debug("[ItemsAdder] Failed to match item '" + itemsAdderId + "': " + ex.getMessage());
            return false;
        }
    }

    /**
     * Apply (decrease) custom durability on an ItemsAdder custom item.
     * <p>
     * Uses {@code ItemsAdder.setCustomItemDurability()} to write the new durability value.
     * If the durability drops to or below zero, the item amount is reduced by one.
     * </p>
     *
     * @param item    the Bukkit ItemStack (must be an ItemsAdder custom item)
     * @param decrease the amount of durability to decrease (must be &gt; 0)
     * @return {@code true} if durability was applied, {@code false} if the item is not a
     *         custom ItemsAdder item or ItemsAdder is unavailable
     */
    public static boolean applyCustomDurability(final ItemStack item, final int decrease) {
        if (!AVAILABLE || item == null || decrease <= 0) return false;
        try {
            // Check if this is actually an ItemsAdder custom item
            final Object customStack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (customStack == null) return false;

            final int currentDurability = dev.lone.itemsadder.api.ItemsAdder.getCustomItemDurability(item);
            final int maxDurability = dev.lone.itemsadder.api.ItemsAdder.getCustomItemMaxDurability(item);
            if (maxDurability <= 0) return false;

            final int newDurability = currentDurability - decrease;
            if (newDurability <= 0) {
                // Item broke — reduce stack size
                item.setAmount(Math.max(0, item.getAmount() - 1));
            } else {
                dev.lone.itemsadder.api.ItemsAdder.setCustomItemDurability(item, newDurability);
            }
            return true;
        } catch (final Throwable ex) {
            // Catch Throwable to safely handle NoClassDefFoundError/LinkageError
            MMOBlockLogger.debug("[ItemsAdder] Failed to apply durability: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Place an ItemsAdder custom block at a specific location in the world.
     * <p>
     * Uses the static {@code CustomBlock.place(namespacedId, location)} method.
     * If the block at the location is already an ItemsAdder custom block of the
     * same type, the placement is skipped entirely to avoid an unnecessary
     * remove+re-place cycle (which could cause visual flickering or data loss
     * during config reload).
     * </p>
     *
     * @param location         the block location to place at
     * @param itemsAdderBlockId the namespaced block ID (e.g. {@code "mmoblock:platina_ore"})
     * @return {@code true} if the block was placed successfully (or was already in place), {@code false} otherwise
     */
    public static boolean placeBlock(final Location location, final String itemsAdderBlockId) {
        if (!AVAILABLE || location == null || itemsAdderBlockId == null || itemsAdderBlockId.isBlank()) return false;
        try {
            // Skip placement if the block is already the same ItemsAdder custom block
            final org.bukkit.block.Block bukkitBlock = location.getBlock();
            final Object existing = dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(bukkitBlock);
            if (existing != null) {
                final String existingId = ((dev.lone.itemsadder.api.CustomBlock) existing).getId();
                final String stripped = stripPrefix(itemsAdderBlockId);
                if (itemsAdderBlockId.equalsIgnoreCase(existingId) || stripped.equalsIgnoreCase(existingId)) {
                    return true; // Already placed, nothing to do
                }
            }

            // Try the static place method with full namespaced string first,
            // then fall back to stripped id
            final String stripped = stripPrefix(itemsAdderBlockId);
            Object placed = dev.lone.itemsadder.api.CustomBlock.place(itemsAdderBlockId, location);
            if (placed == null && !itemsAdderBlockId.equals(stripped)) {
                placed = dev.lone.itemsadder.api.CustomBlock.place(stripped, location);
            }
            if (placed == null) {
                MMOBlockLogger.debug("[ItemsAdder] Custom block '" + itemsAdderBlockId + "' not found in registry.");
                return false;
            }
            return true;
        } catch (final Exception ex) {
            MMOBlockLogger.debug("[ItemsAdder] Failed to place custom block '" + itemsAdderBlockId + "': " + ex.getMessage());
            return false;
        }
    }

    /**
     * Remove a custom block from the world and from ItemsAdder region data.
     * <p>
     * Uses {@code CustomBlock.remove(Location)} which properly cleans up both the
     * visual block and ItemsAdder's internal persistence. Falls back to setting the
     * block to air if ItemsAdder is not available or the API fails.
     * </p>
     *
     * @param location the block location to clear
     */
    public static void removeBlock(final Location location) {
        if (location == null) return;
        if (AVAILABLE) {
            try {
                dev.lone.itemsadder.api.CustomBlock.remove(location);
                return;
            } catch (final Exception ignored) {
                // Fall through to setType(AIR) fallback
            }
        }
        try {
            location.getBlock().setType(org.bukkit.Material.AIR);
        } catch (final Exception ignored) {
        // expected - reflection fallback
        }
    }

    /**
     * Check whether the block at a given location is an ItemsAdder custom block.
     *
     * @param location the block location to check
     * @return {@code true} if ItemsAdder is available and the block at that location is a custom block
     */
    public static boolean isCustomBlock(final Location location) {
        if (!AVAILABLE || location == null) return false;
        try {
            return dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(location.getBlock()) != null;
        } catch (final Exception ignored) {
            return false;
        }
    }
}
