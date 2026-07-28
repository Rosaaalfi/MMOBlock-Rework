package me.chyxelmc.mmoblock.api.model;

import me.chyxelmc.mmoblock.api.model.BlockDefinition;
import me.chyxelmc.mmoblock.api.model.PlacedBlock;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Context for a model renderer operation.
 *
 * @param blockId       the block definition ID
 * @param block         the placed block (may be null during pre-spawn)
 * @param definition    the block definition
 * @param world         the world where the model should render
 * @param location      the location for the model
 * @param player        the player (for per-player rendering), or null for global
 * @param customConfig  custom configuration data from the block config's model section
 */
public record ModelContext(
    @NotNull String blockId,
    @Nullable PlacedBlock block,
    @NotNull BlockDefinition definition,
    @NotNull World world,
    @NotNull Location location,
    @Nullable Player player,
    @NotNull Map<String, Object> customConfig
) {
}
