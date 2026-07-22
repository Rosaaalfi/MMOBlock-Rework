package me.chyxelmc.mmoblock.nms.v26_1;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.CompoundTag;
import me.chyxelmc.mmoblock.nms.SchematicData.SchematicBlock;
import me.chyxelmc.mmoblock.nms.SchematicData;
import org.joml.Matrix4f;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.math.Transformation;
import me.chyxelmc.mmoblock.nms.utils.ClientProtocolUtils;
import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.resources.ResourceKey;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.persistence.PersistentDataType;
import me.chyxelmc.mmoblock.nms.utils.HologramColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("java:S101")
public final class NmsAdapter_v26_1 implements NmsAdapter {

    private static final int TEXT_DISPLAY_INTERPOLATION_DURATION = 2;
    private static final EntityType<net.minecraft.world.entity.Interaction> CUSTOM_INTERACTION_TYPE = createCustomInteractionType();
    private static final double ARMOR_STAND_NAME_Y_OFFSET = 0.23D;
    private static final double ITEM_ENTITY_Y_OFFSET_MODERN = 0.1D;
    private static final double ITEM_ENTITY_Y_OFFSET_LEGACY = 0.16D;

    private final Map<String, PacketHologramState> packetHologramEntityIds = new ConcurrentHashMap<>();
    private final Map<String, PacketBdEngineModelState> packetBdEngineEntityIds = new ConcurrentHashMap<>();

    @Override
    public String targetMinecraftVersion() {
        // Cukup kembalikan String tunggal dengan pemisah koma tanpa spasi
        return "26,26.1,26.1.1,26.1.2";
    }

    @Override
    public void validateNms() {
        Component.literal("MMOBlock").getString();
    }

    @Override
    public void sendSystemMessage(final Player player, final String message) {
        try {
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            handle.sendSystemMessage(Component.literal(message));
        } catch (final RuntimeException ex) {
            player.sendMessage(message);
        }
    }

