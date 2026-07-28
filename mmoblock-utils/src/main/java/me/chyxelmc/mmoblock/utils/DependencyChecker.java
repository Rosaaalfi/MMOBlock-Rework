package me.chyxelmc.mmoblock.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized soft-dependency checker for MMOBlock.
 * <p>
 * Runs at plugin startup ({@link JavaPlugin#onEnable()}) to verify which optional
 * plugins are installed and enabled. Results are exposed as static flags so that
 * all integration code can check them before calling third-party APIs.
 * </p>
 * <p>
 * If a dependency is missing or not enabled, a warning is logged and the
 * corresponding integration is gracefully disabled — no errors or crashes.
 * </p>
 * <p>
 * Checked plugins:
 * <ul>
 *   <li>MMOCore</li>
 *   <li>MMOItems</li>
 *   <li>ItemsAdder</li>
 *   <li>CraftEngine</li>
 *   <li>ModelEngine</li>
 *   <li>BetterModel</li>
 *   <li>Minepacks</li>
 * </ul>
 * </p>
 */
public final class DependencyChecker {

    // Status flags — true if the plugin is installed AND enabled
    private static boolean mmoCore;
    private static boolean mmoItems;
    private static boolean itemsAdder;
    private static boolean craftEngine;
    private static boolean modelEngine;
    private static boolean betterModel;
    private static boolean minepacks;

    private static boolean initialized;

    private DependencyChecker() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Check all soft dependencies and log their status.
     * <p>
     * Must be called early in {@link JavaPlugin#onEnable()}, ideally as the first
     * thing after saving the default config. After this call, the static getters
     * in this class and in each integration can be safely queried.
     * </p>
     *
     * @param plugin the MMOBlock plugin instance
     */
    public static void check(final JavaPlugin plugin) {
        if (initialized) {
            return; // already checked
        }

        final PluginManager pm = Bukkit.getPluginManager();

        mmoCore = pm.isPluginEnabled("MMOCore");
        mmoItems = pm.isPluginEnabled("MMOItems");
        itemsAdder = pm.isPluginEnabled("ItemsAdder");
        craftEngine = pm.isPluginEnabled("CraftEngine");
        modelEngine = pm.isPluginEnabled("ModelEngine");
        betterModel = pm.isPluginEnabled("BetterModel");
        minepacks = pm.isPluginEnabled("Minepacks");

        initialized = true;

        // Log status for each dependency in a consistent, readable format
        logAll();
    }

    // -------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------

    public static boolean isMMOCoreAvailable() {
        return mmoCore;
    }

    public static boolean isMMOItemsAvailable() {
        return mmoItems;
    }

    public static boolean isItemsAdderAvailable() {
        return itemsAdder;
    }

    public static boolean isCraftEngineAvailable() {
        return craftEngine;
    }

    public static boolean isModelEngineAvailable() {
        return modelEngine;
    }

    public static boolean isBetterModelAvailable() {
        return betterModel;
    }

    public static boolean isMinepacksAvailable() {
        return minepacks;
    }

    /**
     * @return {@code true} if the dependency check has been run at least once
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // -------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------

    private static void logAll() {
        // Use a LinkedHashMap to preserve insertion order in the log output
        final Map<String, Boolean> deps = new LinkedHashMap<>();
        deps.put("MMOCore", mmoCore);
        deps.put("MMOItems", mmoItems);
        deps.put("ItemsAdder", itemsAdder);
        deps.put("CraftEngine", craftEngine);
        deps.put("ModelEngine", modelEngine);
        deps.put("BetterModel", betterModel);

        for (final Map.Entry<String, Boolean> entry : deps.entrySet()) {
            final String name = entry.getKey();
            if (entry.getValue()) {
                MMOBlockLogger.info("integration.found", name + " found - integration enabled", java.util.Map.of("{name}", name));
            } else {
                MMOBlockLogger.warning("integration.not_found", name + " is not installed or not enabled.", java.util.Map.of("{name}", name));
            }
        }
    }

    /**
     * Called by {@link org.bukkit.event.server.PluginEnableEvent} listener when a
     * soft dependency enables after MMOBlock's initial startup check.
     * <p>
     * Updates the cached flag for the given plugin and logs the change so the
     * console reflects the correct state without requiring a server restart.
     * </p>
     *
     * @param pluginName the name of the plugin that just enabled
     */
    public static void notifyPluginEnabled(final String pluginName) {
        if (pluginName == null || pluginName.isEmpty()) return;

        switch (pluginName) {
            case "MMOCore" -> {
                if (!mmoCore) {
                    mmoCore = true;
                    MMOBlockLogger.info("integration.found", "MMOCore detected and integration enabled.", java.util.Map.of("{name}", "MMOCore"));
                }
            }
            case "MMOItems" -> {
                if (!mmoItems) {
                    mmoItems = true;
                    MMOBlockLogger.info("integration.found", "MMOItems detected and integration enabled.", java.util.Map.of("{name}", "MMOItems"));
                }
            }
            case "ItemsAdder" -> {
                if (!itemsAdder) {
                    itemsAdder = true;
                    MMOBlockLogger.info("integration.found", "ItemsAdder detected and integration enabled.", java.util.Map.of("{name}", "ItemsAdder"));
                }
            }
            case "CraftEngine" -> {
                if (!craftEngine) {
                    craftEngine = true;
                    MMOBlockLogger.info("integration.found", "CraftEngine detected and integration enabled.", java.util.Map.of("{name}", "CraftEngine"));
                }
            }
            case "ModelEngine" -> {
                if (!modelEngine) {
                    modelEngine = true;
                    MMOBlockLogger.info("integration.found", "ModelEngine detected and integration enabled.", java.util.Map.of("{name}", "ModelEngine"));
                }
            }
            case "BetterModel" -> {
                if (!betterModel) {
                    betterModel = true;
                    MMOBlockLogger.info("integration.found", "BetterModel detected and integration enabled.", java.util.Map.of("{name}", "BetterModel"));
                }
            }
            case "Minepacks" -> {
                if (!minepacks) {
                    minepacks = true;
                    MMOBlockLogger.info("integration.found", "Minepacks detected and integration enabled.", java.util.Map.of("{name}", "Minepacks"));
                }
            }
        }
    }

    /**
     * Force re-check on next call. Useful for testing or plugin reload scenarios.
     */
    public static void reset() {
        initialized = false;
        mmoCore = false;
        mmoItems = false;
        itemsAdder = false;
        craftEngine = false;
        modelEngine = false;
        betterModel = false;
        minepacks = false;
    }
}
