package me.chyxelmc.mmoblock.gui.management;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.TextDecoration;
import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.gui.Gui;
import me.chyxelmc.mmoblock.gui.PagedGui;
import me.chyxelmc.mmoblock.gui.item.SimpleItem;
import me.chyxelmc.mmoblock.gui.window.GuiWindow;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import me.chyxelmc.mmoblock.utils.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Hardcoded block-definition list and basic editor workflow. */
public final class BlockManagementGui {
    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private final MMOBlock plugin;
    private final BlockConfigLoader loader;
    private final BlockDefinitionStore store;
    private final BlockChatInput chatInput;
    private final CustomItemUtil customItemUtil;
    private final BlockEditorGui editor;

    public BlockManagementGui(final MMOBlock plugin, final BlockConfigLoader loader,
                              final CustomItemUtil customItemUtil) {
        this.plugin = plugin;
        this.loader = loader;
        this.customItemUtil = customItemUtil;
        this.store = new BlockDefinitionStore(plugin, loader);
        this.chatInput = new BlockChatInput(plugin);
        this.editor = new BlockEditorGui(plugin, loader, this.store, this.chatInput, this::open);
    }

    public void open(final Player player) {
        final PagedGui gui = new PagedGui(9, 5, CONTENT_SLOTS);

        final List<me.chyxelmc.mmoblock.gui.item.GuiItem> content = new ArrayList<>();
        this.loader.blockIds().stream().sorted().forEach(id -> {
            final BlockDefinitionModel definition = this.loader.findBlock(id);
            final Material icon = definition.itemMaterial() != null ? definition.itemMaterial()
                    : definition.realBlockMaterial() != null ? definition.realBlockMaterial() : Material.IRON_ORE;
            content.add(new SimpleItem(viewer -> stack(icon, displayName(viewer, id, definition),
                    List.of(
                            tr(viewer, "gui.blocks.block.left_click", "&eLeft Click &7Edit Block"),
                            tr(viewer, "gui.blocks.block.right_click", "&eRight Click &7Get Block")
                    )), click -> {
                        if (click.clickType().isLeftClick()) this.editor.open(click.player(), id, click.view().page());
                        if (click.clickType().isRightClick()) giveBlock(click.player(), definition);
                    }));
        });
        content.add(item(player, Material.GREEN_STAINED_GLASS_PANE, "gui.blocks.add.name", "&aNew Block",
                List.of(tr(player, "gui.blocks.add.lore", "&7Adding new resources.")), click -> promptNewBlock(player)));
        gui.setContent(content);

        gui.setItem(18, dynamicNavigation(player, gui, false));
        gui.setItem(26, dynamicNavigation(player, gui, true));
        gui.setItem(40, item(player, Material.BARRIER, "gui.blocks.close", "&cClose", List.of(), click -> click.view().close()));
        GuiWindow.builder().viewer(player).gui(gui)
                .localizedTitle(context -> TextColor.toLegacySection(
                        context.translate("gui.blocks.title", "&8Block Management")))
                .open(this.plugin.guiEngine());
    }

    private SimpleItem dynamicNavigation(final Player player, final PagedGui gui, final boolean next) {
        return new SimpleItem(viewer -> {
            final boolean active = next ? gui.hasNextPage() : gui.hasPreviousPage();
            if (!active) return null;
            return stack(Material.ARROW, tr(viewer, next ? "gui.blocks.next" : "gui.blocks.previous",
                    next ? "&eNext Page" : "&ePrevious Page"), List.of());
        }, click -> {
            if (next && gui.hasNextPage()) gui.nextPage();
            if (!next && gui.hasPreviousPage()) gui.previousPage();
        });
    }

    void open(final Player player, final int page) {
        open(player);
        final var view = this.plugin.guiEngine().view(player);
        if (view != null && view.gui() instanceof PagedGui paged) paged.setPage(page);
    }

    private void openEditor(final Player player, final String id) {
        final BlockDefinitionModel definition = this.loader.findBlock(id);
        if (definition == null) {
            player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.not_found", "&cThat block no longer exists.")));
            open(player);
            return;
        }
        final Gui gui = Gui.empty(9, 3);
        final String displayName = displayName(player, id, definition);
        gui.setItem(11, item(player, Material.NAME_TAG, "gui.blocks.editor.name", "&eEdit Name",
                List.of(tr(player, "gui.blocks.editor.name_lore", "&7Current: {value}").replace("{value}", displayName)),
                click -> promptEdit(player, id, EditField.NAME)));
        final Material material = definition.itemMaterial() == null ? Material.IRON_ORE : definition.itemMaterial();
        gui.setItem(13, item(player, material, "gui.blocks.editor.material", "&eEdit Material",
                List.of(tr(player, "gui.blocks.editor.material_lore", "&7Current: {value}").replace("{value}", material.name())),
                click -> promptEdit(player, id, EditField.MATERIAL)));
        gui.setItem(15, item(player, Material.CLOCK, "gui.blocks.editor.respawn", "&eEdit Respawn Time",
                List.of(tr(player, "gui.blocks.editor.respawn_lore", "&7Current: {value}s")
                        .replace("{value}", String.valueOf(definition.respawnTimeSeconds()))),
                click -> promptEdit(player, id, EditField.RESPAWN)));
        gui.setItem(22, item(player, Material.ARROW, "gui.blocks.editor.back", "&eBack", List.of(), click -> open(player)));
        GuiWindow.builder().viewer(player).gui(gui)
                .localizedTitle(context -> TextColor.toLegacySection(context.translate(
                        "gui.blocks.editor.title", "&8Edit {block}", Map.of("{block}", displayName))))
                .open(this.plugin.guiEngine());
    }

