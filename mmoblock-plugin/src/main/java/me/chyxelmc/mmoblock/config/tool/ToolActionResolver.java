package me.chyxelmc.mmoblock.config.tool;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration;
import me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration;
import me.chyxelmc.mmoblock.api.integration.MMOItemsIntegration;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ToolAction;

/** Resolves configured tool actions independently from YAML loading. */
public final class ToolActionResolver {

    private final Map<String, List<ToolAction>> actionsByGroup;

    public ToolActionResolver(final Map<String, List<ToolAction>> actionsByGroup) {
        this.actionsByGroup = actionsByGroup;
    }

    public ToolAction resolve(final BlockDefinitionModel definition, final ItemStack item, final String clickType) {
        final ToolAction customAction = resolveCustom(definition, item, clickType);
        if (customAction != null) {
            return customAction;
        }
        return isKnownCustomItem(item)
                ? null
                : resolveVanilla(definition, item == null ? null : item.getType(), clickType);
    }

    public ToolAction resolveVanilla(
            final BlockDefinitionModel definition,
            final Material material,
            final String clickType
    ) {
        for (final String toolId : definition.allowedTools()) {
            final List<ToolAction> actions = this.actionsByGroup.get(toolId.toLowerCase(Locale.ROOT));
            if (actions == null) {
                continue;
            }
            for (final ToolAction action : actions) {
                if (!isCustom(action) && action.material() == material && matchesClick(action, clickType)) {
                    return action;
                }
            }
        }
        return null;
    }

    private ToolAction resolveCustom(
            final BlockDefinitionModel definition,
            final ItemStack item,
            final String clickType
    ) {
        for (final String toolId : definition.allowedTools()) {
            final List<ToolAction> actions = this.actionsByGroup.get(toolId.toLowerCase(Locale.ROOT));
            if (actions == null) {
                continue;
            }
            for (final ToolAction action : actions) {
                if (isCustom(action) && matchesClick(action, clickType) && matchesCustomItem(action, item)) {
                    return action;
                }
            }
        }
        return null;
    }

    private static boolean matchesCustomItem(final ToolAction action, final ItemStack item) {
        try {
            return action.mmoItemsId() != null && MMOItemsIntegration.matchItem(item, action.mmoItemsId())
                    || action.craftEngineId() != null && CraftEngineIntegration.matchItem(item, action.craftEngineId())
                    || action.itemsAdderId() != null && ItemsAdderIntegration.matchItem(item, action.itemsAdderId());
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean isKnownCustomItem(final ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        try {
            if (MMOItemsIntegration.isCustomItem(item)) {
                return true;
            }
        } catch (final Throwable ignored) {
            // Optional integration is unavailable.
        }
        try {
            if (CraftEngineIntegration.isCustomItem(item)) {
                return true;
            }
        } catch (final Throwable ignored) {
            // Optional integration is unavailable.
        }
        try {
            return ItemsAdderIntegration.isCustomItem(item);
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean isCustom(final ToolAction action) {
        return action.mmoItemsId() != null
                || action.craftEngineId() != null
                || action.itemsAdderId() != null;
    }

    private static boolean matchesClick(final ToolAction action, final String clickType) {
        if ("block_break".equals(clickType)) {
            return "block_break".equals(action.clickType());
        }
        return clickType.equals(action.clickType()) || "both_click".equals(action.clickType());
    }
}
