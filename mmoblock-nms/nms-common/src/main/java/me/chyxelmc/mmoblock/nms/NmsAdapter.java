package me.chyxelmc.mmoblock.nms;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface NmsAdapter {

    String targetMinecraftVersion();

    void validateNms();

    default SchematicData loadSchematic(final String filePath) {
        return null;
    }

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

    default void showFakeBlock(final Player player, final World world, final Location location, final Material material) {
        showFakeBlock(world, location, material);
    }

    default void clearFakeBlock(final Player player, final World world, final Location location) {
        clearFakeBlock(world, location);
    }

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

    default void clearPacketHologramCacheForPlayer(final UUID playerUniqueId) {
        // Optional adapter feature.
    }

    default boolean supportsPacketBdEngineModels() {
        return false;
    }

    default void upsertPacketBdEngineModel(
            final Player player,
            final UUID modelUniqueId,
            final Location baseLocation,
            final List<BdEngineDisplayPart> parts
    ) {
        // Optional adapter feature.
    }

    default void removePacketBdEngineModel(final Player player, final UUID modelUniqueId) {
        // Optional adapter feature.
    }

    default void clearPacketBdEngineModelCacheForPlayer(final UUID playerUniqueId) {
        // Optional adapter feature.
    }



    /**
     * Applies a colored glow effect to an entity for a specific player.
     * <p>
     * Uses Bukkit Scoreboard API (which internally sends NMS packets) to create
     * a team with the desired color and add the entity. The glowing flag is set
     * via Bukkit API. NMS adapter implementations may override this for
     * optimized per-player packet handling.
     *
     * @param player    the player who should see the glow
     * @param entity    the item entity to glow
     * @param colorName the desired glow color name (e.g. "white", "red", "rainbow")
     */
    @SuppressWarnings("deprecation")
    default void applyEntityGlow(final Player player, final Entity entity, final String colorName) {
        if (entity == null) {
            return;
        }
        try {
            final String normalized = colorName == null ? "white" : colorName.trim().toLowerCase(Locale.ROOT);
            final ChatColor chatColor = resolveGlowChatColor(normalized);
            if (chatColor == null) {
                return;
            }

            final String teamName = "mmbg" + Integer.toString(Math.max(0, entity.getEntityId()), 36);
            if (!sendGlowPackets(player, entity, teamName, chatColor)) {
                entity.setGlowing(true);
            }
        } catch (final Exception ignored) {
            entity.setGlowing(true);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean sendGlowPackets(final Player player, final Entity entity, final String teamName, final ChatColor chatColor) {
        if (player == null || entity == null || chatColor == null) {
            return false;
        }
        try {
            final Object connection = me.chyxelmc.mmoblock.nms.utils.GlowReflectionCache.getConnection(
                    me.chyxelmc.mmoblock.nms.utils.GlowReflectionCache.getHandle(player));
            me.chyxelmc.mmoblock.nms.utils.GlowReflectionCache.sendGlowMetadataPacket(connection, entity);
            me.chyxelmc.mmoblock.nms.utils.GlowReflectionCache.sendGlowTeamPackets(connection, entity, teamName, chatColor);
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Maps a color name string to a Bukkit ChatColor.
     * Delegates to {@link me.chyxelmc.mmoblock.nms.utils.ColorResolver}.
     */
    static ChatColor resolveGlowChatColor(final String raw) {
        return me.chyxelmc.mmoblock.nms.utils.ColorResolver.resolveChatColor(raw);
    }

    record BdEngineDisplayPart(BdEngineDisplayType type, Material material, String text, float[] matrix, int skyLight, int blockLight) {
        @Override
        public final boolean equals(final Object o) {
            if (o == this) return true;
            if (!(o instanceof final BdEngineDisplayPart other)) return false;
            return type == other.type
                    && material == other.material
                    && text.equals(other.text)
                    && java.util.Arrays.equals(matrix, other.matrix)
                    && skyLight == other.skyLight
                    && blockLight == other.blockLight;
        }

        @Override
        public final int hashCode() {
            int result = type.hashCode();
            result = 31 * result + material.hashCode();
            result = 31 * result + text.hashCode();
            result = 31 * result + java.util.Arrays.hashCode(matrix);
            result = 31 * result + skyLight;
            result = 31 * result + blockLight;
            return result;
        }

        @Override
        public final String toString() {
            return "BdEngineDisplayPart["
                    + "type=" + type
                    + ", material=" + material
                    + ", text=" + text
                    + ", matrix=" + java.util.Arrays.toString(matrix)
                    + ", skyLight=" + skyLight
                    + ", blockLight=" + blockLight
                    + "]";
        }
    }

    enum BdEngineDisplayType {
        BLOCK,
        ITEM,
        TEXT
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