    private void giveBlock(final Player player, final BlockDefinitionModel definition) {
        final ItemStack blockItem = this.customItemUtil.createBlockItem(definition);
        if (blockItem == null) {
            player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.block.no_item",
                    "&cThis block has no configured item.")));
            return;
        }
        player.getInventory().addItem(blockItem);
        player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.block.received",
                "&aBlock item added to your inventory.")));
    }

    private void promptNewBlock(final Player player) {
        player.closeInventory();
        player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.new", "&eEnter block name:")));
        this.chatInput.await(player, input -> {
            if (cancelled(player, input)) return;
            if (this.store.exists(input)) {
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.duplicate", "&cA block with that name already exists.")));
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.new", "&eEnter block name:")));
                return;
            }
            try {
                this.store.create(input);
                this.store.reload();
                this.chatInput.complete(player);
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.created", "&aBlock created.")));
                open(player);
            } catch (final IOException exception) {
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.save_failed", "&cCould not save the block.")));
            }
        });
    }

    private void promptEdit(final Player player, final String id, final EditField field) {
        player.closeInventory();
        player.sendMessage(TextColor.toComponent(tr(player, field.promptKey, field.promptFallback)));
        this.chatInput.await(player, input -> {
            if (cancelled(player, input)) return;
            try {
                switch (field) {
                    case NAME -> {
                        if (input.isBlank()) throw new IllegalArgumentException();
                        this.store.updateName(id, input);
                    }
                    case MATERIAL -> {
                        final Material material = Material.matchMaterial(input.trim());
                        if (material == null || !material.isBlock()) throw new IllegalArgumentException();
                        this.store.updateMaterial(id, material);
                    }
                    case RESPAWN -> {
                        final long seconds = Long.parseLong(input.trim());
                        if (seconds < 0) throw new IllegalArgumentException();
                        this.store.updateRespawnTime(id, seconds);
                    }
                }
                this.store.reload();
                this.chatInput.complete(player);
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.updated", "&aBlock updated.")));
                openEditor(player, id);
            } catch (final IllegalArgumentException exception) {
                player.sendMessage(TextColor.toComponent(tr(player, field.errorKey, field.errorFallback)));
                player.sendMessage(TextColor.toComponent(tr(player, field.promptKey, field.promptFallback)));
            } catch (final IOException exception) {
                player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.save_failed", "&cCould not save the block.")));
            }
        });
    }

    private boolean cancelled(final Player player, final String input) {
        final String keyword = this.plugin.getConfig().getString("gui.block-management.cancel-keyword", "cancel");
        if (!input.equalsIgnoreCase(keyword)) return false;
        this.chatInput.complete(player);
        player.sendMessage(TextColor.toComponent(tr(player, "gui.blocks.input.cancelled", "&eBlock editing cancelled.")));
        open(player);
        return true;
    }

    private SimpleItem item(final Player player, final Material material, final String key, final String fallback,
                            final List<String> lore, final me.chyxelmc.mmoblock.gui.GuiAction action) {
        return new SimpleItem(viewer -> stack(material, key == null ? fallback : tr(viewer, key, fallback), lore), action);
    }

    private ItemStack stack(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(TextColor.toComponent(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(TextColor::toComponent)
                    .map(component -> component.decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String tr(final Player player, final String key, final String fallback) {
        return this.plugin.translationService().translate(player, key, fallback);
    }

    private String displayName(final Player player, final String id, final BlockDefinitionModel definition) {
        final String itemName = this.loader.blockListName(id);
        final String configured = itemName == null || itemName.isBlank() ? definition.displayName() : itemName;
        return this.plugin.translationService().resolveInline(player, configured);
    }

    private enum EditField {
        NAME("gui.blocks.input.edit_name", "&eEnter the new display name:", "gui.blocks.input.invalid_name", "&cEnter a non-empty name."),
        MATERIAL("gui.blocks.input.edit_material", "&eEnter a Bukkit block material:", "gui.blocks.input.invalid_material", "&cThat is not a valid block material."),
        RESPAWN("gui.blocks.input.edit_respawn", "&eEnter respawn time in seconds:", "gui.blocks.input.invalid_respawn", "&cEnter a non-negative whole number.");

        private final String promptKey;
        private final String promptFallback;
        private final String errorKey;
        private final String errorFallback;

        EditField(final String promptKey, final String promptFallback, final String errorKey, final String errorFallback) {
            this.promptKey = promptKey;
            this.promptFallback = promptFallback;
            this.errorKey = errorKey;
            this.errorFallback = errorFallback;
        }
    }
}
