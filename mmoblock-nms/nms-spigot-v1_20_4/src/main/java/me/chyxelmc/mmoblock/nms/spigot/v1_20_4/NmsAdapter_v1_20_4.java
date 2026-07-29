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
        // Cast to Display to prevent Paperweight reobfuscator from remapping inherited
        // Display methods to private TextDisplay methods in Spigot mappings (Folia 1.20.4).
        final Display d = display;
        d.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
        d.setTransformationInterpolationDelay(0);
        d.setTransformationInterpolationDuration(2);
        d.setViewRange(0.4F);
        d.setShadowRadius(0.0F);
        d.setShadowStrength(0.0F);
        setTextDisplayBackgroundColor(display, 0);
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
        // Cast to Display to prevent Paperweight reobfuscation issue
        final Display d = display;
        d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
        d.setViewRange(0.4F);
        d.setWidth(0.5F);
        d.setHeight(0.5F);
        d.setShadowRadius(0.0F);
        d.setShadowStrength(0.0F);
        final Matrix4f matrix = new Matrix4f().translation(0.0f, verticalOffset, 0.0f).rotateY(rotationAngle);
        d.setTransformation(new Transformation(matrix));
        d.setTransformationInterpolationDelay(0);
        d.setTransformationInterpolationDuration(2);
        return display;
    }

    @Override
    protected Object createBlockDisplayEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null || !material.isBlock()) return null;
        final Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
        display.setPos(x, y, z);
        display.setBlockState(CraftMagicNumbers.getBlock(material).defaultBlockState());
        // Cast to Display to prevent Paperweight reobfuscation issue
        final Display d = display;
        d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        d.setViewRange(0.4F);
        d.setWidth(0.5F);
        d.setHeight(0.5F);
        d.setShadowRadius(0.0F);
        d.setShadowStrength(0.0F);
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
        setTextDisplayBackgroundColor(display, 0);
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

    /**
     * Cached {@code EntityDataAccessor<Integer>} for the TextDisplay background color.
     *
     * <p>In Spigot-remapped (Folia 1.20.4) jars, both {@code DATA_BACKGROUND_COLOR_ID}
     * and {@code setBackgroundColor(int)} are {@code private} on {@code TextDisplay}.
     * We cannot use the Mojang field/method name string in reflection either, because
     * Paperweight does not remap string literals.
     *
     * <p>Solution: scan all declared static {@code EntityDataAccessor} fields of
     * {@code TextDisplay} and pick the one whose current value in the freshly-created
     * display is {@code 0x40000000} — the {@code DEFAULT_BACKGROUND_COLOR} that the
     * {@code TextDisplay} constructor sets. This works around both the name obfuscation
     * and the fact that Paperweight may inject synthetic copies of parent-class accessors
     * (e.g. {@code DATA_TRANSFORMATION_INTERPOLATION_DELAY_ID}) as declared fields.
     */
    private static volatile net.minecraft.network.syncher.EntityDataAccessor<Integer> backgroundAccessor;

    @SuppressWarnings("unchecked")
    private static net.minecraft.network.syncher.EntityDataAccessor<Integer> resolveBgAccessor(
            final Display.TextDisplay display
    ) {
        try {
            for (final java.lang.reflect.Field field : Display.TextDisplay.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (!net.minecraft.network.syncher.EntityDataAccessor.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                final Object val = field.get(null);
                if (!(val instanceof net.minecraft.network.syncher.EntityDataAccessor<?> accessorUser)) continue;
                try {
                    final Object current = display.getEntityData().get(accessorUser);
                    if (current instanceof Integer intVal && intVal == 0x40000000) {
                        return (net.minecraft.network.syncher.EntityDataAccessor<Integer>) accessorUser;
                    }
                } catch (final Exception ignoredField) {
                    // Not a valid accessor for this entity type — skip
                }
            }
        } catch (final Exception ignored) {
            // Fall through to null
        }
        return null;
    }

    /**
     * Sets the background color of a TextDisplay to transparent by finding the
     * background-color {@code EntityDataAccessor} via value-based verification.
     */
    private static void setTextDisplayBackgroundColor(final Display.TextDisplay display, final int color) {
        net.minecraft.network.syncher.EntityDataAccessor<Integer> accessor = backgroundAccessor;
        if (accessor == null) {
            accessor = resolveBgAccessor(display);
            backgroundAccessor = accessor;
        }
        if (accessor != null) {
            display.getEntityData().set(accessor, color);
        }
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
