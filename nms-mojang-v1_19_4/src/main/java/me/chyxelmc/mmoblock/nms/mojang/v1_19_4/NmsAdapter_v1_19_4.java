package me.chyxelmc.mmoblock.nms.mojang.v1_19_4;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.utils.ClientProtocolUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.craftbukkit.v1_19_R3.util.CraftMagicNumbers;
import net.minecraft.world.phys.Vec3;
import io.papermc.paper.adventure.PaperAdventure;
import me.chyxelmc.mmoblock.nmsloader.utils.HologramColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsAdapter_v1_19_4 implements NmsAdapter {

    private static final EntityType<net.minecraft.world.entity.Interaction> CUSTOM_INTERACTION_TYPE = createCustomInteractionType();
    private final Map<String, PacketHologramState> packetHologramEntityIds = new ConcurrentHashMap<>();
    // Small marker ArmorStand nameplate renders ~0.23 above entity Y.
    // 0.595 is for full-size ArmorStand — setSmall(true) reduces this significantly.
    private static final double ARMOR_STAND_NAME_Y_OFFSET = 0.23D;

    // ItemEntity visual center on modern clients (1.19.4+): ~0.1 above entity Y
    private static final double ITEM_ENTITY_Y_OFFSET_MODERN = 0.1D;

    // ItemEntity visual center on legacy clients (1.19.3 and below): ~0.25 above entity Y
    // Legacy client renderer uses a larger bobbing base offset than modern.
    private static final double ITEM_ENTITY_Y_OFFSET_LEGACY = 0.08D;
    @Override
    public String targetMinecraftVersion() {
        return "1.19.4";
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
            final double lineZ = baseLocation.getZ();
            final double baseLineY = baseLocation.getY() - line.offsetY();
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
            // For item/block display types, ensure the client receives zero velocity
            // after spawn so its local ItemEntity constructor doesn't apply upward motion.
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

    private net.minecraft.world.entity.Entity createDisplay(
            final ServerLevel level,
            final Location base,
            final HologramLine line,
            final boolean legacyClient
    ) {
        final double x = base.getX();
        final double z = base.getZ();
        final double baseLineY = base.getY() - line.offsetY();
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

    private Component parseVanillaText(final String text) {
        return PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text));
    }

    private net.minecraft.world.entity.Entity createTextDisplay(final ServerLevel level, final double x, final double y, final double z, final String text) {
        final Display.TextDisplay display = new Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, level);
        display.setPos(x, y, z);
        display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setViewRange(0.4F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setBackgroundColor(0);
        display.setText(parseVanillaText(text));
        return display;
    }

    private net.minecraft.world.entity.Entity createItemDisplay(final ServerLevel level, final double x, final double y, final double z, final Material material) {
        if (material == null) {
            return null;
        }
        final StaticItemEntity itemEntity = new StaticItemEntity(level, x, y, z, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
        itemEntity.setPos(x, y, z);
        return itemEntity;
    }

    private net.minecraft.world.entity.Entity createBlockDisplay(final ServerLevel level, final double x, final double y, final double z, final Material material) {
        if (material == null) {
            return null;
        }
        return createItemDisplay(level, x, y, z, material);
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
        // Avoid registering an unnamed custom entity type here because the
        // server's data fixer system may not have a mapping for it which leads
        // to the warning: "No data fixer registered for custom_interaction".
        // Using the built-in INTERACTION type is functionally equivalent for
        // our spawn use and avoids needing to register a custom data fixer.
        return EntityType.INTERACTION;
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
        }

        @Override
        public void inactiveTick() {
        }
    }
}
