package me.chyxelmc.mmoblock.nms.v1_20_4;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsAdapter_v1_20_4 implements NmsAdapter {

    private final Map<String, PacketHologramState> packetHologramEntityIds = new ConcurrentHashMap<>();

    @Override
    public String targetMinecraftVersion() {
        return "1.20.4";
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
            final net.minecraft.world.entity.Interaction handle = new net.minecraft.world.entity.Interaction(
                net.minecraft.world.entity.EntityType.INTERACTION,
                level
            );
            handle.setPos(location.getX(), location.getY(), location.getZ());
            applyCustomAabb(handle, location, width, height);
            level.addFreshEntity(handle);

            if (!(handle.getBukkitEntity() instanceof Interaction interaction)) {
                handle.discard();
                return SpawnResult.failed("Spawned NMS entity is not Bukkit Interaction");
            }

            interaction.setInteractionWidth(Math.max(0.25F, width));
            interaction.setInteractionHeight(Math.max(0.25F, height));
            interaction.setResponsive(true);
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
        for (final Player viewer : world.getPlayers()) {
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
        final double radiusSquared = 128.0D * 128.0D;
        for (final Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(location) > radiusSquared) {
                continue;
            }
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
        final double radiusSquared = 128.0D * 128.0D;
        for (final Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(location) > radiusSquared) {
                continue;
            }
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

        if (previous != null && previous.matches(signatures, lines.size()) && !previous.entityIds().isEmpty()) {
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
            this.packetHologramEntityIds.put(key, new PacketHologramState(previous.entityIds(), signatures));
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
        this.packetHologramEntityIds.put(key, new PacketHologramState(List.copyOf(newIds), signatures));
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
        display.setTransformationInterpolationDuration(2);
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        display.setText(Component.literal(text == null ? "" : text));
        return display;
    }

    private net.minecraft.world.entity.Entity createItemDisplay(final ServerLevel level, final double x, final double y, final double z, final Material material) {
        if (material == null) {
            return null;
        }
        final ItemEntity itemEntity = new ItemEntity(level, x, y, z, CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
        itemEntity.setNoGravity(true);
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
            signatures.add(new PacketLineSignature(line.type(), line.offsetY()));
        }
        return signatures;
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

    private record PacketHologramState(List<Integer> entityIds, List<PacketLineSignature> signatures) {

        private boolean matches(final List<PacketLineSignature> otherSignatures, final int lineCount) {
            return this.entityIds.size() == lineCount && this.signatures.equals(otherSignatures);
        }
    }

    private record PacketLineSignature(NmsAdapter.HologramLineType type, double offsetY) {
    }
}
