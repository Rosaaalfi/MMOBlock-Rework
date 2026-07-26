package me.chyxelmc.mmoblock.nms.v1_21_4;

import me.chyxelmc.mmoblock.nms.utils.NmsLogger;

import com.mojang.math.Transformation;
import io.papermc.paper.adventure.PaperAdventure;
import me.chyxelmc.mmoblock.nms.AbstractPacketBasedNmsAdapter;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.nms.utils.HologramColorUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NMS adapter for Minecraft 1.21.4 (Mojang-mapped).
 *
 * <p>Extends {@link AbstractPacketBasedNmsAdapter} which contains all common
 * packet hologram, BDEngine, schematic, and interaction lifecycle code.
 * This class only provides version-specific hook implementations and
 * overrides that differ between Minecraft versions.
 */
@SuppressWarnings("java:S101")
public final class NmsAdapter_v1_21_4 extends AbstractPacketBasedNmsAdapter {

    private static final EntityType<net.minecraft.world.entity.Interaction> CUSTOM_INTERACTION_TYPE = createCustomInteractionType();

    // ============================================================
    // Core metadata
    // ============================================================

    @Override
    public String targetMinecraftVersion() {
        return "1.21.4";
    }

    @Override
    public void validateNms() {
        Component.literal("MMOBlock").getString();
    }

    // ============================================================
    // Hook implementations
    // ============================================================

