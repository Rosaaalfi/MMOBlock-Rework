package me.chyxelmc.mmoblock.api.integration;

import at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack;
import at.pcgamingfreaks.Minepacks.Bukkit.API.MinepacksPlugin;
import me.chyxelmc.mmoblock.utils.MMOBlockLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Integration layer for <a href="https://www.spigotmc.org/resources/minepacks.1000/">Minepacks</a>.
 * <p>
 * Uses the Minepacks API to add items directly into a player's backpack.
 * <strong>Only uses cached backpack lookups</strong> ({@code getBackpackCachedOnly})
 * to guarantee synchronous, predictable behavior. If the backpack is not yet cached,
 * the item falls back to the player's inventory immediately — no async callbacks,
 * no race conditions.
 * </p>
 * <p>
 * All Bukkit API calls are ensured to run on the server thread for
 * Paper/Folia thread-safety.
 * </p>
 */
public final class MinepacksIntegration {

    private static final String MINEPACKS_PLUGIN_NAME = "Minepacks";
    private static final String MMOBLOCK_PLUGIN_NAME = "MMOBlock";

    private MinepacksIntegration() {
    }

    /**
     * Checks whether Minepacks is currently installed and enabled on the server.
     *
     * @return {@code true} if Minepacks is installed and currently enabled
     */
    public static boolean isAvailable() {
        try {
            final Plugin minepacks = Bukkit.getPluginManager().getPlugin(MINEPACKS_PLUGIN_NAME);
            return minepacks != null && minepacks.isEnabled();
        } catch (final Exception ignored) {
            return false;
        }
    }

    /**
     * Resolve the {@link MinepacksPlugin} instance from Bukkit's plugin manager.
     */
    private static MinepacksPlugin resolvePlugin() {
        try {
            final Plugin plugin = Bukkit.getPluginManager().getPlugin(MINEPACKS_PLUGIN_NAME);
            if (plugin == null || !plugin.isEnabled()) {
                return null;
            }
            if (plugin instanceof MinepacksPlugin) {
                return (MinepacksPlugin) plugin;
            }
            return MinepacksPlugin.getInstance();
        } catch (final Exception e) {
            MMOBlockLogger.debug("[Minepacks] Failed to resolve MinepacksPlugin: " + e.getMessage());
            return null;
        }
    }

    /**
     * Add an {@link ItemStack} to the player's Minepacks backpack.
     * <p>
     * Uses only the cached backpack (synchronous). If the backpack is not yet cached
     * in Minepacks, the item falls back to the player's inventory immediately.
     * This avoids the race conditions and duplication bugs inherent in async
     * callback-based backpack loading.
     * </p>
     *
     * @param player the player whose backpack to add items to
     * @param stack  the item stack to add
     * @return {@code true} if Minepacks was available and the item was handled
     *         (either added to backpack or fell back to inventory internally),
     *         {@code false} if Minepacks is unavailable (caller should handle the item)
     */
    public static boolean addToBackpack(final Player player, final ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return false;
        }

        final MinepacksPlugin minepacksPlugin = resolvePlugin();
        if (minepacksPlugin == null) {
            return false;
        }

        executeOnServerThread(() -> {
            // Only use cached backpack — synchronous, no race conditions
            final Backpack backpack = minepacksPlugin.getBackpackCachedOnly(player);
            if (backpack != null) {
                addItemAndSave(backpack, player, stack);
            } else {
                // Backpack not cached yet — fall back to inventory immediately
                MMOBlockLogger.debug("[Minepacks] Backpack not cached for " + player.getName()
                        + ", falling back to inventory.");
                fallbackToInventory(player, stack);
            }
        });

        return true;
    }

    /**
     * Add an item to a backpack and save it.
     * <b>Must be called on the server thread.</b>
     */
    private static void addItemAndSave(final Backpack backpack, final Player player, final ItemStack stack) {
        final ItemStack inserted = stack.clone();
        boolean added = false;

        try {
            final ItemStack remainder = backpack.addItem(inserted);
            added = true;

            // Drop remainder if backpack is full
            if (remainder != null && !remainder.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remainder);
            }

            backpack.save();
        } catch (final Exception e) {
            MMOBlockLogger.debug("[Minepacks] Failed to save backpack: " + e.getMessage());

            // Rollback: remove the item from backpack to prevent duplication
            if (added) {
                try {
                    backpack.getInventory().removeItem(inserted);
                } catch (final Exception ignored) {
                    // Best-effort removal
                }
            }

            // Fall back to inventory
            fallbackToInventory(player, stack);
        }
    }

    /**
     * Add items directly to the player's inventory. Drops any overflow on the ground.
     * <b>Must be called on the server thread.</b>
     */
    private static void fallbackToInventory(final Player player, final ItemStack stack) {
        if (!player.isOnline()) return;
        try {
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (final ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        } catch (final Exception e) {
            try {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            } catch (final Exception ignored) {
                MMOBlockLogger.warning("[Minepacks] Could not add item to inventory or world: " + e.getMessage());
            }
        }
    }

    /**
     * Execute a task on the server thread. If already on it, run immediately.
     * If not, schedule via Bukkit scheduler.
     */
    private static void executeOnServerThread(final Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            final Plugin plugin = Bukkit.getPluginManager().getPlugin(MMOBLOCK_PLUGIN_NAME);
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, task);
            } else {
                MMOBlockLogger.warning("[Minepacks] MMOBlock plugin reference not found, cannot schedule task.");
            }
        }
    }
}
