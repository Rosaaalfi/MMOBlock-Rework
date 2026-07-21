package me.chyxelmc.mmoblock.nmsloader;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import me.chyxelmc.mmoblock.nmsloader.utils.ReflectionUtil;
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

    private boolean sendGlowPackets(final Player player, final Entity entity, final String teamName, final ChatColor chatColor) {
        if (player == null || entity == null || chatColor == null) {
            return false;
        }
        try {
            final Object handle = player.getClass().getMethod("getHandle").invoke(player);
            final Object connection = handle.getClass().getField("connection").get(handle);
            sendGlowMetadataPacket(connection, entity);
            sendGlowTeamPackets(connection, entity, teamName, chatColor);
            return true;
        } catch (final ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private void sendGlowMetadataPacket(final Object connection, final Entity entity) throws ReflectiveOperationException {
        final Object nmsEntity = entity.getClass().getMethod("getHandle").invoke(entity);
        final Class<?> nmsEntityClass = Class.forName("net.minecraft.world.entity.Entity");
        final Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
        final Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
        final Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
        final Class<?> metadataPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");

        final java.lang.reflect.Field sharedFlagsField = nmsEntityClass.getDeclaredField("DATA_SHARED_FLAGS_ID");
        // NMS internal field — no public API to read entity data accessors; reflection required for cross-version compatibility
        ReflectionUtil.safeSetAccessible(sharedFlagsField, "NMS DATA_SHARED_FLAGS_ID field");
        final Object sharedFlags = sharedFlagsField.get(null);
        final Object entityData = nmsEntityClass.getMethod("getEntityData").invoke(nmsEntity);
        final byte currentFlags = (byte) synchedEntityDataClass.getMethod("get", entityDataAccessorClass).invoke(entityData, sharedFlags);
        final byte glowingFlags = (byte) (currentFlags | (1 << 6));
        final Object dataValue = dataValueClass.getMethod("create", entityDataAccessorClass, Object.class).invoke(null, sharedFlags, glowingFlags);
        final java.lang.reflect.Constructor<?> metadataConstructor = metadataPacketClass.getDeclaredConstructor(int.class, List.class);
        // NMS packet constructor is package-private; no public API for constructing ClientboundSetEntityDataPacket with custom data
        ReflectionUtil.safeSetAccessible(metadataConstructor, "NMS ClientboundSetEntityDataPacket constructor");
        sendPacket(connection, metadataConstructor.newInstance(entity.getEntityId(), List.of(dataValue)));
    }

    private void sendGlowTeamPackets(final Object connection, final Entity entity, final String teamName, final ChatColor chatColor)
            throws ReflectiveOperationException {
            final Class<?> scoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard");
            final Class<?> playerTeamClass = Class.forName("net.minecraft.world.scores.PlayerTeam");
            final Class<?> chatFormattingClass = Class.forName("net.minecraft.ChatFormatting");
            final Class<?> teamPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket");
            final Class<?> collisionRuleClass = Class.forName("net.minecraft.world.scores.Team$CollisionRule");
            final Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket$Action");

            final Object scoreboard = scoreboardClass.getConstructor().newInstance();
            final java.lang.reflect.Constructor<?> playerTeamConstructor = playerTeamClass.getDeclaredConstructor(scoreboardClass, String.class);
            // NMS PlayerTeam constructor is package-private; no public API for constructing team packets with custom data
            ReflectionUtil.safeSetAccessible(playerTeamConstructor, "NMS PlayerTeam constructor");
            final Object playerTeam = playerTeamConstructor.newInstance(scoreboard, teamName);
            final Object formatting = chatFormattingClass.getMethod("getByCode", char.class).invoke(null, chatColor.getChar());
            playerTeamClass.getMethod("setColor", chatFormattingClass).invoke(playerTeam, formatting);
            playerTeamClass.getMethod("setCollisionRule", collisionRuleClass)
                    .invoke(playerTeam, Enum.valueOf((Class<Enum>) collisionRuleClass.asSubclass(Enum.class), "NEVER"));
            final String entry = entity.getUniqueId().toString();

            final Object createTeamPacket = teamPacketClass
                    .getMethod("createAddOrModifyPacket", playerTeamClass, boolean.class)
                    .invoke(null, playerTeam, true);
            sendPacket(connection, createTeamPacket);

            final Object addAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "ADD");
            final Object addEntityPacket = teamPacketClass
                    .getMethod("createPlayerPacket", playerTeamClass, String.class, actionClass)
                    .invoke(null, playerTeam, entry, addAction);
            sendPacket(connection, addEntityPacket);
    }

    private void sendPacket(final Object connection, final Object packet) throws ReflectiveOperationException {
        if (connection == null || packet == null) {
            return;
        }
        for (final java.lang.reflect.Method method : connection.getClass().getMethods()) {
            if (!"send".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isInstance(packet)) {
                method.invoke(connection, packet);
                return;
            }
        }
    }

    /**
     * Maps a color name string to a Bukkit ChatColor.
     */
    static ChatColor resolveGlowChatColor(final String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatColor.WHITE;
        }
        final String normalized = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return ChatColor.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return switch (normalized) {
                case "black" -> ChatColor.BLACK;
                case "navy" -> ChatColor.DARK_BLUE;
                case "dark_blue" -> ChatColor.DARK_BLUE;
                case "dark_green" -> ChatColor.DARK_GREEN;
                case "teal" -> ChatColor.DARK_AQUA;
                case "dark_aqua", "dark_cyan" -> ChatColor.DARK_AQUA;
                case "maroon" -> ChatColor.DARK_RED;
                case "dark_red" -> ChatColor.DARK_RED;
                case "purple" -> ChatColor.DARK_PURPLE;
                case "dark_purple" -> ChatColor.DARK_PURPLE;
                case "orange" -> ChatColor.GOLD;
                case "gold" -> ChatColor.GOLD;
                case "silver" -> ChatColor.GRAY;
                case "gray", "grey" -> ChatColor.GRAY;
                case "dark_gray", "dark_grey" -> ChatColor.DARK_GRAY;
                case "blue" -> ChatColor.BLUE;
                case "lime" -> ChatColor.GREEN;
                case "green" -> ChatColor.GREEN;
                case "cyan", "aqua" -> ChatColor.AQUA;
                case "red" -> ChatColor.RED;
                case "pink", "fuchsia", "magenta", "light_purple" -> ChatColor.LIGHT_PURPLE;
                case "yellow", "olive" -> ChatColor.YELLOW;
                case "white" -> ChatColor.WHITE;
                default -> ChatColor.WHITE;
            };
        }
    }

    record BdEngineDisplayPart(BdEngineDisplayType type, Material material, String text, float[] matrix, int skyLight, int blockLight) {
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
