package me.chyxelmc.mmoblock.nms;

import me.chyxelmc.mmoblock.nms.SchematicData;
import me.chyxelmc.mmoblock.nms.SchematicData.SchematicBlock;
import me.chyxelmc.mmoblock.nms.utils.ClientProtocolUtils;
import me.chyxelmc.mmoblock.nms.utils.HologramColorUtil;
import me.chyxelmc.mmoblock.nms.utils.NmsLogger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for version-specific NMS adapters.
 *
 * <p>Contains template-method implementations for all packet-based hologram
 * and BDEngine model lifecycle management, schematic loading/parsing,
 * break animation, and fake block show/clear — the ~80% of code that is
 * textually identical across all 9 NMS version adapters.
 *
 * <p>Subclasses only need to override:
 * <ul>
 *   <li>{@link #targetMinecraftVersion()} and {@link #validateNms()}</li>
 *   <li>The "hook" factory methods below for version-specific NMS operations</li>
 *   <li>{@link #spawnInteraction} / {@link #removeInteraction} (version-specific entity creation)</li>
 * </ul>
 *
 * <p>All NMS types are represented as {@code Object} in the hook signatures
 * so that this class compiles in the {@code nms-common} module which does
 * not have a paperweight dev bundle on its classpath.
 */
@SuppressWarnings({"java:S101", "unchecked", "rawtypes"})
public abstract class AbstractPacketBasedNmsAdapter implements NmsAdapter {

    // ============================================================
    // Constants (identical across all versions)
    // ============================================================

    protected static final double ARMOR_STAND_NAME_Y_OFFSET = 0.23D;
    protected static final double ITEM_ENTITY_Y_OFFSET_MODERN = 0.1D;
    protected static final double ITEM_ENTITY_Y_OFFSET_LEGACY = 0.16D;
    protected static final int TEXT_DISPLAY_INTERPOLATION_DURATION = 2;
    protected static final int BDENGINE_INTERPOLATION_DURATION = 2;

    // ============================================================
    // Cache maps (identical across all versions)
    // ============================================================

    protected final Map<String, PacketHologramState> packetHologramEntityIds = new ConcurrentHashMap<>();
    protected final Map<String, PacketBdEngineModelState> packetBdEngineEntityIds = new ConcurrentHashMap<>();

    // ============================================================
    // HOOK METHODS — subclasses provide version-specific NMS impls
    // ============================================================

    // ---- Core NMS access ----

    /** Returns the NMS ServerPlayer handle for a Bukkit Player. */
    protected abstract Object getServerPlayer(Player player);

    /** Returns the NMS ServerLevel handle for a Bukkit World. */
    protected abstract Object getServerLevel(World world);

    /** Sends an NMS packet via the player's connection. */
    protected abstract void sendPacket(Object serverPlayer, Object packet);

    // ---- Entity metadata extraction ----

    protected abstract int getEntityId(Object nmsEntity);
    protected abstract UUID getEntityUUID(Object nmsEntity);
    protected abstract float getEntityXRot(Object nmsEntity);
    protected abstract float getEntityYRot(Object nmsEntity);
    protected abstract float getEntityYHeadRot(Object nmsEntity);
    protected abstract Object getEntityType(Object nmsEntity);
    protected abstract List<?> getEntityDataValues(Object nmsEntity);

    // ---- Packet construction ----

    protected abstract Object createAddEntityPacket(
            int id, UUID uuid, double x, double y, double z,
            float xRot, float yRot, Object entityType, int data,
            double vx, double vy, double vz, float yHeadRot);

    protected abstract Object createRemoveEntitiesPacket(int[] entityIds);

    protected abstract Object createEntityDataPacket(int entityId, List<?> packedItems);

    protected abstract Object createSetEntityMotionPacket(int entityId, double vx, double vy, double vz);

    protected abstract Object createBlockDestructionPacket(int entityId, int x, int y, int z, int stage);

    protected abstract Object createBlockUpdatePacket(int x, int y, int z, Material material);

    // ---- Display entity factories (holograms) ----

    protected abstract Object createTextDisplayEntity(Object serverLevel, double x, double y, double z, String text);

    protected abstract Object createItemDisplayEntity(Object serverLevel, double x, double y, double z, Material material);

    protected abstract Object createBlockDisplayEntity(Object serverLevel, double x, double y, double z, Material material);

    protected abstract Object createLegacyArmorStandEntity(Object serverLevel, double x, double y, double z, String text);

    protected abstract Object createLegacyItemStandEntity(Object serverLevel, double x, double y, double z, Material material);

    // ---- BDEngine display factories ----

    protected abstract Object createBdEngineBlockDisplay(Object serverLevel, Location base, BdEngineDisplayPart part);

    protected abstract Object createBdEngineItemDisplay(Object serverLevel, Location base, BdEngineDisplayPart part);

    protected abstract Object createBdEngineTextDisplay(Object serverLevel, Location base, BdEngineDisplayPart part);

    protected abstract void configureBdEngineDisplay(Object display, BdEngineDisplayPart part, float[] matrix);

    protected abstract Object nmsBlockState(Material material);

    // ---- Entity configuration hooks (post-creation) ----

    /**
     * Apply per-player yaw to a hologram display entity.
     * Called during upsertPacketHologram for each new display entity.
     * Some adapters (1.19.4, 1.21.11) use this for per-player facing;
     * modern versions (1.21.4+) leave it as default.
     */
    protected void applyHologramEntityYaw(Object display, Location baseLocation) {
        // Default: no-op
    }

    /**
     * Returns the Y offset for legacy client (<1.19.4) item entities.
     * Most versions use 0.16D; 1.19.4 Spigot uses 0.08D.
     */
    protected double getLegacyItemOffset() {
        return ITEM_ENTITY_Y_OFFSET_LEGACY;
    }

    // ---- Schematic / NBT hooks ----

    protected abstract Object readNbtFromFile(String filePath);
    protected abstract int nbtGetShort(Object compoundTag, String key);
    protected abstract Object nbtGetCompound(Object compoundTag, String key);
    protected abstract byte[] nbtGetByteArray(Object compoundTag, String key);
    protected abstract Set<String> nbtGetKeys(Object compoundTag);
    protected abstract int nbtGetInt(Object compoundTag, String key);
    protected abstract boolean nbtContains(Object compoundTag, String key);

    // ============================================================
    // PACKET HOLOGRAM METHODS (identical in all version adapters)
    // ============================================================

    @Override
    public boolean supportsPacketHolograms() {
        return true;
    }

    @Override
    public boolean supportsPacketBdEngineModels() {
        return true;
    }

    @Override
    public void upsertPacketHologram(
            final Player player,
            final UUID hologramUniqueId,
            final Location baseLocation,
            final List<HologramLine> lines
    ) {
        if (baseLocation.getWorld() == null) {
            return;
        }

        final String key = sessionKey(player.getUniqueId(), hologramUniqueId);
        final PacketHologramState previous = this.packetHologramEntityIds.get(key);
        final boolean legacyClient = ClientProtocolUtils.isLegacyClientBelow_1_19_4(player);

        // Fast path: structural match against raw HologramLine list — no intermediate objects
        if (previous != null && !previous.entityIds().isEmpty()
                && previous.structurallyMatches(lines, baseLocation, legacyClient)) {
            // Structural match — send entity data updates only, state unchanged
            final Object level = getServerLevel(baseLocation.getWorld());
            final Object handle = getServerPlayer(player);
            for (int i = 0; i < lines.size(); i++) {
                final Object display = createDisplayEntity(level, baseLocation, lines.get(i), legacyClient);
                if (display == null) continue;
                final List<?> values = getEntityDataValues(display);
                if (values != null && !values.isEmpty()) {
                    sendPacket(handle, createEntityDataPacket(previous.entityIds().get(i), values));
                }
            }
            // Cache state unchanged — no new PacketHologramState, no signatures allocated
            return;
        }

        // Remove old entities
        if (previous != null && !previous.entityIds().isEmpty()) {
            sendRemovePacket(player, previous.entityIds());
        }

        // Full spawn: compute signatures for new state
        final List<PacketLineSignature> signatures = packetLineSignatures(lines);
        final PacketBaseSignature baseSignature = packetBaseSignature(baseLocation);

        final Object level = getServerLevel(baseLocation.getWorld());
        final Object handle = getServerPlayer(player);
        final List<Integer> newIds = new ArrayList<>();

        for (final HologramLine line : lines) {
            final double lineX = baseLocation.getX();
            final double baseLineY = baseLocation.getY() - line.offsetY();
            final double lineZ = baseLocation.getZ();
            final double itemOffset = legacyClient ? getLegacyItemOffset() : ITEM_ENTITY_Y_OFFSET_MODERN;
            final double lineY = switch (line.type()) {
                case TEXT -> legacyClient ? baseLineY - ARMOR_STAND_NAME_Y_OFFSET : baseLineY;
                case ITEM, BLOCK -> legacyClient ? baseLineY - itemOffset : baseLineY;
            };

            final Object display = createDisplayEntity(level, baseLocation, line, legacyClient);
            if (display == null) continue;

            // Hook for per-player yaw (used by 1.19.4 and 1.21.11)
            applyHologramEntityYaw(display, baseLocation);

            final int displayId = getEntityId(display);
            newIds.add(displayId);

            sendPacket(handle, createAddEntityPacket(
                    displayId,
                    getEntityUUID(display),
                    lineX, lineY, lineZ,
                    getEntityXRot(display),
                    getEntityYRot(display),
                    getEntityType(display),
                    0,
                    0.0D, 0.0D, 0.0D,
                    getEntityYHeadRot(display)
            ));

            final List<?> values = getEntityDataValues(display);
            if (values != null && !values.isEmpty()) {
                sendPacket(handle, createEntityDataPacket(displayId, values));
            }

            if (line.type() == HologramLineType.ITEM || line.type() == HologramLineType.BLOCK) {
                sendPacket(handle, createSetEntityMotionPacket(displayId, 0.0D, 0.0D, 0.0D));
            }
        }

        this.packetHologramEntityIds.put(key, new PacketHologramState(List.copyOf(newIds), signatures, baseSignature, legacyClient));
    }

    @Override
    public void removePacketHologram(final Player player, final UUID hologramUniqueId) {
        final String key = sessionKey(player.getUniqueId(), hologramUniqueId);
        final PacketHologramState state = this.packetHologramEntityIds.remove(key);
        final List<Integer> ids = state == null ? List.of() : state.entityIds();
        if (ids == null || ids.isEmpty()) return;
        sendRemovePacket(player, ids);
    }

    @Override
    public void clearPacketHologramCacheForPlayer(final UUID playerUniqueId) {
        final String prefix = playerUniqueId + ":";
        this.packetHologramEntityIds.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public void upsertPacketBdEngineModel(
            final Player player,
            final UUID modelUniqueId,
            final Location baseLocation,
            final List<BdEngineDisplayPart> parts
    ) {
        if (baseLocation.getWorld() == null || parts == null || parts.isEmpty()) return;

        final String key = sessionKey(player.getUniqueId(), modelUniqueId);
        final PacketBdEngineModelState previous = this.packetBdEngineEntityIds.get(key);

        // Fast path: structural match against raw parts list — no intermediate PacketBaseSignature
        if (previous != null && !previous.entityIds().isEmpty()
                && previous.structurallyMatches(parts.size(), baseLocation)) {
            // Structural match — send entity data updates only
            final Object level = getServerLevel(baseLocation.getWorld());
            final Object handle = getServerPlayer(player);
            for (int i = 0; i < parts.size(); i++) {
                final Object display = createBdEngineDisplayDispatch(level, baseLocation, parts.get(i));
                if (display == null) continue;
                final List<?> values = getEntityDataValues(display);
                if (values != null && !values.isEmpty()) {
                    sendPacket(handle, createEntityDataPacket(previous.entityIds().get(i), values));
                }
            }
            // Cache state unchanged — no new PacketBdEngineModelState allocated
            return;
        }

        // Remove old
        if (previous != null && !previous.entityIds().isEmpty()) {
            sendRemovePacket(player, previous.entityIds());
        }

        // Full spawn: compute base signature for new state
        final PacketBaseSignature baseSignature = packetBaseSignature(baseLocation);

        final Object level = getServerLevel(baseLocation.getWorld());
        final Object handle = getServerPlayer(player);
        final List<Integer> entityIds = new ArrayList<>(parts.size());

        for (final BdEngineDisplayPart part : parts) {
            final Object display = createBdEngineDisplayDispatch(level, baseLocation, part);
            if (display == null) continue;

            final int displayId = getEntityId(display);
            entityIds.add(displayId);

            sendPacket(handle, createAddEntityPacket(
                    displayId,
                    getEntityUUID(display),
                    baseLocation.getX(), baseLocation.getY(), baseLocation.getZ(),
                    getEntityXRot(display),
                    getEntityYRot(display),
                    getEntityType(display),
                    0,
                    0.0D, 0.0D, 0.0D,
                    getEntityYHeadRot(display)
            ));

            final List<?> values = getEntityDataValues(display);
            if (values != null && !values.isEmpty()) {
                sendPacket(handle, createEntityDataPacket(displayId, values));
            }
        }

        this.packetBdEngineEntityIds.put(key, new PacketBdEngineModelState(List.copyOf(entityIds), baseSignature));
    }

    @Override
    public void removePacketBdEngineModel(final Player player, final UUID modelUniqueId) {
        final String key = sessionKey(player.getUniqueId(), modelUniqueId);
        final PacketBdEngineModelState state = this.packetBdEngineEntityIds.remove(key);
        if (state == null || state.entityIds().isEmpty()) return;
        sendRemovePacket(player, state.entityIds());
    }

    @Override
    public void clearPacketBdEngineModelCacheForPlayer(final UUID playerUniqueId) {
        final String prefix = playerUniqueId + ":";
        this.packetBdEngineEntityIds.keySet().removeIf(key -> key.startsWith(prefix));
    }

    // ============================================================
    // SHOW/FAKE BLOCK METHODS (identical across all versions)
    // ============================================================

    @Override
    public void sendBreakAnimation(final World world, final Location location, final int entityId, final int stage) {
        final int bx = (int) Math.floor(location.getX());
        final int by = (int) Math.floor(location.getY());
        final int bz = (int) Math.floor(location.getZ());
        final Object packet = createBlockDestructionPacket(entityId, bx, by, bz, stage);
        broadcastPacketToNearby(location, packet);
    }

    @Override
    public void showFakeBlock(final World world, final Location location, final Material material) {
        final int bx = (int) Math.floor(location.getX());
        final int by = (int) Math.floor(location.getY());
        final int bz = (int) Math.floor(location.getZ());
        final Object packet = createBlockUpdatePacket(bx, by, bz, material);
        broadcastPacketToNearby(location, packet);
    }

    @Override
    public void showFakeBlock(final Player player, final World world, final Location location, final Material material) {
        if (player == null) return;
        final int bx = (int) Math.floor(location.getX());
        final int by = (int) Math.floor(location.getY());
        final int bz = (int) Math.floor(location.getZ());
        final Object packet = createBlockUpdatePacket(bx, by, bz, material);
        sendPacket(getServerPlayer(player), packet);
    }

    @Override
    public void clearFakeBlock(final World world, final Location location) {
        if (!isOwnedByCurrentRegion(location)) return;
        try {
            final org.bukkit.block.data.BlockData realData = world.getBlockAt(location).getBlockData();
            for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
                viewer.sendBlockChange(location, realData);
            }
        } catch (final Exception e) {
            NmsLogger.debug("clearFakeBlock(World) failed at " + location + ": " + e.getMessage());
        }
    }

    @Override
    public void clearFakeBlock(final Player player, final World world, final Location location) {
        if (!isOwnedByCurrentRegion(location)) return;
        try {
            final org.bukkit.block.data.BlockData realData = world.getBlockAt(location).getBlockData();
            player.sendBlockChange(location, realData);
        } catch (final Exception e) {
            NmsLogger.debug("clearFakeBlock(Player) failed at " + location + ": " + e.getMessage());
        }
    }

    // ============================================================
    // SCHEMATIC LOADING (identical across all versions)
    // ============================================================

    @Override
    public SchematicData loadSchematic(final String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        final java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) return null;
        try {
            final Object tag = readNbtFromFile(filePath);
            if (tag == null) return null;
            return parseSchematicTag(tag);
        } catch (final Exception e) {
            NmsLogger.debug("Failed to load schematic '" + filePath + "': " + e.getMessage());
            return null;
        }
    }

    private SchematicData parseSchematicTag(final Object tag) {
        try {
            if (nbtContains(tag, "Version") && nbtContains(tag, "Palette") && nbtContains(tag, "BlockData")) {
                return parseSpongeSchematic(tag);
            }
            if (nbtContains(tag, "Blocks") && nbtContains(tag, "Data")) {
                return parseMceSchematic(tag);
            }
            return null;
        } catch (final Exception e) {
            NmsLogger.debug("Failed to parse schematic tag: " + e.getMessage());
            return null;
        }
    }

    private SchematicData parseSpongeSchematic(final Object tag) {
        final int width = nbtGetShort(tag, "Width");
        final int height = nbtGetShort(tag, "Height");
        final int length = nbtGetShort(tag, "Length");
        final Object palette = nbtGetCompound(tag, "Palette");
        final byte[] blockData = nbtGetByteArray(tag, "BlockData");

        if (width <= 0 || height <= 0 || length <= 0 || palette == null || blockData == null) {
            return null;
        }

        final Map<Integer, String> paletteMap = new HashMap<>();
        for (final String key : nbtGetKeys(palette)) {
            final int index = nbtGetInt(palette, key);
            final String materialName = extractMaterialName(key);
            if (materialName != null) {
                paletteMap.put(index, materialName);
            }
        }

        final List<SchematicBlock> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    final int dataIndex = y * width * length + z * width + x;
                    if (dataIndex >= blockData.length) continue;
                    final int paletteIndex = blockData[dataIndex] & 0xFF;
                    final String materialName = paletteMap.get(paletteIndex);
                    if (materialName == null || "AIR".equals(materialName)) continue;
                    blocks.add(new SchematicBlock(x, y, z, materialName));
                }
            }
        }

        return new SchematicData(width, height, length, blocks);
    }

    private SchematicData parseMceSchematic(final Object tag) {
        final int width = nbtGetShort(tag, "Width");
        final int height = nbtGetShort(tag, "Height");
        final int length = nbtGetShort(tag, "Length");
        final byte[] blocks = nbtGetByteArray(tag, "Blocks");
        final byte[] data = nbtGetByteArray(tag, "Data");

        if (width <= 0 || height <= 0 || length <= 0 || blocks == null) return null;

        final byte[] dataArr = (data != null && data.length == blocks.length) ? data : new byte[blocks.length];

        final List<SchematicBlock> result = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    final int index = y * width * length + z * width + x;
                    if (index >= blocks.length) continue;
                    final int blockId = blocks[index] & 0xFF;
                    final int blockData = dataArr[index] & 0xFF;
                    if (blockId == 0) continue;
                    final String materialName = legacyIdToMaterial(blockId, blockData);
                    if (materialName == null) continue;
                    result.add(new SchematicBlock(x, y, z, materialName));
                }
            }
        }

        return new SchematicData(width, height, length, result);
    }

    private static String extractMaterialName(final String blockStateKey) {
        if (blockStateKey == null || blockStateKey.isBlank()) return null;
        final String key = blockStateKey.contains("[")
                ? blockStateKey.substring(0, blockStateKey.indexOf('['))
                : blockStateKey;
        final String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        if (name == null || name.isBlank()) return null;
        return name.toUpperCase(Locale.ROOT);
    }

    private static String legacyIdToMaterial(final int blockId, final int data) {
        return switch (blockId) {
            case 1 -> "STONE";
            case 2 -> "GRASS_BLOCK";
            case 3 -> "DIRT";
            case 4 -> "COBBLESTONE";
            case 5 -> "OAK_PLANKS";
            case 6 -> "OAK_SAPLING";
            case 7 -> "BEDROCK";
            case 8, 9 -> "WATER";
            case 12 -> "SAND";
            case 13 -> "GRAVEL";
            case 14 -> "GOLD_ORE";
            case 15 -> "IRON_ORE";
            case 16 -> "COAL_ORE";
            case 17 -> "OAK_LOG";
            case 18 -> "OAK_LEAVES";
            case 19 -> "SPONGE";
            case 20 -> "GLASS";
            case 21 -> "LAPIS_ORE";
            case 24 -> "SANDSTONE";
            case 25 -> "NOTE_BLOCK";
            case 41 -> "GOLD_BLOCK";
            case 42 -> "IRON_BLOCK";
            case 45 -> "BRICK";
            case 46 -> "TNT";
            case 47 -> "BOOKSHELF";
            case 48 -> "MOSSY_COBBLESTONE";
            case 49 -> "OBSIDIAN";
            case 53 -> "OAK_STAIRS";
            case 56 -> "DIAMOND_ORE";
            case 57 -> "DIAMOND_BLOCK";
            case 58 -> "CRAFTING_TABLE";
            case 61 -> "FURNACE";
            case 73 -> "REDSTONE_ORE";
            case 78 -> "SNOW";
            case 79 -> "ICE";
            case 80 -> "SNOW_BLOCK";
            case 82 -> "CLAY";
            case 84 -> "JUKEBOX";
            case 86 -> "PUMPKIN";
            case 87 -> "NETHERRACK";
            case 88 -> "SOUL_SAND";
            case 89 -> "GLOWSTONE";
            case 95 -> "STAINED_GLASS";
            case 98 -> "STONE_BRICKS";
            case 103 -> "MELON";
            case 133 -> "EMERALD_BLOCK";
            case 152 -> "REDSTONE_BLOCK";
            case 153 -> "QUARTZ_BLOCK";
            case 155 -> "QUARTZ_PILLAR";
            case 159 -> "STAINED_HARDENED_CLAY";
            case 161 -> "ACACIA_LEAVES";
            case 162 -> "ACACIA_LOG";
            case 168 -> "PRISMARINE";
            case 169 -> "SEA_LANTERN";
            case 173 -> "COAL_BLOCK";
            case 179 -> "RED_SANDSTONE";
            case 198 -> "END_ROD";
            case 199 -> "CHORUS_PLANT";
            default -> blockId < 256 ? ("minecraft:" + blockId) : null;
        };
    }

    // ============================================================
    // INTERACTION SPAWN/REMOVE helpers (shared fallback logic)
    // ============================================================

    protected SpawnResult spawnInteractionViaBukkit(
            final World world,
            final Location location,
            final float width,
            final float height,
            final NamespacedKey uniqueIdKey,
            final UUID blockUniqueId,
            final String nmsFailure
    ) {
        try {
            final Interaction interaction = world.spawn(
                    location,
                    Interaction.class,
                    spawned -> configureInteraction(spawned, width, height, uniqueIdKey, blockUniqueId)
            );
            return SpawnResult.success(interaction.getUniqueId(), SpawnPath.NMS);
        } catch (final RuntimeException fallbackException) {
            return SpawnResult.failed(joinSpawnFailures(nmsFailure, "Bukkit spawn failed: " + fallbackException.getMessage()));
        }
    }

    protected static void configureInteraction(
            final Interaction interaction,
            final float width,
            final float height,
            final NamespacedKey uniqueIdKey,
            final UUID blockUniqueId
    ) {
        interaction.setInteractionWidth(Math.max(0.25F, width));
        interaction.setInteractionHeight(Math.max(0.25F, height));
        interaction.setResponsive(true);
        interaction.setPersistent(false);
        interaction.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, blockUniqueId.toString());
    }

    protected static String joinSpawnFailures(final String first, final String second) {
        if (first == null || first.isBlank()) return second == null ? "" : second;
        if (second == null || second.isBlank()) return first;
        return first + " | " + second;
    }

    protected static boolean isOwnedByCurrentRegion(final Location location) {
        if (location == null || location.getWorld() == null) return false;
        try {
            final java.lang.reflect.Method method = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return Boolean.TRUE.equals(method.invoke(null, location));
        } catch (final NoSuchMethodException ignored) {
            return true;
        } catch (final Exception e) {
            NmsLogger.debug("isOwnedByCurrentRegion failed at " + location + ": " + e.getMessage());
            return false;
        }
    }

    // ============================================================
    // DISPATCHER METHODS (delegate to hooks)
    // ============================================================

    private Object createDisplayEntity(
            final Object level,
            final Location base,
            final HologramLine line,
            final boolean legacyClient
    ) {
        final double x = base.getX();
        final double baseLineY = base.getY() - line.offsetY();
        final double z = base.getZ();
        final double itemOffset = legacyClient ? getLegacyItemOffset() : ITEM_ENTITY_Y_OFFSET_MODERN;
        return switch (line.type()) {
            case TEXT -> legacyClient
                    ? createLegacyArmorStandEntity(level, x, baseLineY - ARMOR_STAND_NAME_Y_OFFSET, z, line.text())
                    : createTextDisplayEntity(level, x, baseLineY, z, line.text());
            case ITEM -> legacyClient
                    ? createLegacyItemStandEntity(level, x, baseLineY - itemOffset, z, line.material())
                    : createItemDisplayEntity(level, x, baseLineY, z, line.material());
            case BLOCK -> legacyClient
                    ? createLegacyItemStandEntity(level, x, baseLineY - itemOffset, z, line.material())
                    : createBlockDisplayEntity(level, x, baseLineY, z, line.material());
        };
    }

    private Object createBdEngineDisplayDispatch(
            final Object level,
            final Location baseLocation,
            final BdEngineDisplayPart part
    ) {
        return switch (part.type()) {
            case ITEM -> createBdEngineItemDisplay(level, baseLocation, part);
            case TEXT -> createBdEngineTextDisplay(level, baseLocation, part);
            case BLOCK -> createBdEngineBlockDisplay(level, baseLocation, part);
        };
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    protected static float[] normalizeMatrix(final float[] values) {
        return values != null && values.length >= 16
                ? values
                : new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    protected String sessionKey(final UUID playerUniqueId, final UUID hologramUniqueId) {
        return playerUniqueId + ":" + hologramUniqueId;
    }

    private List<PacketLineSignature> packetLineSignatures(final List<HologramLine> lines) {
        final List<PacketLineSignature> signatures = new ArrayList<>(lines.size());
        for (final HologramLine line : lines) {
            final String content = switch (line.type()) {
                case TEXT -> line.text();
                case ITEM, BLOCK -> line.material() == null ? "" : line.material().name();
            };
            signatures.add(new PacketLineSignature(line.type(), line.offsetY(), content));
        }
        return signatures;
    }

    private PacketBaseSignature packetBaseSignature(final Location baseLocation) {
        return new PacketBaseSignature(
                baseLocation.getWorld().getName(),
                baseLocation.getX(),
                baseLocation.getY(),
                baseLocation.getZ()
        );
    }

    private void sendRemovePacket(final Player player, final List<Integer> ids) {
        final Object handle = getServerPlayer(player);
        if (handle == null) return;
        sendPacket(handle, createRemoveEntitiesPacket(ids.stream().mapToInt(Integer::intValue).toArray()));
    }

    private void broadcastPacketToNearby(final Location location, final Object packet) {
        final World world = location.getWorld();
        if (world == null) return;
        for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
            final Object handle = getServerPlayer(viewer);
            if (handle != null) {
                sendPacket(handle, packet);
            }
        }
    }

    // ============================================================
    // INNER TYPES (identical across all versions — moved here once)
    // ============================================================

    protected record PacketHologramState(
            List<Integer> entityIds,
            List<PacketLineSignature> signatures,
            PacketBaseSignature baseSignature,
            boolean legacyClient
    ) {
        /**
         * Fast structural match against raw spawn parameters — avoids creating
         * intermediate {@link PacketLineSignature} and {@link PacketBaseSignature}
         * objects in the common case where the hologram hasn't changed.
         */
        public boolean structurallyMatches(
                final List<HologramLine> newLines,
                final Location newLocation,
                final boolean newLegacyClient
        ) {
            if (this.entityIds.size() != newLines.size() || this.legacyClient != newLegacyClient) {
                return false;
            }
            if (!this.baseSignature.matches(newLocation)) {
                return false;
            }
            for (int i = 0; i < this.signatures.size(); i++) {
                if (!this.signatures.get(i).matchesLine(newLines.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    protected record PacketBaseSignature(String worldName, double x, double y, double z) {
        /** Fast match against a raw Location — no intermediate allocation. */
        public boolean matches(final Location loc) {
            if (loc.getWorld() == null) return false;
            return this.worldName.equals(loc.getWorld().getName())
                    && Double.compare(this.x, loc.getX()) == 0
                    && Double.compare(this.y, loc.getY()) == 0
                    && Double.compare(this.z, loc.getZ()) == 0;
        }
    }

    protected record PacketBdEngineModelState(List<Integer> entityIds, PacketBaseSignature baseSignature) {
        /** Returns true if the number of parts and base location match. */
        public boolean structurallyMatches(final int partCount, final Location newLocation) {
            return this.entityIds.size() == partCount && this.baseSignature.matches(newLocation);
        }
    }

    protected record PacketLineSignature(HologramLineType type, double offsetY, String content) {
        /** Fast match against a raw HologramLine — avoids creating a PacketLineSignature. */
        public boolean matchesLine(final HologramLine line) {
            if (this.type != line.type() || Double.compare(this.offsetY, line.offsetY()) != 0) {
                return false;
            }
            // TEXT lines match by structure (type + offsetY) only — content can change without respawn
            if (this.type == HologramLineType.TEXT) {
                return true;
            }
            final String newContent = line.material() == null ? "" : line.material().name();
            return this.content.equals(newContent);
        }
    }
}
