package me.chyxelmc.mmoblock.nms.spigot.v1_20_4;

import me.chyxelmc.mmoblock.nms.utils.NmsLogger;

import me.chyxelmc.mmoblock.nms.AbstractPacketBasedNmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.utils.ClientProtocolUtils;
import me.chyxelmc.mmoblock.nms.utils.HologramColorUtil;
import com.mojang.math.Transformation;
import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.util.*;

@SuppressWarnings({"java:S101", "unchecked", "rawtypes"})
public final class NmsAdapter_v1_20_4 extends AbstractPacketBasedNmsAdapter {

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
    public void applyEntityGlow(final Player player, final Entity entity, final String colorName) {
        if (!(player instanceof CraftPlayer craftPlayer) || !(entity instanceof CraftEntity craftEntity)) {
            super.applyEntityGlow(player, entity, colorName);
            return;
        }
        final ChatColor chatColor = NmsAdapter.resolveGlowChatColor(colorName);
        if (chatColor == null) return;
        try {
            final net.minecraft.world.entity.Entity handle = craftEntity.getHandle();
            handle.setGlowingTag(true);
            final String teamName = "mmbg" + Integer.toString(Math.max(0, entity.getEntityId()), 36);
            final PlayerTeam team = new PlayerTeam(new Scoreboard(), teamName);
            team.setColor(ChatFormatting.getByCode(chatColor.getChar()));
            team.setCollisionRule(Team.CollisionRule.NEVER);
            craftPlayer.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            craftPlayer.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    team, entity.getUniqueId().toString(), ClientboundSetPlayerTeamPacket.Action.ADD));
        } catch (final RuntimeException exception) {
            super.applyEntityGlow(player, entity, colorName);
        }
    }

    @Override
    public SpawnResult spawnInteraction(
            final World world, final Location location, final float width, final float height,
            final NamespacedKey uniqueIdKey, final UUID blockUniqueId
    ) {
        final SpawnResult bukkitResult = spawnInteractionViaBukkit(world, location, width, height, uniqueIdKey, blockUniqueId, "");
        if (bukkitResult.success()) return bukkitResult;
        try {
            final ServerLevel level = ((CraftWorld) world).getHandle();
            final OptimizedInteraction handle = new OptimizedInteraction(EntityType.INTERACTION, level);
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
            if (entity == null) return RemoveResult.success(false, SpawnPath.NMS);
            entity.discard();
            return RemoveResult.success(true, SpawnPath.NMS);
        } catch (final RuntimeException exception) {
            return RemoveResult.failed("NMS remove failed: " + exception.getMessage());
        }
    }

    // --- version-specific override ---

    @Override
    protected double getLegacyItemOffset() {
        return 0.08D;
    }

    // --- hooks ---

    @Override
    protected Object getServerPlayer(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @Override
    protected Object getServerLevel(World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    protected void sendPacket(Object serverPlayer, Object packet) {
        ((ServerPlayer) serverPlayer).connection.send((net.minecraft.network.protocol.Packet<?>) packet);
    }

    @Override
    protected int getEntityId(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getId();
    }

    @Override
    protected UUID getEntityUUID(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getUUID();
    }

    @Override
    protected float getEntityXRot(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getXRot();
    }

    @Override
    protected float getEntityYRot(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getYRot();
    }

    @Override
    protected float getEntityYHeadRot(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getYHeadRot();
    }

    @Override
    protected Object getEntityType(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getType();
    }

    @Override
    protected List<?> getEntityDataValues(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getEntityData().getNonDefaultValues();
    }

    @Override
    protected Object createAddEntityPacket(int id, UUID uuid, double x, double y, double z,
                                           float xRot, float yRot, Object entityType, int data,
                                           double vx, double vy, double vz, float yHeadRot) {
        return new ClientboundAddEntityPacket(id, uuid, x, y, z, xRot, yRot, (EntityType<?>) entityType, data, new Vec3(vx, vy, vz), yHeadRot);
    }

    @Override
    protected Object createRemoveEntitiesPacket(int[] entityIds) {
        return new ClientboundRemoveEntitiesPacket(entityIds);
    }

    @Override
    protected Object createEntityDataPacket(int entityId, List<?> packedItems) {
        return new ClientboundSetEntityDataPacket(entityId, (List<SynchedEntityData.DataValue<?>>) packedItems);
    }

    @Override
    protected Object createSetEntityMotionPacket(int entityId, double vx, double vy, double vz) {
        return new ClientboundSetEntityMotionPacket(entityId, new Vec3(vx, vy, vz));
    }

    @Override
    protected Object createBlockDestructionPacket(int entityId, int x, int y, int z, int stage) {
        return new net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket(entityId, new BlockPos(x, y, z), stage);
    }

    @Override
    protected Object createBlockUpdatePacket(int x, int y, int z, Material material) {
        return new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(new BlockPos(x, y, z), CraftMagicNumbers.getBlock(material).defaultBlockState());
    }

    // --- display factories ---

    @Override
    protected Object createTextDisplayEntity(Object serverLevel, double x, double y, double z, String text) {
        final Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(x, y, z);
        display.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        display.setTransformationInterpolationDelay(0);
        display.setTransformationInterpolationDuration(2);
        display.setViewRange(0.4F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        display.setText(PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text)));
        return display;
    }

    @Override
    protected Object createItemDisplayEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null) return null;
        final long currentTime = System.currentTimeMillis();
        final float seconds = (currentTime % 10000L) / 1000.0f;
        final float rotationAngle = seconds * (float) (2.0 * Math.PI / 5.0);
        final float verticalOffset = (float) (Math.sin(seconds * Math.PI) * 0.05);
        final Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(x, y, z);
        display.setItemStack(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
        display.setItemTransform(ItemDisplayContext.GROUND);
        display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.setViewRange(0.4F);
        display.setWidth(0.5F);
        display.setHeight(0.5F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        final Matrix4f matrix = new Matrix4f().translation(0.0f, verticalOffset, 0.0f).rotateY(rotationAngle);
        display.setTransformation(new Transformation(matrix));
        display.setTransformationInterpolationDelay(0);
        display.setTransformationInterpolationDuration(2);
        return display;
    }

    @Override
    protected Object createBlockDisplayEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null || !material.isBlock()) return null;
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(x, y, z);
        display.setBlockState(CraftMagicNumbers.getBlock(material).defaultBlockState());
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setViewRange(0.4F);
        display.setWidth(0.5F);
        display.setHeight(0.5F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        return display;
    }

    @Override
    protected Object createLegacyArmorStandEntity(Object serverLevel, double x, double y, double z, String text) {
        final ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, (ServerLevel) serverLevel);
        stand.setPos(x, y, z);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.setNoBasePlate(true);
        stand.setMarker(true);
        stand.setCustomName(PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text)));
        stand.setCustomNameVisible(true);
        return stand;
    }

    @Override
    protected Object createLegacyItemStandEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null) return null;
        final Level level = (Level) serverLevel;
        final StaticItemEntity itemEntity = new StaticItemEntity(
                level, x, y, z,
                CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
        itemEntity.setPos(x, y, z);
        return itemEntity;
    }

    // --- BDEngine factories ---

    @Override
    protected Object createBdEngineBlockDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        if (part.material() == null || !part.material().isBlock()) return null;
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setBlockState(CraftMagicNumbers.getBlock(part.material()).defaultBlockState());
        configureBdEngineDisplay(display, part, normalizeMatrix(part.matrix()));
        return display;
    }

    @Override
    protected Object createBdEngineItemDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        if (part.material() == null) return null;
        final Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setItemStack(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(part.material())));
        display.setItemTransform(ItemDisplayContext.NONE);
        configureBdEngineDisplay(display, part, normalizeMatrix(part.matrix()));
        return display;
    }

    @Override
    protected Object createBdEngineTextDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        final Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setText(PaperAdventure.asVanilla(HologramColorUtil.toComponent(part.text() == null ? "" : part.text())));
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        configureBdEngineDisplay(display, part, normalizeMatrix(part.matrix()));
        return display;
    }

    @Override
    protected void configureBdEngineDisplay(Object display, BdEngineDisplayPart part, float[] matrix) {
        final Display d = (Display) display;
        d.setTransformation(new Transformation(new Matrix4f(
                matrix[0], matrix[1], matrix[2], matrix[3],
                matrix[4], matrix[5], matrix[6], matrix[7],
                matrix[8], matrix[9], matrix[10], matrix[11],
                matrix[12], matrix[13], matrix[14], matrix[15]
        )));
        d.setTransformationInterpolationDelay(-1);
        d.setTransformationInterpolationDuration(BDENGINE_INTERPOLATION_DURATION);
        d.setViewRange(1.0F);
        d.setWidth(4.0F);
        d.setHeight(4.0F);
        d.setShadowRadius(0.0F);
        d.setShadowStrength(0.0F);
        d.setBrightnessOverride(new Brightness(
                Math.max(0, Math.min(15, part.blockLight())),
                Math.max(0, Math.min(15, part.skyLight()))));
    }

    // --- NBT hooks ---

    @Override
    protected Object readNbtFromFile(String filePath) {
        try {
            return NbtIo.readCompressed(new java.io.File(filePath).toPath(), NbtAccounter.unlimitedHeap());
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Override
    protected int nbtGetShort(Object tag, String key) {
        return ((CompoundTag) tag).getShort(key);
    }

    @Override
    protected Object nbtGetCompound(Object tag, String key) {
        return ((CompoundTag) tag).getCompound(key);
    }

    @Override
    protected byte[] nbtGetByteArray(Object tag, String key) {
        return ((CompoundTag) tag).getByteArray(key);
    }

    @Override
    protected Set<String> nbtGetKeys(Object tag) {
        return ((CompoundTag) tag).getAllKeys();
    }

    @Override
    protected int nbtGetInt(Object tag, String key) {
        return ((CompoundTag) tag).getInt(key);
    }

    @Override
    protected boolean nbtContains(Object tag, String key) {
        return ((CompoundTag) tag).contains(key);
    }

    @Override
    protected Object nmsBlockState(Material material) {
        return CraftMagicNumbers.getBlock(material).defaultBlockState();
    }

    // --- inner types ---

    private void applyCustomAabb(
            final net.minecraft.world.entity.Interaction handle,
            final Location location,
            final float width,
            final float height
    ) {
        final double half = width / 2.0D;
        handle.setBoundingBox(new AABB(
                location.getX() - half, location.getY(), location.getZ() - half,
                location.getX() + half, location.getY() + height, location.getZ() + half));
    }

    private static final class OptimizedInteraction extends net.minecraft.world.entity.Interaction {
        private OptimizedInteraction(EntityType<? extends net.minecraft.world.entity.Interaction> type, Level level) { super(type, level); }
        @Override public void tick() {}
        @Override public void inactiveTick() {}
    }

    private static final class StaticItemEntity extends ItemEntity {
        private StaticItemEntity(Level level, double x, double y, double z, net.minecraft.world.item.ItemStack item) {
            super(level, x, y, z, item);
            this.setNoGravity(true);
            this.setSilent(true);
            this.setNeverPickUp();
            this.age = 6000;
            this.setDeltaMovement(Vec3.ZERO);
        }
        @Override public void tick() {}
        @Override public void inactiveTick() {}
    }
}
