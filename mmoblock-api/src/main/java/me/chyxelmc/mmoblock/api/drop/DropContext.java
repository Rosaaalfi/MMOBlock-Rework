package me.chyxelmc.mmoblock.api.drop;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Context passed to a {@link DropHandler} when a custom drop is processed.
 *
 * <p>Contains all relevant information about the block being mined, the player,
 * and any custom configuration data specified in the drops YAML file.</p>
 */
public interface DropContext {

    /**
     * The player who mined the block.
     */
    @NotNull
    Player player();

    /**
     * The block that was mined.
     */
    @NotNull
    PlacedBlock block();

    /**
     * The block definition for the mined block.
     */
    @NotNull
    BlockDefinition blockDefinition();

    /**
     * The location where the drop should appear.
     */
    @NotNull
    Location dropLocation();

    /**
     * The unique handler ID that was specified in the drop configuration
     * (e.g., {@code "backpack_plugin:backpack"}).
     */
    @NotNull
    String handlerId();

    /**
     * An unmodifiable map of custom configuration data from the drop entry.
     *
     * <p>These are the key-value pairs specified under the {@code customData}
     * section in the drops YAML configuration.</p>
     *
     * @return custom configuration data, never null but may be empty
     */
    @NotNull
    Map<String, Object> customData();

    /**
     * The drop entry's configured chance (0.0 – 1.0).
     * If probability check was already performed, this is the original chance value.
     */
    double chance();
}