    @Override
    protected Object getServerPlayer(final Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @Override
    protected Object getServerLevel(final World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    protected void sendPacket(final Object serverPlayer, final Object packet) {
        ((ServerPlayer) serverPlayer).connection.send((Packet<?>) packet);
    }

    @Override
    protected int getEntityId(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getId();
    }

    @Override
    protected UUID getEntityUUID(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getUUID();
    }

    @Override
    protected float getEntityXRot(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getXRot();
    }

    @Override
    protected float getEntityYRot(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getYRot();
    }

    @Override
    protected float getEntityYHeadRot(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getYHeadRot();
    }

    @Override
    protected Object getEntityType(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getType();
    }

    @Override
    protected List<?> getEntityDataValues(final Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getEntityData().getNonDefaultValues();
    }

    // ---- Packet creation ----

    @Override
    protected Object createAddEntityPacket(
            final int id, final UUID uuid, final double x, final double y, final double z,
            final float xRot, final float yRot, final Object entityType,
            final int data, final double vx, final double vy, final double vz, final float yHeadRot
    ) {
        return new ClientboundAddEntityPacket(
                id, uuid, x, y, z, xRot, yRot,
                (EntityType<?>) entityType, data, new Vec3(vx, vy, vz), yHeadRot
        );
    }

    @Override
    protected Object createRemoveEntitiesPacket(final int[] entityIds) {
        return new ClientboundRemoveEntitiesPacket(entityIds);
    }

    @Override
    protected Object createEntityDataPacket(final int entityId, final List<?> packedItems) {
        return new ClientboundSetEntityDataPacket(entityId, (List) packedItems);
    }

    @Override
    protected Object createSetEntityMotionPacket(final int entityId, final double vx, final double vy, final double vz) {
        return new ClientboundSetEntityMotionPacket(entityId, new Vec3(vx, vy, vz));
    }

    @Override
    protected Object createBlockDestructionPacket(final int entityId, final int x, final int y, final int z, final int stage) {
        return new ClientboundBlockDestructionPacket(entityId, new BlockPos(x, y, z), stage);
    }

    @Override
    protected Object createBlockUpdatePacket(final int x, final int y, final int z, final Material material) {
        return new ClientboundBlockUpdatePacket(new BlockPos(x, y, z),
                CraftMagicNumbers.getBlock(material).defaultBlockState());
    }

    // ---- Display entity factories ----

    @Override
    protected Object createTextDisplayEntity(final Object serverLevel, final double x, final double y, final double z, final String text) {
        final Display.TextDisplay display = new Display.TextDisplay(
                net.minecraft.world.entity.EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
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

    @Override
    protected Object createItemDisplayEntity(final Object serverLevel, final double x, final double y, final double z, final Material material) {
        if (material == null) return null;

        final long currentTime = System.currentTimeMillis();
        final float seconds = (currentTime % 10000L) / 1000.0f;
        final float rotationAngle = seconds * (float) (2.0 * Math.PI / 5.0);
        final float verticalOffset = (float) (Math.sin(seconds * Math.PI) * 0.05);

        final Display.ItemDisplay display = new Display.ItemDisplay(
                EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
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
    protected Object createBlockDisplayEntity(final Object serverLevel, final double x, final double y, final double z, final Material material) {
        if (material == null || !material.isBlock()) return null;
        final Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
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
    protected Object createLegacyArmorStandEntity(final Object serverLevel, final double x, final double y, final double z, final String text) {
        final ArmorStand stand = new ArmorStand(
                EntityType.ARMOR_STAND, (ServerLevel) serverLevel);
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

    @Override
    protected Object createLegacyItemStandEntity(final Object serverLevel, final double x, final double y, final double z, final Material material) {
        if (material == null) return null;
        return new StaticItemEntity(
                (ServerLevel) serverLevel, x, y, z,
                CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material))
        );
    }

    // ---- BDEngine display factories ----

    @Override
    protected Object createBdEngineBlockDisplay(final Object serverLevel, final Location base, final BdEngineDisplayPart part) {
        if (part.material() == null || !part.material().isBlock()) return null;
        final Display.BlockDisplay display = new Display.BlockDisplay(
                EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setBlockState(CraftMagicNumbers.getBlock(part.material()).defaultBlockState());
        configureBdEngineDisplay(display, part, part.matrix());
        return display;
    }

    @Override
    protected Object createBdEngineItemDisplay(final Object serverLevel, final Location base, final BdEngineDisplayPart part) {
        if (part.material() == null) return null;
        final Display.ItemDisplay display = new Display.ItemDisplay(
                EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setItemStack(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(part.material())));
        display.setItemTransform(ItemDisplayContext.NONE);
        configureBdEngineDisplay(display, part, part.matrix());
        return display;
    }

    @Override
    protected Object createBdEngineTextDisplay(final Object serverLevel, final Location base, final BdEngineDisplayPart part) {
        final Display.TextDisplay display = new Display.TextDisplay(
                net.minecraft.world.entity.EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(base.getX(), base.getY(), base.getZ());
        display.setText(parseVanillaText(part.text() == null ? "" : part.text()));
        display.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        configureBdEngineDisplay(display, part, part.matrix());
        return display;
    }

    @Override
    protected void configureBdEngineDisplay(final Object display, final BdEngineDisplayPart part, final float[] matrix) {
        final Display disp = (Display) display;
        final float[] safe = normalizeMatrix(matrix);
        disp.setTransformation(new Transformation(new Matrix4f(
                safe[0], safe[1], safe[2], safe[3],
                safe[4], safe[5], safe[6], safe[7],
                safe[8], safe[9], safe[10], safe[11],
                safe[12], safe[13], safe[14], safe[15]
        )));
        disp.setTransformationInterpolationDelay(-1);
        disp.setTransformationInterpolationDuration(BDENGINE_INTERPOLATION_DURATION);
        disp.setViewRange(1.0F);
        disp.setWidth(4.0F);
        disp.setHeight(4.0F);
        disp.setShadowRadius(0.0F);
        disp.setShadowStrength(0.0F);
        disp.setBrightnessOverride(new Brightness(
                Math.max(0, Math.min(15, part.blockLight())),
                Math.max(0, Math.min(15, part.skyLight()))
        ));
    }

    @Override
    protected Object nmsBlockState(final Material material) {
        return CraftMagicNumbers.getBlock(material).defaultBlockState();
    }

    // ---- Schematic / NBT hooks ----

    @Override
    protected Object readNbtFromFile(final String filePath) {
        try {
            return NbtIo.readCompressed(new java.io.File(filePath).toPath(), NbtAccounter.unlimitedHeap());
        } catch (final Exception ignored) {
            return null;
        }
    }

    @Override
    protected int nbtGetShort(final Object compoundTag, final String key) {
        return ((CompoundTag) compoundTag).getShort(key);
    }

    @Override
    protected Object nbtGetCompound(final Object compoundTag, final String key) {
        return ((CompoundTag) compoundTag).getCompound(key);
    }

    @Override
    protected byte[] nbtGetByteArray(final Object compoundTag, final String key) {
        return ((CompoundTag) compoundTag).getByteArray(key);
    }

    @Override
    protected Set<String> nbtGetKeys(final Object compoundTag) {
        return ((CompoundTag) compoundTag).getAllKeys();
    }

    @Override
    protected int nbtGetInt(final Object compoundTag, final String key) {
        return ((CompoundTag) compoundTag).getInt(key);
    }

    @Override
    protected boolean nbtContains(final Object compoundTag, final String key) {
        return ((CompoundTag) compoundTag).contains(key);
    }

    // ---- Text parsing ----

    private Component parseVanillaText(final String text) {
        return PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text));
    }

    // ============================================================
    // Version-specific overrides (cannot be abstracted)
    // ============================================================

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
            team.setColor(net.minecraft.ChatFormatting.getByCode(chatColor.getChar()));
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
            final World world, final Location location,
            final float width, final float height,
            final NamespacedKey uniqueIdKey, final UUID blockUniqueId
    ) {
        final SpawnResult bukkitResult = spawnInteractionViaBukkit(world, location, width, height, uniqueIdKey, blockUniqueId, "");
        if (bukkitResult.success()) return bukkitResult;

        try {
            final ServerLevel level = ((CraftWorld) world).getHandle();
            final OptimizedInteraction handle = new OptimizedInteraction(CUSTOM_INTERACTION_TYPE, level);
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

    // ============================================================
    // Private helpers
    // ============================================================

    private void applyCustomAabb(
            final net.minecraft.world.entity.Interaction handle,
            final Location location, final float width, final float height
    ) {
        final double half = width / 2.0D;
        handle.setBoundingBox(new AABB(
                location.getX() - half, location.getY(), location.getZ() - half,
                location.getX() + half, location.getY() + height, location.getZ() + half
        ));
    }

    private static EntityType<net.minecraft.world.entity.Interaction> createCustomInteractionType() {
        try {
            final ResourceKey<EntityType<?>> key = BuiltInRegistries.ENTITY_TYPE
                    .getResourceKey(EntityType.INTERACTION).orElseThrow();
            return EntityType.Builder
                    .of(net.minecraft.world.entity.Interaction::new, MobCategory.MISC)
                    .clientTrackingRange(2)
                    .build(key);
        } catch (final RuntimeException ignored) {
            return EntityType.INTERACTION;
        }
    }

    // ============================================================
    // Inner classes (version-specific NMS entity types)
    // ============================================================

    private static final class OptimizedInteraction extends net.minecraft.world.entity.Interaction {
        private OptimizedInteraction(
                final net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Interaction> type,
                final Level level
        ) {
            super(type, level);
        }

        @Override
        public void tick() {
            // No-op — static interaction hitboxes don't need per-tick processing.
        }

        @Override
        public void inactiveTick() {
            // No-op while chunk is inactive.
        }
    }

    private static final class StaticItemEntity extends ItemEntity {
        private StaticItemEntity(
                final Level level, final double x, final double y, final double z,
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
            // No-op — suppresses bobbing animation and all physics updates.
        }

        @Override
        public void inactiveTick() {
            // No-op while chunk is inactive.
        }
    }
}
