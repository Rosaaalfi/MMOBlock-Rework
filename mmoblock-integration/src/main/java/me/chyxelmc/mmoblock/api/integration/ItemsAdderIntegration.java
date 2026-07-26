package me.chyxelmc.mmoblock.api.integration;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("deprecation")
public final class ItemsAdderIntegration {

    private static final boolean AVAILABLE;

    static {
        boolean available = false;
        try {
            Class.forName("dev.lone.itemsadder.api.CustomStack");
            available = true;
        } catch (final ReflectiveOperationException | LinkageError ignored) {
        }
        AVAILABLE = available;
    }

    private ItemsAdderIntegration() {
    }

    public static boolean isAvailable() {
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isItemsAdderAvailable();
        }
        return true;
    }

    public static boolean isItemsAdderId(final String materialString) {
        if (materialString == null || materialString.isBlank()) return false;
        if (!materialString.contains(":")) return false;
        return org.bukkit.Material.matchMaterial(materialString, false) == null;
    }

    public static String stripPrefix(final String namespacedId) {
        if (namespacedId == null) return null;
        final int colon = namespacedId.indexOf(':');
        if (colon < 0) return namespacedId;
        return namespacedId.substring(colon + 1);
    }

    public static ItemStack getItemStack(final String itemsAdderId) {
        if (!AVAILABLE || itemsAdderId == null || itemsAdderId.isBlank()) return null;
        try {
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

    public static ItemStack getItemStack(final String itemsAdderId, final int amount) {
        final ItemStack stack = getItemStack(itemsAdderId);
        if (stack != null && amount > 0) {
            stack.setAmount(amount);
        }
        return stack;
    }

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

    public static boolean matchItem(final ItemStack item, final String itemsAdderId) {
        if (!AVAILABLE || item == null || itemsAdderId == null || itemsAdderId.isBlank()) return false;
        try {
            final String stripped = stripPrefix(itemsAdderId);
            final Object customStack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (customStack != null) {
                final String stackId = ((dev.lone.itemsadder.api.CustomStack) customStack).getId();
                if (itemsAdderId.equalsIgnoreCase(stackId) || stripped.equalsIgnoreCase(stackId)) {
                    return true;
                }
                if (!stackId.contains(":")) {
                    final String prefixed = stripped + ":" + stackId;
                    if (itemsAdderId.equalsIgnoreCase(prefixed)) return true;
                }
            }
            try {
                if (dev.lone.itemsadder.api.ItemsAdder.matchCustomItemName(item, stripped)) return true;
                if (!itemsAdderId.equals(stripped)) {
                    return dev.lone.itemsadder.api.ItemsAdder.matchCustomItemName(item, itemsAdderId);
                }
            } catch (final Exception ignored) {
            }
            return false;
        } catch (final Exception ex) {
            MMOBlockLogger.debug("[ItemsAdder] Failed to match item '" + itemsAdderId + "': " + ex.getMessage());
            return false;
        }
    }

    public static boolean applyCustomDurability(final ItemStack item, final int decrease) {
        if (!AVAILABLE || item == null || decrease <= 0) return false;
        try {
            final Object customStack = dev.lone.itemsadder.api.CustomStack.byItemStack(item);
            if (customStack == null) return false;

            final int currentDurability = dev.lone.itemsadder.api.ItemsAdder.getCustomItemDurability(item);
            final int maxDurability = dev.lone.itemsadder.api.ItemsAdder.getCustomItemMaxDurability(item);
            if (maxDurability <= 0) return false;

            final int newDurability = currentDurability - decrease;
            if (newDurability <= 0) {
                item.setAmount(Math.max(0, item.getAmount() - 1));
            } else {
                dev.lone.itemsadder.api.ItemsAdder.setCustomItemDurability(item, newDurability);
            }
            return true;
        } catch (final Throwable ex) {
            MMOBlockLogger.debug("[ItemsAdder] Failed to apply durability: " + ex.getMessage());
            return false;
        }
    }

    public static boolean placeBlock(final Location location, final String itemsAdderBlockId) {
        if (!AVAILABLE || location == null || itemsAdderBlockId == null || itemsAdderBlockId.isBlank()) return false;
        try {
            final org.bukkit.block.Block bukkitBlock = location.getBlock();
            final Object existing = dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(bukkitBlock);
            if (existing != null) {
                final String existingId = ((dev.lone.itemsadder.api.CustomBlock) existing).getId();
                final String stripped = stripPrefix(itemsAdderBlockId);
                if (itemsAdderBlockId.equalsIgnoreCase(existingId) || stripped.equalsIgnoreCase(existingId)) {
                    return true;
                }
            }
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

    public static void removeBlock(final Location location) {
        if (location == null) return;
        if (AVAILABLE) {
            try {
                dev.lone.itemsadder.api.CustomBlock.remove(location);
                return;
            } catch (final Exception ignored) {
            }
        }
        try {
            location.getBlock().setType(org.bukkit.Material.AIR);
        } catch (final Exception e) {
            MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
        }
    }

    public static boolean isCustomBlock(final Location location) {
        if (!AVAILABLE || location == null) return false;
        try {
            return dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(location.getBlock()) != null;
        } catch (final Exception ignored) {
            return false;
        }
    }
}
