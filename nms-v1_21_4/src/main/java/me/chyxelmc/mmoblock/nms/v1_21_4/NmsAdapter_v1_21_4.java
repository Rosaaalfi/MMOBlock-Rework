package me.chyxelmc.mmoblock.nms.v1_21_4;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceKey;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsAdapter_v1_21_4 implements NmsAdapter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final int TEXT_DISPLAY_INTERPOLATION_DURATION = 2;
    private static final EntityType<net.minecraft.world.entity.Interaction> CUSTOM_INTERACTION_TYPE = createCustomInteractionType();

    private static final Map<Character, String> LEGACY_TO_MINI_MESSAGE = Map.ofEntries(
        Map.entry('0', "<black>"),
        Map.entry('1', "<dark_blue>"),
        Map.entry('2', "<dark_green>"),
        Map.entry('3', "<dark_aqua>"),
        Map.entry('4', "<dark_red>"),
        Map.entry('5', "<dark_purple>"),
        Map.entry('6', "<gold>"),
        Map.entry('7', "<gray>"),
        Map.entry('8', "<dark_gray>"),
        Map.entry('9', "<blue>"),
        Map.entry('a', "<green>"),
        Map.entry('b', "<aqua>"),
        Map.entry('c', "<red>"),
        Map.entry('d', "<light_purple>"),
        Map.entry('e', "<yellow>"),
        Map.entry('f', "<white>"),
        Map.entry('k', "<obfuscated>"),
        Map.entry('l', "<bold>"),
        Map.entry('m', "<strikethrough>"),
        Map.entry('n', "<underlined>"),
        Map.entry('o', "<italic>"),
        Map.entry('r', "<reset>")
    );

    private final Map<String, PacketHologramState> packetHologramEntityIds = new ConcurrentHashMap<>();

    @Override
    public String targetMinecraftVersion() {
        return "1.21.4";
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
    public SpawnResult spawnInteraction(
        final World world,
        final Location location,
        final float width,
        final float height,
        final NamespacedKey uniqueIdKey,
        final UUID blockUniqueId
    ) {
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

            interaction.setInteractionWidth(Math.max(0.25F, width));
            interaction.setInteractionHeight(Math.max(0.25F, height));
            interaction.setResponsive(true);
            interaction.setPersistent(false);
            interaction.getPersistentDataContainer().set(uniqueIdKey, PersistentDataType.STRING, blockUniqueId.toString());
            return SpawnResult.success(interaction.getUniqueId(), SpawnPath.NMS);
        } catch (final RuntimeException exception) {
            return SpawnResult.failed("NMS spawn failed: " + exception.getMessage());
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
    public void clearFakeBlock(final World world, final Location location) {
        final ServerLevel level = ((CraftWorld) world).getHandle();
        final BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        final ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos));
        for (final Player viewer : world.getNearbyPlayers(location, 128.0D)) {
            if (viewer instanceof CraftPlayer craftPlayer) {
                craftPlayer.getHandle().connection.send(packet);
            }
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

        if (previous != null && previous.matches(signatures, lines.size(), baseSignature) && !previous.entityIds().isEmpty()) {
            for (int i = 0; i < lines.size(); i++) {
                final net.minecraft.world.entity.Entity display = createDisplay(level, baseLocation, lines.get(i));
                if (display == null) {
                    continue;
                }

                final List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
                if (values != null && !values.isEmpty()) {
                    handle.connection.send(new ClientboundSetEntityDataPacket(previous.entityIds().get(i), values));
                }
            }
            this.packetHologramEntityIds.put(key, new PacketHologramState(previous.entityIds(), signatures, baseSignature));
            return;
        }

        if (previous != null && !previous.entityIds().isEmpty()) {
            handle.connection.send(new ClientboundRemoveEntitiesPacket(previous.entityIds().stream().mapToInt(Integer::intValue).toArray()));
        }

        final List<Integer> newIds = new ArrayList<>();
        for (final HologramLine line : lines) {
            final net.minecraft.world.entity.Entity display = createDisplay(level, baseLocation, line);
            if (display == null) {
                continue;
            }

            newIds.add(display.getId());
            handle.connection.send(new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                display.getX(),
                display.getY(),
                display.getZ(),
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
        this.packetHologramEntityIds.put(key, new PacketHologramState(List.copyOf(newIds), signatures, baseSignature));
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

    private net.minecraft.world.entity.Entity createDisplay(final ServerLevel level, final Location base, final HologramLine line) {
        final double x = base.getX();
        final double y = base.getY() - line.offsetY();
        final double z = base.getZ();
        return switch (line.type()) {
            case TEXT -> createTextDisplay(level, x, y, z, line.text());
            case ITEM -> createItemDisplay(level, x, y, z, line.material());
            case BLOCK -> createBlockDisplay(level, x, y, z, line.material());
        };
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
        final ItemEntity itemEntity = new ItemEntity(level, x, y, z, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
        itemEntity.setNoGravity(true);
        itemEntity.setSilent(true);
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
        final String safeText = text == null ? "" : text;
        if (safeText.isEmpty()) {
            return Component.empty();
        }

        if (safeText.indexOf('§') >= 0 && safeText.indexOf('<') < 0) {
            return PaperAdventure.asVanilla(LEGACY_SECTION.deserialize(safeText));
        }

        final String miniMessageText = ampersandToMiniMessage(safeText);
        try {
            return PaperAdventure.asVanilla(MINI_MESSAGE.deserialize(miniMessageText));
        } catch (final RuntimeException exception) {
            return PaperAdventure.asVanilla(LEGACY_SECTION.deserialize(safeText));
        }
    }

    private String ampersandToMiniMessage(final String input) {
        if (input.isEmpty()) {
            return input;
        }

        final StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            final char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                final char next = Character.toLowerCase(input.charAt(i + 1));
                if (next == '#' && i + 7 < input.length()) {
                    final String hex = input.substring(i + 2, i + 8);
                    if (hex.matches("[0-9a-fA-F]{6}")) {
                        out.append("<#").append(hex.toLowerCase(Locale.ROOT)).append('>');
                        i += 7;
                        continue;
                    }
                }

                final String tag = LEGACY_TO_MINI_MESSAGE.get(next);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String sessionKey(final UUID playerUniqueId, final UUID hologramUniqueId) {
        return playerUniqueId + ":" + hologramUniqueId;
    }

    private List<PacketLineSignature> packetLineSignatures(final List<HologramLine> lines) {
        final List<PacketLineSignature> signatures = new ArrayList<>(lines.size());
        for (final HologramLine line : lines) {
            signatures.add(new PacketLineSignature(line.type(), line.offsetY()));
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


    private record PacketHologramState(List<Integer> entityIds, List<PacketLineSignature> signatures, PacketBaseSignature baseSignature) {

        private boolean matches(final List<PacketLineSignature> otherSignatures, final int lineCount, final PacketBaseSignature otherBaseSignature) {
            return this.entityIds.size() == lineCount
                && this.signatures.equals(otherSignatures)
                && this.baseSignature.equals(otherBaseSignature);
        }
    }

    private record PacketBaseSignature(String worldName, double x, double y, double z) {
    }

    private record PacketLineSignature(NmsAdapter.HologramLineType type, double offsetY) {
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
}
