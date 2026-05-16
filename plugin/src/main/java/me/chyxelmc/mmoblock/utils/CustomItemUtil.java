package me.chyxelmc.mmoblock.utils;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.NodeDefinition;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CustomItemUtil {

    public static final String TYPE_BLOCK = "block";
    public static final String TYPE_NODE = "node";
    public static final String TYPE_BLOCK_REMOVER = "block_remover";
    public static final String TYPE_NODE_REMOVER = "node_remover";

    private final NamespacedKey typeKey;
    private final NamespacedKey idKey;

    public CustomItemUtil(final MMOBlock plugin) {
        this.typeKey = new NamespacedKey(plugin, "mmoblock_item_type");
        this.idKey = new NamespacedKey(plugin, "mmoblock_item_id");
    }

    public ItemStack createBlockItem(final BlockDefinition definition) {
        if (definition == null || definition.itemMaterial() == null) {
            return null;
        }
        final String name = definition.itemName() != null ? definition.itemName() : definition.displayName();
        return createItem(definition.itemMaterial(), name, null, TYPE_BLOCK, definition.id());
    }

    public ItemStack createNodeItem(final NodeDefinition definition) {
        if (definition == null || definition.itemMaterial() == null) {
            return null;
        }
        final String name = definition.itemName() != null ? definition.itemName() : definition.id();
        return createItem(definition.itemMaterial(), name, null, TYPE_NODE, definition.id());
    }

    public ItemStack createBlockRemover() {
        return createItem(Material.STICK, "&cBlock Remover", List.of("&7Tools for removing MMOBlock block"), TYPE_BLOCK_REMOVER, null);
    }

    public ItemStack createNodeRemover() {
        return createItem(Material.STICK, "&cNode Remover", List.of("&7Tools for removing MMOBlock node"), TYPE_NODE_REMOVER, null);
    }

    public CustomItemData read(final ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        final String type = container.get(this.typeKey, PersistentDataType.STRING);
        if (type == null) {
            return null;
        }
        final String id = container.get(this.idKey, PersistentDataType.STRING);
        return new CustomItemData(type, id);
    }

    public boolean isCustomItem(final ItemStack itemStack) {
        return read(itemStack) != null;
    }

    private ItemStack createItem(
            final Material material,
            final String name,
            final List<String> lore,
            final String type,
            final String id
    ) {
        if (material == null) {
            return null;
        }
        final ItemStack itemStack = new ItemStack(material, 1);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return itemStack;
        }
        if (name != null && !name.isBlank()) {
            meta.displayName(TextColor.toComponent(name));
        }
        if (lore != null && !lore.isEmpty()) {
            final List<Component> loreComponents = lore.stream()
                    .map(TextColor::toComponent)
                    .toList();
            meta.lore(loreComponents);
        }
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(this.typeKey, PersistentDataType.STRING, type);
        if (id != null && !id.isBlank()) {
            container.set(this.idKey, PersistentDataType.STRING, id);
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public record CustomItemData(String type, String id) {
    }
}