    @Override
    public void applyEntityGlow(final Player player, final Entity entity, final String colorName) {
        if (!(player instanceof CraftPlayer craftPlayer) || !(entity instanceof CraftEntity craftEntity)) {
            NmsAdapter.super.applyEntityGlow(player, entity, colorName);
            return;
        }
        final ChatColor chatColor = NmsAdapter.resolveGlowChatColor(colorName);
        if (chatColor == null) {
            return;
        }
        try {
            final net.minecraft.world.entity.Entity handle = craftEntity.getHandle();
            handle.setGlowingTag(true);
            final String teamName = "mmbg" + Integer.toString(Math.max(0, entity.getEntityId()), 36);
            final PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
            team.setColor(ChatFormatting.getByCode(chatColor.getChar()));
            team.setCollisionRule(Team.CollisionRule.NEVER);
            craftPlayer.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            craftPlayer.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team,
                    entity.getUniqueId().toString(),
                    ClientboundSetPlayerTeamPacket.Action.ADD
            ));
        } catch (final RuntimeException exception) {
            NmsAdapter.super.applyEntityGlow(player, entity, colorName);
        }
    }

    @Override
    public SpawnResult spawnInteraction(
            final World world,
            final Location location,
            final float width,
            final float height,
            final NamespacedKey uniqueIdKey,
            final UUID blockUniqueId
    ) {
        final SpawnResult bukkitResult = spawnInteractionViaBukkit(world, location, width, height, uniqueIdKey, blockUniqueId, "");
        if (bukkitResult.success()) {
            return bukkitResult;
        }

        try {
            final ServerLevel level = ((CraftWorld) world).getHandle();
            final OptimizedInteraction handle = new OptimizedInteraction(
                    CUSTOM_INTERACTION_TYPE,
                    level
            );
            handle.setPos(location.getX(), location.getY(), location.getZ());
            handle.setNoGravity(true);
            handle.setSilent(true);
            applyCustomAabb(handle, location, width, height);
            level.addFreshEntity(handle);

            if (!(handle.getBukkitEntity() instanceof Interaction interaction)) {
                handle.discard();
                return SpawnResult.failed("Spawned NMS entity is not Bukkit Interaction");
            }

            configureInteraction(interaction, width, height, uniqueIdKey, blockUniqueId);
            return SpawnResult.success(interaction.getUniqueId(), SpawnPath.NMS);
        } catch (final RuntimeException exception) {
            return SpawnResult.failed(joinSpawnFailures(bukkitResult.reason(), "NMS spawn failed: " + exception.getMessage()));
        }
    }

    @Override
    public RemoveResult removeInteraction(final World world, final UUID interactionUniqueId) {
        try {
            final ServerLevel level = ((CraftWorld) world).getHandle();
            final net.minecraft.world.entity.Entity entity = level.getEntity(interactionUniqueId);
            if (entity == null) {
                return RemoveResult.success(false, SpawnPath.NMS);
            }
            entity.discard();
            return RemoveResult.success(true, SpawnPath.NMS);
        } catch (final RuntimeException exception) {
            return RemoveResult.failed("NMS remove failed: " + exception.getMessage());
        }
    }

    @Override
    public void sendBreakAnimation(final World world, final Location location, final int entityId, final int stage) {
        final BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        final ClientboundBlockDestructionPacket packet = new ClientboundBlockDestructionPacket(entityId, pos, stage);
        for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
            if (!(viewer instanceof CraftPlayer craftPlayer)) {
                continue;
            }
            craftPlayer.getHandle().connection.send(packet);
        }
    }

    @Override
    public void showFakeBlock(final World world, final Location location, final Material material) {
        final BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        final ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, CraftMagicNumbers.getBlock(material).defaultBlockState());
        for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
            if (viewer instanceof CraftPlayer craftPlayer) {
                craftPlayer.getHandle().connection.send(packet);
            }
        }
    }

    @Override
    public void showFakeBlock(final Player player, final World world, final Location location, final Material material) {
        final BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        final ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, CraftMagicNumbers.getBlock(material).defaultBlockState());
        if (player instanceof CraftPlayer craftPlayer) {
            craftPlayer.getHandle().connection.send(packet);
        }
    }

    @Override
    public void clearFakeBlock(final World world, final Location location) {
        if (!isOwnedByCurrentRegion(location)) {
            return;
        }
        try {
            final org.bukkit.block.data.BlockData realData = world.getBlockAt(location).getBlockData();
            for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
                viewer.sendBlockChange(location, realData);
            }
        } catch (final Exception ignored) {
            // World data may already be tearing down or owned by another Folia region.
        }
    }

    @Override
    public void clearFakeBlock(final Player player, final World world, final Location location) {
        if (!isOwnedByCurrentRegion(location)) {
            return;
        }
        try {
            final org.bukkit.block.data.BlockData realData = world.getBlockAt(location).getBlockData();
            player.sendBlockChange(location, realData);
        } catch (final Exception ignored) {
            // World data may already be tearing down or owned by another Folia region.
        }
    }

    private SpawnResult spawnInteractionViaBukkit(
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

    private static void configureInteraction(
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

    private static String joinSpawnFailures(final String first, final String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + " | " + second;
    }

    private static boolean isOwnedByCurrentRegion(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            final java.lang.reflect.Method method = org.bukkit.Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return Boolean.TRUE.equals(method.invoke(null, location));
        } catch (final NoSuchMethodException ignored) {
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }
    @Override
    public boolean supportsPacketHolograms() {
        return true;
    }

    @Override
    public void upsertPacketHologram(final Player player, final UUID hologramUniqueId, final Location baseLocation, final List<HologramLine> lines) {
        if (!(player instanceof CraftPlayer craftPlayer) || baseLocation.getWorld() == null) {
            return;
        }

        final ServerPlayer handle = craftPlayer.getHandle();
        final ServerLevel level = ((CraftWorld) baseLocation.getWorld()).getHandle();
        final String key = sessionKey(player.getUniqueId(), hologramUniqueId);
        final PacketHologramState previous = this.packetHologramEntityIds.get(key);
        final List<PacketLineSignature> signatures = packetLineSignatures(lines);
        final PacketBaseSignature baseSignature = packetBaseSignature(baseLocation);
        final boolean legacyClient = ClientProtocolUtils.isLegacyClientBelow_1_19_4(player);

        if (previous != null && previous.matches(signatures, lines.size(), baseSignature, legacyClient) && !previous.entityIds().isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                final net.minecraft.world.entity.Entity display = createDisplay(level, baseLocation, lines.get(i), legacyClient);
                if (display == null) {
                    continue;
                }

                final List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
                if (values != null && !values.isEmpty()) {
                    handle.connection.send(new ClientboundSetEntityDataPacket(previous.entityIds().get(i), values));
                }
            }
            this.packetHologramEntityIds.put(key, new PacketHologramState(previous.entityIds(), signatures, baseSignature, legacyClient));
            return;
        }

        if (previous != null && !previous.entityIds().isEmpty()) {
            handle.connection.send(new ClientboundRemoveEntitiesPacket(previous.entityIds().stream().mapToInt(Integer::intValue).toArray()));
        }

        final List<Integer> newIds = new ArrayList<>();
        for (final HologramLine line : lines) {
            final double lineX = baseLocation.getX();
            final double baseLineY = baseLocation.getY() - line.offsetY();
            final double lineZ = baseLocation.getZ();
            final double itemOffset = legacyClient ? ITEM_ENTITY_Y_OFFSET_LEGACY : ITEM_ENTITY_Y_OFFSET_MODERN;
            final double lineY = switch (line.type()) {
                case TEXT -> legacyClient ? baseLineY - ARMOR_STAND_NAME_Y_OFFSET : baseLineY;
                case ITEM, BLOCK -> baseLineY - itemOffset;
            };
            final net.minecraft.world.entity.Entity display = createDisplay(level, baseLocation, line, legacyClient);
            if (display == null) {
                continue;
            }

            newIds.add(display.getId());
            handle.connection.send(new ClientboundAddEntityPacket(
                    display.getId(),
                    display.getUUID(),
                    lineX,
                    lineY,
                    lineZ,
                    display.getXRot(),
                    display.getYRot(),
                    display.getType(),
                    0,
                    Vec3.ZERO,
                    display.getYHeadRot()
            ));
            final List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
            if (values != null && !values.isEmpty()) {
                handle.connection.send(new ClientboundSetEntityDataPacket(display.getId(), values));
            }
            if (line.type() == NmsAdapter.HologramLineType.ITEM || line.type() == NmsAdapter.HologramLineType.BLOCK) {
                handle.connection.send(new ClientboundSetEntityMotionPacket(display.getId(), Vec3.ZERO));
            }
        }
        this.packetHologramEntityIds.put(key, new PacketHologramState(List.copyOf(newIds), signatures, baseSignature, legacyClient));
    }

    @Override
    public void removePacketHologram(final Player player, final UUID hologramUniqueId) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return;
        }

        final String key = sessionKey(player.getUniqueId(), hologramUniqueId);
        final PacketHologramState state = this.packetHologramEntityIds.remove(key);
        final List<Integer> ids = state == null ? List.of() : state.entityIds();
        if (ids == null || ids.isEmpty()) {
            return;
        }
        craftPlayer.getHandle().connection.send(new ClientboundRemoveEntitiesPacket(ids.stream().mapToInt(Integer::intValue).toArray()));
    }

    @Override
    public void clearPacketHologramCacheForPlayer(final UUID playerUniqueId) {
        final String prefix = playerUniqueId + ":";
        this.packetHologramEntityIds.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @Override
    public boolean supportsPacketBdEngineModels() {
        return true;
    }

    @Override
    public void upsertPacketBdEngineModel(
            final Player player,
            final UUID modelUniqueId,
            final Location baseLocation,
            final List<NmsAdapter.BdEngineDisplayPart> parts
    ) {
        if (!(player instanceof CraftPlayer craftPlayer) || baseLocation.getWorld() == null || parts == null || parts.isEmpty()) {
            return;
        }

        final String key = sessionKey(player.getUniqueId(), modelUniqueId);
        final PacketBdEngineModelState previous = this.packetBdEngineEntityIds.get(key);
        final PacketBaseSignature baseSignature = packetBaseSignature(baseLocation);
        final ServerPlayer handle = craftPlayer.getHandle();
        final ServerLevel level = ((CraftWorld) baseLocation.getWorld()).getHandle();

        if (previous != null && previous.entityIds().size() == parts.size() && previous.baseSignature().equals(baseSignature)) {
            for (int i = 0; i < parts.size(); i++) {
                final net.minecraft.world.entity.Entity display = createBdEngineDisplay(level, baseLocation, parts.get(i));
                if (display == null) {
                    continue;
                }
                final List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
                if (values != null && !values.isEmpty()) {
                    handle.connection.send(new ClientboundSetEntityDataPacket(previous.entityIds().get(i), values));
                }
            }
            return;
        }

        if (previous != null && !previous.entityIds().isEmpty()) {
            handle.connection.send(new ClientboundRemoveEntitiesPacket(previous.entityIds().stream().mapToInt(Integer::intValue).toArray()));
        }

        final List<Integer> entityIds = new ArrayList<>(parts.size());
        for (final NmsAdapter.BdEngineDisplayPart part : parts) {
            final net.minecraft.world.entity.Entity display = createBdEngineDisplay(level, baseLocation, part);
            if (display == null) {
                continue;
            }
            entityIds.add(display.getId());
            handle.connection.send(new ClientboundAddEntityPacket(
                    display.getId(),
                    display.getUUID(),
                    baseLocation.getX(),
                    baseLocation.getY(),
                    baseLocation.getZ(),
                    display.getXRot(),
                    display.getYRot(),
                    display.getType(),
                    0,
                    Vec3.ZERO,
                    display.getYHeadRot()
            ));
            final List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
            if (values != null && !values.isEmpty()) {
                handle.connection.send(new ClientboundSetEntityDataPacket(display.getId(), values));
            }
        }
        this.packetBdEngineEntityIds.put(key, new PacketBdEngineModelState(List.copyOf(entityIds), baseSignature));
    }

    @Override
    public void removePacketBdEngineModel(final Player player, final UUID modelUniqueId) {
        if (!(player instanceof CraftPlayer craftPlayer)) {
            return;
        }
        final String key = sessionKey(player.getUniqueId(), modelUniqueId);
        final PacketBdEngineModelState state = this.packetBdEngineEntityIds.remove(key);
        if (state == null || state.entityIds().isEmpty()) {
            return;
        }
        craftPlayer.getHandle().connection.send(new ClientboundRemoveEntitiesPacket(state.entityIds().stream().mapToInt(Integer::intValue).toArray()));
    }

    @Override
    public void clearPacketBdEngineModelCacheForPlayer(final UUID playerUniqueId) {
        final String prefix = playerUniqueId + ":";
        this.packetBdEngineEntityIds.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private net.minecraft.world.entity.Entity createBdEngineDisplay(
            final ServerLevel level,
            final Location baseLocation,
            final NmsAdapter.BdEngineDisplayPart part
    ) {
        if (part.type() == NmsAdapter.BdEngineDisplayType.ITEM) {
            return createBdEngineItemDisplay(level, baseLocation, part);
        }
        if (part.type() == NmsAdapter.BdEngineDisplayType.TEXT) {
            return createBdEngineTextDisplay(level, baseLocation, part);
        }
        return createBdEngineBlockDisplay(level, baseLocation, part);
    }

    private Display.BlockDisplay createBdEngineBlockDisplay(
            final ServerLevel level,
            final Location baseLocation,
            final NmsAdapter.BdEngineDisplayPart part
    ) {
        if (part.material() == null || !part.material().isBlock()) {
            return null;
        }
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(baseLocation.getX(), baseLocation.getY(), baseLocation.getZ());
        display.setBlockState(CraftMagicNumbers.getBlock(part.material()).defaultBlockState());
        configureBdEngineDisplay(display, part);
        return display;
    }

    private Display.ItemDisplay createBdEngineItemDisplay(
            final ServerLevel level,
            final Location baseLocation,
            final NmsAdapter.BdEngineDisplayPart part
    ) {
        if (part.material() == null) {
            return null;
        }
        final Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(baseLocation.getX(), baseLocation.getY(), baseLocation.getZ());
        display.setItemStack(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(part.material())));
        display.setItemTransform(ItemDisplayContext.NONE);
        configureBdEngineDisplay(display, part);
        return display;
    }

    private Display.TextDisplay createBdEngineTextDisplay(
            final ServerLevel level,
            final Location baseLocation,
            final NmsAdapter.BdEngineDisplayPart part
    ) {
        final Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setPos(baseLocation.getX(), baseLocation.getY(), baseLocation.getZ());
        display.setText(parseVanillaText(part.text() == null ? "" : part.text()));
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        configureBdEngineDisplay(display, part);
        return display;
    }

    private void configureBdEngineDisplay(final Display display, final NmsAdapter.BdEngineDisplayPart part) {
        display.setTransformation(new Transformation(toMatrix(part.matrix())));
        display.setTransformationInterpolationDelay(-1);
        display.setTransformationInterpolationDuration(2);
        display.setViewRange(1.0F);
        display.setWidth(4.0F);
        display.setHeight(4.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setBrightnessOverride(new Brightness(
                Math.max(0, Math.min(15, part.blockLight())),
                Math.max(0, Math.min(15, part.skyLight()))
        ));
    }

    private Matrix4f toMatrix(final float[] values) {
        final float[] safe = values != null && values.length >= 16
                ? values
                : new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        return new Matrix4f(
                safe[0], safe[1], safe[2], safe[3],
                safe[4], safe[5], safe[6], safe[7],
                safe[8], safe[9], safe[10], safe[11],
                safe[12], safe[13], safe[14], safe[15]
        );
    }

    @Override
    public SchematicData loadSchematic(final String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        final java.io.File file = new java.io.File(filePath);
        if (!file.exists() || !file.isFile()) return null;
        try {
            final CompoundTag tag = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
            return parseSchematicTag(tag);
        } catch (final Exception ignored) {
            return null;
        }
    }

    private SchematicData parseSchematicTag(final CompoundTag tag) {
        try {
            if (tag.contains("Version") && tag.contains("Palette") && tag.contains("BlockData")) {
                return parseSpongeSchematic(tag);
            }
            if (tag.contains("Blocks") && tag.contains("Data")) {
                return parseMceSchematic(tag);
            }
            return null;
        } catch (final Exception ignored) {
            return null;
        }
    }

    private SchematicData parseSpongeSchematic(final CompoundTag tag) {
        final int width = tag.getShort("Width").orElse((short) 0);
        final int height = tag.getShort("Height").orElse((short) 0);
        final int length = tag.getShort("Length").orElse((short) 0);
        final CompoundTag palette = tag.getCompound("Palette").orElse(null);
        final byte[] blockData = tag.getByteArray("BlockData").orElse(null);

        if (width <= 0 || height <= 0 || length <= 0 || palette == null || blockData == null) {
            return null;
        }

        final java.util.Map<Integer, String> paletteMap = new java.util.HashMap<>();
        for (final String key : palette.keySet()) {
            final int index = palette.getInt(key).orElse(0);
            final String materialName = extractMaterialName(key);
            if (materialName != null) {
                paletteMap.put(index, materialName);
            }
        }

        final java.util.List<SchematicBlock> blocks = new java.util.ArrayList<>();
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

    private SchematicData parseMceSchematic(final CompoundTag tag) {
        final int width = tag.getShort("Width").orElse((short) 0);
        final int height = tag.getShort("Height").orElse((short) 0);
        final int length = tag.getShort("Length").orElse((short) 0);
        final byte[] blocks = tag.getByteArray("Blocks").orElse(null);
        final byte[] data = tag.getByteArray("Data").orElse(null);

        if (width <= 0 || height <= 0 || length <= 0 || blocks == null) return null;

        final byte[] dataArr = (data != null && data.length == blocks.length) ? data : new byte[blocks.length];

        final java.util.List<SchematicBlock> result = new java.util.ArrayList<>();
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
        final String key = blockStateKey.contains("[") ? blockStateKey.substring(0, blockStateKey.indexOf('[')) : blockStateKey;
        final String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        if (name == null || name.isBlank()) return null;
        return name.toUpperCase(java.util.Locale.ROOT);
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
            case 8 -> "WATER";
            case 9 -> "WATER";
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

    private net.minecraft.world.entity.Entity createDisplay(
            final ServerLevel level,
            final Location base,
            final HologramLine line,
            final boolean legacyClient
    ) {
        final double x = base.getX();
        final double baseLineY = base.getY() - line.offsetY();
        final double z = base.getZ();
        final double itemOffset = legacyClient ? ITEM_ENTITY_Y_OFFSET_LEGACY : ITEM_ENTITY_Y_OFFSET_MODERN;
        return switch (line.type()) {
            case TEXT -> legacyClient
                    ? createLegacyArmorStandText(level, x, baseLineY - ARMOR_STAND_NAME_Y_OFFSET, z, line.text())
                    : createTextDisplay(level, x, baseLineY, z, line.text());
            case ITEM -> createItemDisplay(level, x, baseLineY - itemOffset, z, line.material());
            case BLOCK -> createBlockDisplay(level, x, baseLineY - itemOffset, z, line.material());
        };
    }

    private net.minecraft.world.entity.Entity createLegacyArmorStandText(
            final ServerLevel level,
            final double x,
            final double y,
            final double z,
            final String text
    ) {
        final ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setPos(x, y, z);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setNoBasePlate(true);
        stand.setMarker(true);
        final String safeText = text == null ? "" : text;
        final net.kyori.adventure.text.Component advComp = HologramColorUtil.toComponent(safeText);
        stand.setCustomName(PaperAdventure.asVanilla(advComp));
        stand.setCustomNameVisible(true);
        return stand;
    }

    private net.minecraft.world.entity.Entity createTextDisplay(final ServerLevel level, final double x, final double y, final double z, final String text) {
        final Display.TextDisplay display = new Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
        display.setPos(x, y, z);
        display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        display.setTransformationInterpolationDelay(0);
        display.setTransformationInterpolationDuration(TEXT_DISPLAY_INTERPOLATION_DURATION);
        display.setViewRange(0.4F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        display.setText(parseVanillaText(text));
        return display;
    }

    private net.minecraft.world.entity.Entity createItemDisplay(final ServerLevel level, final double x, final double y, final double z, final Material material) {
        if (material == null) {
            return null;
        }
        final StaticItemEntity itemEntity = new StaticItemEntity(
                level, x, y, z,
                CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material))
        );
        itemEntity.setPos(x, y, z);
        return itemEntity;
    }

    private net.minecraft.world.entity.Entity createBlockDisplay(final ServerLevel level, final double x, final double y, final double z, final Material material) {
        if (material == null) {
            return null;
        }
        return createItemDisplay(level, x, y, z, material);
    }

    private Component parseVanillaText(final String text) {
        return PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text));
    }

    private String sessionKey(final UUID playerUniqueId, final UUID hologramUniqueId) {
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
        return new PacketBaseSignature(baseLocation.getWorld().getName(), baseLocation.getX(), baseLocation.getY(), baseLocation.getZ());
    }

    private void applyCustomAabb(
            final net.minecraft.world.entity.Interaction handle,
            final Location location,
            final float width,
            final float height
    ) {
        final double half = width / 2.0D;
        final double minX = location.getX() - half;
        final double minY = location.getY();
        final double minZ = location.getZ() - half;
        final double maxX = location.getX() + half;
        final double maxY = location.getY() + height;
        final double maxZ = location.getZ() + half;
        handle.setBoundingBox(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static EntityType<net.minecraft.world.entity.Interaction> createCustomInteractionType() {
        try {
            final ResourceKey<EntityType<?>> key = BuiltInRegistries.ENTITY_TYPE
                    .getResourceKey(EntityType.INTERACTION)
                    .orElseThrow();
            return EntityType.Builder
                    .of(net.minecraft.world.entity.Interaction::new, MobCategory.MISC)
                    .clientTrackingRange(2)
                    .build(key);
        } catch (final RuntimeException ignored) {
            return EntityType.INTERACTION;
        }
    }

    private record PacketHologramState(
            List<Integer> entityIds,
            List<PacketLineSignature> signatures,
            PacketBaseSignature baseSignature,
            boolean legacyClient
    ) {

        private boolean matches(
                final List<PacketLineSignature> otherSignatures,
                final int lineCount,
                final PacketBaseSignature otherBaseSignature,
                final boolean otherLegacyClient
        ) {
            return this.entityIds.size() == lineCount
                    && structurallyMatches(otherSignatures)
                    && this.baseSignature.equals(otherBaseSignature)
                    && this.legacyClient == otherLegacyClient;
        }

        private boolean structurallyMatches(final List<PacketLineSignature> otherSignatures) {
            if (this.signatures.size() != otherSignatures.size()) {
                return false;
            }
            for (int i = 0; i < this.signatures.size(); i++) {
                if (!this.signatures.get(i).matchesEntityStructure(otherSignatures.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private record PacketBaseSignature(String worldName, double x, double y, double z) {
    }

    private record PacketBdEngineModelState(List<Integer> entityIds, PacketBaseSignature baseSignature) {
    }

    private record PacketLineSignature(NmsAdapter.HologramLineType type, double offsetY, String content) {

        private boolean matchesEntityStructure(final PacketLineSignature other) {
            if (other == null || this.type != other.type || Double.compare(this.offsetY, other.offsetY) != 0) {
                return false;
            }
            return this.type == NmsAdapter.HologramLineType.TEXT || java.util.Objects.equals(this.content, other.content);
        }
    }

    private static final class OptimizedInteraction extends net.minecraft.world.entity.Interaction {

        private OptimizedInteraction(
                final net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Interaction> type,
                final Level level
        ) {
            super(type, level);
        }

        @Override
        public void tick() {
            // Intentionally no-op to avoid per-tick CPU work for static interaction hitboxes.
        }

        @Override
        public void inactiveTick() {
            // Intentionally no-op while chunk is inactive.
        }
    }

    private static final class StaticItemEntity extends ItemEntity {

        private StaticItemEntity(
                final Level level,
                final double x,
                final double y,
                final double z,
                final net.minecraft.world.item.ItemStack item
        ) {
            super(level, x, y, z, item);
            this.setNoGravity(true);
            this.setSilent(true);
            this.setNeverPickUp();
            this.age = 6000;
            this.setDeltaMovement(Vec3.ZERO);
        }

        @Override
        public void tick() {
            // Intentionally no-op — suppresses bobbing animation and all physics updates.
        }

        @Override
        public void inactiveTick() {
            // Intentionally no-op while chunk is inactive.
        }
    }
}
