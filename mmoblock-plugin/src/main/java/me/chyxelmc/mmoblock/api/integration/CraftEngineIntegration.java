package me.chyxelmc.mmoblock.api.integration;  
  
import java.util.Map;

import me.chyxelmc.mmoblock.utils.DependencyChecker;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.Location;  
import org.bukkit.Material;  
import org.bukkit.block.Block;  
import org.bukkit.inventory.ItemStack;  
import org.bukkit.inventory.meta.Damageable;  
import org.bukkit.inventory.meta.ItemMeta;  
  
/**  
 * Integration layer for <a href="https://github.com/Xiao-MoMi/craft-engine">CraftEngine</a>.  
 * <p>  
 * Uses the stable {@code net.momirealms.craftengine.bukkit.api} package guarded by a  
 * static availability check. If CraftEngine is not installed the methods are no-ops  
 * and the class loads safely because the JVM resolves class references lazily.  
 * </p>  
 * <p>  
 * Provides utilities for resolving custom item stacks, matching held items against  
 * CraftEngine namespaced IDs, managing custom durability, and placing/removing custom  
 * blocks in the world.  
 * </p>  
 */  
public final class CraftEngineIntegration {  
  
    private static final boolean AVAILABLE;  
  
    static {  
        boolean available = false;  
        try {  
            Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");  
            Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks");
            available = true;  
        } catch (final ReflectiveOperationException | LinkageError ignored) {  
            MMOBlockLogger.debug("CraftEngine is not installed or incompatible.");
        }  
        AVAILABLE = available;  
    }  
  
    private CraftEngineIntegration() {  
    }  
  
    // -------------------------------------------------------------  
    // Public API  
    // -------------------------------------------------------------  
  
