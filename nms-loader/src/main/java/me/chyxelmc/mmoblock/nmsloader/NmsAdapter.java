package me.chyxelmc.mmoblock.nmsloader;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public interface NmsAdapter {

    String targetMinecraftVersion();

    void validateNms();

    void sendSystemMessage(Player player, String message);

    SpawnResult spawnInteraction(
        final World world,
        final Location location,
        final float width,
        final float height,
        final NamespacedKey uniqueIdKey,
        final UUID blockUniqueId
    );

    RemoveResult removeInteraction(World world, UUID interactionUniqueId);

    void sendBreakAnimation(World world, Location location, int entityId, int stage);

    void showFakeBlock(World world, Location location, Material material);

    void clearFakeBlock(World world, Location location);

    default boolean supportsPacketHolograms() {
        return false;
    }

    default void upsertPacketHologram(
        final Player player,
        final UUID hologramUniqueId,
        final Location baseLocation,
        final List<HologramLine> lines
    ) {
        // Optional adapter feature.
    }

    default void removePacketHologram(final Player player, final UUID hologramUniqueId) {
        // Optional adapter feature.
    }

    record HologramLine(HologramLineType type, String text, Material material, double offsetY) {

        public static HologramLine text(final String text, final double offsetY) {
            return new HologramLine(HologramLineType.TEXT, text, null, offsetY);
        }

        public static HologramLine item(final Material material, final double offsetY) {
            return new HologramLine(HologramLineType.ITEM, null, material, offsetY);
        }

        public static HologramLine block(final Material material, final double offsetY) {
            return new HologramLine(HologramLineType.BLOCK, null, material, offsetY);
        }
    }

    enum HologramLineType {
        TEXT,
        ITEM,
        BLOCK
    }

    enum SpawnPath {
        NMS
    }

    record SpawnResult(boolean success, String reason, UUID interactionUniqueId, SpawnPath path) {

        public static SpawnResult success(final UUID interactionUniqueId, final SpawnPath path) {
            return new SpawnResult(true, "", interactionUniqueId, path);
        }

        public static SpawnResult failed(final String reason) {
            return new SpawnResult(false, reason, null, SpawnPath.NMS);
        }
    }

    record RemoveResult(boolean success, boolean removed, String reason, SpawnPath path) {

        public static RemoveResult success(final boolean removed, final SpawnPath path) {
            return new RemoveResult(true, removed, "", path);
        }

        public static RemoveResult failed(final String reason) {
            return new RemoveResult(false, false, reason, SpawnPath.NMS);
        }
    }
}
