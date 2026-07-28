package me.chyxelmc.mmoblock.api.drop;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A handler for custom drop types registered by third-party addon plugins.
 *
 * <p>When a block with a {@link me.chyxelmc.mmoblock.api.model.DropType#CUSTOM} drop entry is mined,
 * the registered {@code DropHandler} for that handler ID is invoked to process the drop.</p>
 *
 * <h3>Example: Backpack Drop</h3>
 * <pre>{@code
 * MMOBlockApi.get().getDropHandlerRegistry()
 *     .register("backpack_plugin:backpack", new BackpackDropHandler());
 * }</pre>
 *
 * <p>Config example:</p>
 * <pre>{@code
 * drops:
 *   custom_backpack:
 *     - type: CUSTOM
 *       customHandlerId: "backpack_plugin:backpack"
 *       chance: 1.0
 *       customData:
 *         backpack_type: "mining"
 *         slots: 27
 * }</pre>
 */
@FunctionalInterface
public interface DropHandler {

    /**
     * Invoked when a custom drop needs to be processed.
     *
     * @param context the drop context containing player, block, definition, and custom data
     */
    void processDrop(@NotNull DropContext context);
}
