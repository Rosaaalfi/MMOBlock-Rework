package me.chyxelmc.mmoblock.api.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves and matches items from third-party plugins.
 *
 * <p>Third-party addon plugins can register custom item resolvers to handle
 * item matching for tools and drops within MMOBlock configurations.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MMOBlockApi.get().getItemResolverRegistry()
 *     .register("myplugin", new MyPluginItemResolver());
 * }</pre>
 *
 * <p>Config example:</p>
 * <pre>{@code
 * tools:
 *   my_tools:
 *     - item:
 *         type: "myplugin"
 *         id: "super_pickaxe"
 *       left_click:
 *         clickNeeded: 4
 *         allowedDrops:
 *           - "ore_drops"
 * }</pre>
 */
public interface ItemResolver {

    /**
     * Get a namespaced string identifier for this resolver.
     * This should match the {@code type} field in configuration files
     * (e.g., {@code "myplugin"}, {@code "mmoitems"}, {@code "itemsadder"}).
     *
     * @return the resolver namespace/type identifier
     */
    @NotNull
    String getNamespace();

    /**
     * Get an ItemStack for the given item ID and amount.
     *
     * @param itemId the item identifier (e.g., {@code "super_pickaxe"})
     * @param amount the desired stack amount
     * @return the ItemStack, or null if the item was not found
     */
    @Nullable
    ItemStack getItemStack(@NotNull String itemId, int amount);

    /**
     * Check whether the given ItemStack matches the specified item ID.
     *
     * @param item   the item to check
     * @param itemId the item identifier to match against
     * @return true if the item matches
     */
    boolean matches(@NotNull ItemStack item, @NotNull String itemId);
}