    /**  
     * @return {@code true} if CraftEngine is installed and its API classes are resolvable  
     */  
    public static boolean isAvailable() {  
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isCraftEngineAvailable();
        }
        return true;
    }  
  
    /**  
     * Check whether a material string refers to a CraftEngine custom item or block  
     * by looking it up in CraftEngine's registry.  
     * <p>  
     * Namespace is fully flexible — it can be anything like {@code craftengine:item},  
     * {@code default:item}, {@code mmoblock:item}, etc.  
     * <p>  
     * NOTE: For config loading, use {@link #isCraftEngineAvailable(String)} instead,  
     * which does NOT require registry lookup (prevents rejection during startup).  
     *  
     * @param materialString the raw material string from config  
     * @return {@code true} if the string is found in CraftEngine's registry  
     */  
    public static boolean isCraftEngineId(final String materialString) {  
        if (materialString == null || materialString.isBlank()) return false;  
        if (!materialString.contains(":")) return false;  
  
        // If it's a valid Minecraft material, it's not CraftEngine.  
        if (org.bukkit.Material.matchMaterial(materialString, false) != null) return false;  
  
        if (!AVAILABLE) return false;  
  
        try {
            if (resolveItemDefinition(materialString) != null) return true;
            if (resolveBlockKey(materialString) != null) return true;
        } catch (final Exception ignored) {  
            // Registry might not be loaded yet during startup  
        }  
  
        return false;  
    }  
  
    /**  
     * Check whether a material string SHOULD be treated as a CraftEngine ID during config loading.  
     * <p>  
     * This is more permissive than {@link #isCraftEngineId(String)} — it returns true for ANY  
     * string with {@code namespace:id} format that isn't a valid Minecraft material, as long as  
     * CraftEngine is installed. This prevents config rejection when CraftEngine's registry isn't  
     * ready yet during startup.  
     * <p>  
     * Namespace is fully flexible — it can be anything: {@code default:item}, {@code mmoblock:item},  
     * {@code craftengine:item}, etc.  
     * <p>  
     * ItemsAdder IDs should be checked BEFORE calling this method.  
     *  
     * @param materialString the raw material string from config  
     * @return {@code true} if CraftEngine is available and the string looks like a CraftEngine ID  
     */  
    public static boolean isCraftEngineAvailable(final String materialString) {  
        if (materialString == null || materialString.isBlank()) return false;  
        if (!materialString.contains(":")) return false;  
        if (!AVAILABLE) return false;  
        // If it's a valid Minecraft material, it's not CraftEngine  
        return org.bukkit.Material.matchMaterial(materialString, false) == null;  
    }  

    /**
     * Check whether a config value should be routed to CraftEngine before other
     * custom-item providers get a chance to claim generic namespaced IDs.
     */
    public static boolean isCraftEngineConfigId(final String materialString) {
        if (!isCraftEngineAvailable(materialString)) return false;
        if (isCraftEngineId(materialString)) return true;

        final String normalized = normalizeId(materialString);
        if (normalized == null) return false;
        final int colon = normalized.indexOf(':');
        if (colon < 1) return false;
        final String namespace = normalized.substring(0, colon);
        return "craftengine".equals(namespace) || "default".equals(namespace);
    }
  
    /**  
     * Strip the namespace prefix from a namespaced ID, returning just the item/block name.  
     *  
     * @param namespacedId the full namespaced ID (e.g. {@code "craftengine:custom_pickaxe"})  
     * @return the item name without prefix, or the original string if no colon is present  
     */  
    public static String stripPrefix(final String namespacedId) {  
        if (namespacedId == null) return null;  
        final int colon = namespacedId.indexOf(':');  
        if (colon < 0) return namespacedId;  
        return namespacedId.substring(colon + 1);  
    }  
  
    /**  
     * Get an {@link ItemStack} for a CraftEngine custom item by its namespaced ID.  
     *  
     * @param craftEngineId the namespaced item ID (e.g. {@code "craftengine:custom_pickaxe"})  
     * @return the ItemStack, or {@code null} if CraftEngine is unavailable or the item does not exist  
     */  
    public static ItemStack getItemStack(final String craftEngineId) {  
        if (!AVAILABLE || craftEngineId == null || craftEngineId.isBlank()) return null;  
        try {  
            final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition def =  
                    resolveItemDefinition(craftEngineId);
            if (def != null) {  
                return def.buildBukkitItem();  
            }  
            MMOBlockLogger.warning("[CraftEngine] Item not found: '" + craftEngineId  
                    + "'. Check that the item exists in CraftEngine with this exact namespace and ID.");  
        } catch (final Exception ex) {  
            MMOBlockLogger.warning("[CraftEngine] Failed to get item stack for '" + craftEngineId + "'", ex);  
        }  
        return null;  
    }  
  
    /**  
     * Get an {@link ItemStack} for a CraftEngine custom item with a specific amount.  
     */  
    public static ItemStack getItemStack(final String craftEngineId, final int amount) {  
        final ItemStack stack = getItemStack(craftEngineId);  
        if (stack != null && amount > 0) {  
            stack.setAmount(amount);  
        }  
        return stack;  
    }  
  
    /**  
     * Check whether a Bukkit {@link ItemStack} is a CraftEngine custom item (of any type).  
     */  
    public static boolean isCustomItem(final ItemStack item) {  
        if (!AVAILABLE || item == null || item.getType().isAir()) return false;  
        try {  
            return net.momirealms.craftengine.bukkit.api.CraftEngineItems.isCustomItem(item);  
        } catch (final Exception ignored) {  
            return false;  
        }  
    }  
  
    /**  
     * Check whether a Bukkit {@link ItemStack} matches a specific CraftEngine namespaced ID.  
     *  
     * @param item           the Bukkit ItemStack to check (e.g. from a player's hand)  
     * @param craftEngineId  the namespaced item ID to match against (e.g. {@code "craftengine:custom_pickaxe"})  
     * @return {@code true} if CraftEngine is available, the item is a custom CraftEngine item,  
     *         and its ID matches the given ID  
     */  
    public static boolean matchItem(final ItemStack item, final String craftEngineId) {  
        if (!AVAILABLE || item == null || item.getType().isAir()  
                || craftEngineId == null || craftEngineId.isBlank()) return false;  
        try {  
            final net.momirealms.craftengine.core.util.Key itemKey =  
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.getCustomItemId(item);  
            if (itemKey == null) {
                // Held item is not a CraftEngine custom item — normal when player
                // holds a vanilla item against a block configured with CraftEngine tools.
                return false;
            }  
  
            if (matchesConfiguredId(itemKey, craftEngineId)) {
                return true;  
            }  

            /*
            ** LOGGER.warning("[CraftEngine] Item mismatch: configured='" + craftEngineId
            **        + "), held item='" + itemKey.asString()
            **        + "' (namespace=" + itemKey.namespace() + ", value=" + itemKey.value() + ")"
            **        + ", loadedItems=" + loadedItemIds());
             */
            return false;  
        } catch (final Exception ex) {  
            MMOBlockLogger.warning("[CraftEngine] Failed to match item '" + craftEngineId + "'", ex);  
            return false;  
        }  
    }  
  
    /**
     * Apply (decrease) durability on a CraftEngine custom item.
     * <p>
     * CraftEngine custom items use the vanilla damage system ({@link Damageable}),
     * but the max damage comes from CraftEngine's item definition (e.g.
     * {@code data.max_damage}), not from the vanilla template material.
     */
    public static boolean applyCustomDurability(final ItemStack item, final int decrease) {
        if (!AVAILABLE || item == null || decrease <= 0) return false;
        try {
            if (!net.momirealms.craftengine.bukkit.api.CraftEngineItems.isCustomItem(item)) return false;

            final ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                final int maxDamage = getCraftEngineMaxDamage(item);
                if (maxDamage <= 0) {
                    return true;
                }
                final int currentDamage = damageable.getDamage();
                final int newDamage = currentDamage + decrease;
                if (newDamage >= maxDamage) {
                    item.setAmount(Math.max(0, item.getAmount() - 1));
                } else {
                    damageable.setDamage(newDamage);
                    item.setItemMeta(meta);
                }
                return true;
            }
            item.setAmount(Math.max(0, item.getAmount() - 1));
            return true;
        } catch (final Throwable ex) {
            // Catch Throwable to safely handle NoClassDefFoundError/LinkageError
            MMOBlockLogger.debug("[CraftEngine] Failed to apply durability: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Get the max damage for a CraftEngine custom item.
     * <p>
     * The primary mechanism is wrapping the Bukkit ItemStack into CraftEngine's
     * {@code BukkitItem} via {@code BukkitItemManager.wrap()} and reading the
     * {@code maxDamage()} method directly from the {@code Item} interface.
     * This works on all supported server versions because CraftEngine stores
     * the configured {@code data.max_damage} on the built item.
     * <p>
     * Falls back to the vanilla material's max durability if CraftEngine's
     * value cannot be resolved.
     */
    private static int getCraftEngineMaxDamage(final ItemStack item) {
        try {
            // ----- Primary strategy: Wrap into CraftEngine BukkitItem and call maxDamage() -----
            // BukkitItemManager.instance().wrap(item) returns a BukkitItem which implements
            // the Item interface. Item.maxDamage() returns the configured max_damage value.
            final net.momirealms.craftengine.bukkit.item.BukkitItem ceItem =
                    net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(item);
            return ceItem.maxDamage();
        } catch (final Exception ignored) {
        }

        // ----- Fallback strategies -----
        try {
            final net.momirealms.craftengine.core.util.Key itemKey =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.getCustomItemId(item);
            if (itemKey == null) return item.getType().getMaxDurability();

            final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition definition =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(itemKey);
            final net.momirealms.craftengine.core.item.ItemDefinition coreDef =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().get(itemKey);

            // Fallback 1: Paper 1.20.5+ component system
            int result = readMaxDamageFromItemMeta(item);
            if (result > 0) return result;

            // Fallback 2: Build fresh ItemStack from definition and read components
            if (definition != null) {
                result = readMaxDamageFromBuiltDefinition(definition);
                if (result > 0) return result;
            }

            // Fallback 3: Data/settings map
            for (final Object def : new Object[]{definition, coreDef}) {
                if (def == null) continue;
                result = readMaxDamageFromDataMap(def);
                if (result > 0) return result;
            }

            // Fallback 4: Brute-force method scan
            for (final Object def : new Object[]{definition, coreDef}) {
                if (def == null) continue;
                result = bruteForceMaxDamage(def);
                if (result > 0) return result;
            }
        } catch (final Exception ignored) {
        }
        return item.getType().getMaxDurability();
    }

    /**
     * Build a fresh {@link ItemStack} from the definition and attempt to read its
     * max damage. CraftEngine applies all item processors (including max_damage)
     * during {@code buildBukkitItem()}, so the resulting ItemStack should have the
     * correct max damage component set.
     */
    private static int readMaxDamageFromBuiltDefinition(
            final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition definition
    ) {
        try {
            final ItemStack built = definition.buildBukkitItem();
            return readMaxDamageFromItemMeta(built);
        } catch (final Exception ignored) {
            return -1;
        }
    }

    /**
     * Attempt to read {@code max_damage} by calling {@code data()}, {@code settings()},
     * or {@code itemData()} on the definition and extracting the value from the returned map.
     * This works across CraftEngine versions where the dedicated {@code maxDamage()} method
     * may not exist, but the item's raw config data is still accessible.
     */
    @SuppressWarnings("unchecked")
    private static int readMaxDamageFromDataMap(final Object definition) {
        // Try common method names that return item data / settings
        for (final String methodName : new String[]{"data", "settings", "itemData", "itemSettings", "getData"}) {
            java.lang.reflect.Method method = null;
            try {
                method = definition.getClass().getMethod(methodName);
            } catch (final NoSuchMethodException ignored) {
                continue;
            }
            try {
                final Object raw = method.invoke(definition);
                if (raw instanceof java.util.Map) {
                    final java.util.Map<Object, Object> map = (java.util.Map<Object, Object>) raw;
                    // Try both key naming conventions used by CraftEngine configs
                    final Object val = map.get("max_damage");
                    if (val instanceof Number num) {
                        final int dmg = num.intValue();
                        if (dmg > 0) return dmg;
                    }
                    // Try alternate key name
                    final Object altVal = map.get("maxDamage");
                    if (altVal instanceof Number num) {
                        final int dmg = num.intValue();
                        if (dmg > 0) return dmg;
                    }
                }
            } catch (final Exception ignored) {
            }
        }
        return -1;
    }


    /**
     * Read max damage from the ItemStack's own meta data via the Paper 1.20.5+
     * component system. If CraftEngine properly sets the {@code max_damage} component
     * on the item (which it does when {@code data.max_damage} is configured),
     * {@code ItemMeta.getMaxDamage()} will return the correct value.
     */
    private static int readMaxDamageFromItemMeta(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        try {
            // Paper 1.20.5+: ItemMeta#hasMaxDamage() returns whether a custom max damage is set
            final java.lang.reflect.Method hasMax = meta.getClass().getMethod("hasMaxDamage");
            if (!Boolean.TRUE.equals(hasMax.invoke(meta))) return -1;
            final java.lang.reflect.Method getMax = meta.getClass().getMethod("getMaxDamage");
            final Object result = getMax.invoke(meta);
            if (result instanceof Integer val && val > 0) return val;
        } catch (final NoSuchMethodException ignored) {
            // Pre-1.20.5 server — component system not available
        } catch (final Exception ignored) {
        }
        return -1;
    }

    /**
     * Last-resort brute-force: iterate every public method on the definition object
     * and try to find one whose name contains "max" + ("damage" / "dura") and returns
     * a numeric type or {@code OptionalInt}.
     * <p>
     * This handles unknown CraftEngine versions where the method has a non-standard name.
     * It explicitly skips {@code getMaxDurability()} which returns the material's vanilla
     * max durability instead of the CraftEngine configured value.
     */
    private static int bruteForceMaxDamage(final Object definition) {
        for (final java.lang.reflect.Method method : definition.getClass().getMethods()) {
            final String mName = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!mName.contains("max") && !mName.contains("dura")) continue;
            // Skip vanilla getMaxDurability — that returns the TYPE's durability, not CraftEngine's
            if ("getMaxDurability".equalsIgnoreCase(method.getName())) continue;
            try {
                final Object result = method.invoke(definition);
                if (result instanceof Integer val && val > 0) return val;
                if (result instanceof java.util.OptionalInt opt && opt.isPresent()) return opt.getAsInt();
                if (result instanceof java.util.Optional<?> opt) {
                    final Object inner = opt.orElse(null);
                    if (inner instanceof Integer val && val > 0) return val;
                }
                if (result instanceof Number num) {
                    final int val = num.intValue();
                    if (val > 0) return val;
                }
            } catch (final Exception ignored) {
            }
        }
        return -1;
    }
  
    /**  
     * Place a CraftEngine custom block at a specific location.  
     * <p>
     * If the location already has a CraftEngine custom block of the same type,
     * the placement is skipped (returns {@code true}) to avoid redundant warnings
     * on server restart or chunk reload where the block is already persistent
     * in the world.
     * </p>
     */  
    public static boolean placeBlock(final Location location, final String craftEngineBlockId) {  
        if (!AVAILABLE || location == null || craftEngineBlockId == null || craftEngineBlockId.isBlank()) return false;  
        try {  
            final net.momirealms.craftengine.core.util.Key blockKey = resolveBlockKey(craftEngineBlockId);
            if (blockKey == null) {
                MMOBlockLogger.warning("[CraftEngine] Block not found: '" + craftEngineBlockId
                        + "'. Check that the block exists in CraftEngine with this namespace and ID.");
                return false;
            }

            // If a CraftEngine custom block already exists at this location, skip placement.
            // This prevents spurious warnings on server restart / chunk reload when the
            // block is still persistent in the world from the previous session.
            if (net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.isCustomBlock(location.getBlock())) {
                return true;
            }
  
            final boolean success = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(  
                    location, blockKey, true);  
            if (!success) {  
                MMOBlockLogger.warning("[CraftEngine] Failed to place block '" + craftEngineBlockId  
                        + "'. Check that the block exists in CraftEngine's registry (namespace:id must match exactly).");  
            }  
            return success;  
        } catch (final Exception ex) {  
            MMOBlockLogger.warning("[CraftEngine] Failed to place custom block '" + craftEngineBlockId + "'", ex);  
            return false;  
        }  
    }  
  
    /**  
     * Remove a CraftEngine custom block from the world.  
     */  
    public static void removeBlock(final Location location) {  
        if (location == null) return;  
        if (AVAILABLE) {  
            try {  
                final Block block = location.getBlock();  
                if (net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.isCustomBlock(block)) {  
                    net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.remove(block);  
                    return;  
                }  
            } catch (final Exception ignored) {  
            }  
        }  
        try {  
            final Block block = location.getBlock();  
            if (block.getType() != Material.AIR) {  
                block.setType(Material.AIR);  
            }  
        } catch (final Exception ignored) {  
        }  
    }  
  
    /**  
     * Check whether the block at a given location is a CraftEngine custom block.  
     */  
    public static boolean isCustomBlock(final Location location) {  
        if (!AVAILABLE || location == null) return false;  
        try {  
            return net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.isCustomBlock(location.getBlock());  
        } catch (final Exception ignored) {  
            return false;  
        }  
    }  

    private static net.momirealms.craftengine.bukkit.item.BukkitItemDefinition resolveItemDefinition(final String rawId) {
        final String normalized = normalizeId(rawId);
        if (normalized == null) return null;

        net.momirealms.craftengine.bukkit.item.BukkitItemDefinition definition =
                net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(normalized);
        if (definition != null) return definition;

        final String path = stripPrefix(normalized);
        if (!path.equals(normalized)) {
            definition = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(path);
            if (definition != null) return definition;
        }

        for (final Map.Entry<net.momirealms.craftengine.core.util.Key, net.momirealms.craftengine.core.item.ItemDefinition> entry
                : net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().entrySet()) {
            final net.momirealms.craftengine.core.util.Key key = entry.getKey();
            if (matchesConfiguredId(key, normalized)) {
                return net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(key);
            }
        }
        return null;
    }

    private static net.momirealms.craftengine.core.util.Key resolveBlockKey(final String rawId) {
        final String normalized = normalizeId(rawId);
        if (normalized == null) return null;

        final net.momirealms.craftengine.core.util.Key exactKey =
                net.momirealms.craftengine.core.util.Key.of(normalized);
        if (net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.byId(exactKey) != null) {
            return exactKey;
        }

        final String path = stripPrefix(normalized);
        for (final net.momirealms.craftengine.core.util.Key key
                : net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.loadedBlocks().keySet()) {
            if (key.asString().equalsIgnoreCase(normalized) || key.value().equalsIgnoreCase(path)) {
                return key;
            }
        }
        return null;
    }

    private static boolean matchesConfiguredId(
            final net.momirealms.craftengine.core.util.Key actualKey,
            final String configuredId
    ) {
        final String normalized = normalizeId(configuredId);
        if (actualKey == null || normalized == null) return false;
        if (actualKey.asString().equalsIgnoreCase(normalized)) return true;
        return actualKey.value().equalsIgnoreCase(stripPrefix(normalized));
    }

    private static String normalizeId(final String rawId) {
        if (rawId == null) return null;
        final String trimmed = rawId.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private static String loadedItemIds() {
        if (!AVAILABLE) return "[]";
        try {
            return net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().keySet().stream()
                    .map(net.momirealms.craftengine.core.util.Key::asString)
                    .sorted()
                    .limit(30)
                    .toList()
                    .toString();
        } catch (final Exception ex) {
            return "[unavailable: " + ex.getMessage() + "]";
        }
    }
}
