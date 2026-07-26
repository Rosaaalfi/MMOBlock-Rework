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

    public static boolean isAvailable() {
        if (!AVAILABLE) return false;
        if (DependencyChecker.isInitialized()) {
            return DependencyChecker.isCraftEngineAvailable();
        }
        return true;
    }

    public static boolean isCraftEngineId(final String materialString) {
        if (materialString == null || materialString.isBlank()) return false;
        if (!materialString.contains(":")) return false;
        if (org.bukkit.Material.matchMaterial(materialString, false) != null) return false;
        if (!AVAILABLE) return false;
        try {
            if (resolveItemDefinition(materialString) != null) return true;
            if (resolveBlockKey(materialString) != null) return true;
        } catch (final Exception ignored) {
        }
        return false;
    }

    public static boolean isCraftEngineAvailable(final String materialString) {
        if (materialString == null || materialString.isBlank()) return false;
        if (!materialString.contains(":")) return false;
        if (!AVAILABLE) return false;
        return org.bukkit.Material.matchMaterial(materialString, false) == null;
    }

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

    public static String stripPrefix(final String namespacedId) {
        if (namespacedId == null) return null;
        final int colon = namespacedId.indexOf(':');
        if (colon < 0) return namespacedId;
        return namespacedId.substring(colon + 1);
    }

    public static ItemStack getItemStack(final String craftEngineId) {
        if (!AVAILABLE || craftEngineId == null || craftEngineId.isBlank()) return null;
        try {
            final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition def = resolveItemDefinition(craftEngineId);
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

    public static ItemStack getItemStack(final String craftEngineId, final int amount) {
        final ItemStack stack = getItemStack(craftEngineId);
        if (stack != null && amount > 0) {
            stack.setAmount(amount);
        }
        return stack;
    }

    public static boolean isCustomItem(final ItemStack item) {
        if (!AVAILABLE || item == null || item.getType().isAir()) return false;
        try {
            return net.momirealms.craftengine.bukkit.api.CraftEngineItems.isCustomItem(item);
        } catch (final Exception ignored) {
            return false;
        }
    }

    public static boolean matchItem(final ItemStack item, final String craftEngineId) {
        if (!AVAILABLE || item == null || item.getType().isAir()
                || craftEngineId == null || craftEngineId.isBlank()) return false;
        try {
            final net.momirealms.craftengine.core.util.Key itemKey =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.getCustomItemId(item);
            if (itemKey == null) return false;
            if (matchesConfiguredId(itemKey, craftEngineId)) return true;
            return false;
        } catch (final Exception ex) {
            MMOBlockLogger.warning("[CraftEngine] Failed to match item '" + craftEngineId + "'", ex);
            return false;
        }
    }

    public static boolean applyCustomDurability(final ItemStack item, final int decrease) {
        if (!AVAILABLE || item == null || decrease <= 0) return false;
        try {
            if (!net.momirealms.craftengine.bukkit.api.CraftEngineItems.isCustomItem(item)) return false;
            final ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                final int maxDamage = getCraftEngineMaxDamage(item);
                if (maxDamage <= 0) return true;
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
            MMOBlockLogger.debug("[CraftEngine] Failed to apply durability: " + ex.getMessage());
            return false;
        }
    }

    private static int getCraftEngineMaxDamage(final ItemStack item) {
        try {
            final net.momirealms.craftengine.bukkit.item.BukkitItem ceItem =
                    net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(item);
            return ceItem.maxDamage();
        } catch (final Exception ignored) {
        }
        try {
            final net.momirealms.craftengine.core.util.Key itemKey =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.getCustomItemId(item);
            if (itemKey == null) return item.getType().getMaxDurability();
            int result = readMaxDamageFromItemMeta(item);
            if (result > 0) return result;
            final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition definition =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(itemKey);
            if (definition != null) {
                result = readMaxDamageFromBuiltDefinition(definition);
                if (result > 0) return result;
            }
            final net.momirealms.craftengine.core.item.ItemDefinition coreDef =
                    net.momirealms.craftengine.bukkit.api.CraftEngineItems.loadedItems().get(itemKey);
            for (final Object def : new Object[]{definition, coreDef}) {
                if (def == null) continue;
                result = readMaxDamageFromDataMap(def);
                if (result > 0) return result;
            }
            for (final Object def : new Object[]{definition, coreDef}) {
                if (def == null) continue;
                result = bruteForceMaxDamage(def);
                if (result > 0) return result;
            }
        } catch (final Exception ignored) {
        }
        return item.getType().getMaxDurability();
    }

    private static int readMaxDamageFromBuiltDefinition(final net.momirealms.craftengine.bukkit.item.BukkitItemDefinition definition) {
        try {
            final ItemStack built = definition.buildBukkitItem();
            return readMaxDamageFromItemMeta(built);
        } catch (final Exception ignored) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static int readMaxDamageFromDataMap(final Object definition) {
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
                    final Object val = map.get("max_damage");
                    if (val instanceof Number num) {
                        final int dmg = num.intValue();
                        if (dmg > 0) return dmg;
                    }
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

    private static int readMaxDamageFromItemMeta(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        try {
            final java.lang.reflect.Method hasMax = meta.getClass().getMethod("hasMaxDamage");
            if (!Boolean.TRUE.equals(hasMax.invoke(meta))) return -1;
            final java.lang.reflect.Method getMax = meta.getClass().getMethod("getMaxDamage");
            final Object result = getMax.invoke(meta);
            if (result instanceof Integer val && val > 0) return val;
        } catch (final NoSuchMethodException ignored) {
        } catch (final Exception ignored) {
        }
        return -1;
    }

    private static int bruteForceMaxDamage(final Object definition) {
        for (final java.lang.reflect.Method method : definition.getClass().getMethods()) {
            final String mName = method.getName().toLowerCase(java.util.Locale.ROOT);
            if (!mName.contains("max") && !mName.contains("dura")) continue;
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

    public static boolean placeBlock(final Location location, final String craftEngineBlockId) {
        if (!AVAILABLE || location == null || craftEngineBlockId == null || craftEngineBlockId.isBlank()) return false;
        try {
            final net.momirealms.craftengine.core.util.Key blockKey = resolveBlockKey(craftEngineBlockId);
            if (blockKey == null) {
                MMOBlockLogger.warning("[CraftEngine] Block not found: '" + craftEngineBlockId
                        + "'. Check that the block exists in CraftEngine with this namespace and ID.");
                return false;
            }
            if (net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.isCustomBlock(location.getBlock())) {
                return true;
            }
            final boolean success = net.momirealms.craftengine.bukkit.api.CraftEngineBlocks.place(location, blockKey, true);
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

    private static boolean matchesConfiguredId(final net.momirealms.craftengine.core.util.Key actualKey, final String configuredId) {
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
}
