package me.chyxelmc.mmoblock.nms.v1_21_8;

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
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NMS adapter for Minecraft 1.21.8 (Mojang-mapped).
 *
 * <p>Extends {@link AbstractPacketBasedNmsAdapter} which contains all common
 * packet hologram, BDEngine, schematic, and interaction lifecycle code.
 * This class only provides version-specific hook implementations and
 * overrides that differ between Minecraft versions.</p>
 *
 * <p>Note: 1.21.8 uses the Optional-based NBT API (introduced in 1.21.5),
 * so the NBT-related hooks follow the same pattern as 1.21.11.</p>
 */
@SuppressWarnings("java:S101")
public final class NmsAdapter_v1_21_8 extends AbstractPacketBasedNmsAdapter {
    private final me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess guiInventoryAccess = new GuiInventoryAccessImpl();
    @Override public me.chyxelmc.mmoblock.nms.gui.GuiInventoryAccess guiInventoryAccess() { return this.guiInventoryAccess; }

        @Override
    public String targetMinecraftVersion() {
        return "1.21.8";
    }

    @Override
    public void validateNms() {
        Component.literal("MMOBlock").getString();
    }

    // ============================================================
    // Hook implementations
    // ============================================================

    @Override
    protected Object getServerPlayer(Player player) { return ((CraftPlayer) player).getHandle(); }

    @Override
    protected Object getServerLevel(World world) { return ((CraftWorld) world).getHandle(); }

    @Override
    protected void sendPacket(Object serverPlayer, Object packet) {
        ((ServerPlayer) serverPlayer).connection.send((Packet<?>) packet);
    }

    @Override
    protected int getEntityId(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getId(); }

    @Override
    protected UUID getEntityUUID(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getUUID(); }

    @Override
    protected float getEntityXRot(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getXRot(); }

    @Override
    protected float getEntityYRot(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getYRot(); }

    @Override
    protected float getEntityYHeadRot(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getYHeadRot(); }

    @Override
    protected Object getEntityType(Object nmsEntity) { return ((net.minecraft.world.entity.Entity) nmsEntity).getType(); }

    @Override
    protected List<?> getEntityDataValues(Object nmsEntity) {
        return ((net.minecraft.world.entity.Entity) nmsEntity).getEntityData().getNonDefaultValues();
    }

    @Override
    protected void applyHologramEntityYaw(Object display, Location baseLocation) {
        ((net.minecraft.world.entity.Entity) display).setYRot((float) baseLocation.getYaw());
    }

    @Override
    protected Object createAddEntityPacket(int id, UUID uuid, double x, double y, double z,
            float xRot, float yRot, Object entityType, int data,
            double vx, double vy, double vz, float yHeadRot) {
        return new ClientboundAddEntityPacket(id, uuid, x, y, z, xRot, yRot,
                (EntityType<?>) entityType, data, new Vec3(vx, vy, vz), yHeadRot);
    }

    @Override
    protected Object createRemoveEntitiesPacket(int[] entityIds) {
        return new ClientboundRemoveEntitiesPacket(entityIds);
    }

    @Override
    protected Object createEntityDataPacket(int entityId, List<?> packedItems) {
        return new ClientboundSetEntityDataPacket(entityId, (List) packedItems);
    }

    @Override
    protected Object createSetEntityMotionPacket(int entityId, double vx, double vy, double vz) {
        return new ClientboundSetEntityMotionPacket(entityId, new Vec3(vx, vy, vz));
    }

    @Override
    protected Object createBlockDestructionPacket(int entityId, int x, int y, int z, int stage) {
        return new ClientboundBlockDestructionPacket(entityId, new BlockPos(x, y, z), stage);
    }

    @Override
    protected Object createBlockUpdatePacket(int x, int y, int z, Material material) {
        return new ClientboundBlockUpdatePacket(new BlockPos(x, y, z),
                CraftMagicNumbers.getBlock(material).defaultBlockState());
    }

    @Override
    protected Object createTextDisplayEntity(Object serverLevel, double x, double y, double z, String text) {
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
    protected Object createItemDisplayEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null) return null;
        final long t = System.currentTimeMillis();
        final float s = (t % 10000L) / 1000.0f;
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
        final Matrix4f m = new Matrix4f()
                .translation(0.0f, (float) (Math.sin(s * Math.PI) * 0.05), 0.0f)
                .rotateY(s * (float) (2.0 * Math.PI / 5.0));
        display.setTransformation(new Transformation(m));
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
        final String safe = text == null ? "" : text;
        stand.setCustomName(PaperAdventure.asVanilla(HologramColorUtil.toComponent(safe)));
        stand.setCustomNameVisible(true);
        return stand;
    }

    @Override
    protected Object createLegacyItemStandEntity(Object serverLevel, double x, double y, double z, Material material) {
        if (material == null) return null;
        return new StaticItemEntity((ServerLevel) serverLevel, x, y, z,
                CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(material)));
    }

    @Override
    protected Object createBdEngineBlockDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        if (part.material() == null || !part.material().isBlock()) return null;
        final Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
        d.setPos(base.getX(), base.getY(), base.getZ());
        d.setBlockState(CraftMagicNumbers.getBlock(part.material()).defaultBlockState());
        configureBdEngineDisplay(d, part, part.matrix());
        return d;
    }

    @Override
    protected Object createBdEngineItemDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        if (part.material() == null) return null;
        final Display.ItemDisplay d = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
        d.setPos(base.getX(), base.getY(), base.getZ());
        d.setItemStack(CraftItemStack.asNMSCopy(new org.bukkit.inventory.ItemStack(part.material())));
        d.setItemTransform(ItemDisplayContext.NONE);
        configureBdEngineDisplay(d, part, part.matrix());
        return d;
    }

    @Override
    protected Object createBdEngineTextDisplay(Object serverLevel, Location base, BdEngineDisplayPart part) {
        final Display.TextDisplay d = new Display.TextDisplay(
                net.minecraft.world.entity.EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
        d.setPos(base.getX(), base.getY(), base.getZ());
        d.setText(parseVanillaText(part.text() == null ? "" : part.text()));
        d.getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, 0);
        configureBdEngineDisplay(d, part, part.matrix());
        return d;
    }

    @Override
    protected Object createBdEngineDisplayForUpdate(Object serverLevel, Location base, BdEngineDisplayPart part) {
        final Display display = switch (part.type()) {
            case BLOCK -> new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, (ServerLevel) serverLevel);
            case ITEM -> new Display.ItemDisplay(EntityType.ITEM_DISPLAY, (ServerLevel) serverLevel);
            case TEXT -> new Display.TextDisplay(EntityType.TEXT_DISPLAY, (ServerLevel) serverLevel);
        };
        display.setPos(base.getX(), base.getY(), base.getZ());
        configureBdEngineDisplay(display, part, part.matrix());
        return display;
    }

    @Override
    protected void configureBdEngineDisplay(Object display, BdEngineDisplayPart part, float[] matrix) {
        final Display d = (Display) display;
        final float[] safe = normalizeMatrix(matrix);
        d.setTransformation(new Transformation(new Matrix4f(
                safe[0], safe[1], safe[2], safe[3],
                safe[4], safe[5], safe[6], safe[7],
                safe[8], safe[9], safe[10], safe[11],
                safe[12], safe[13], safe[14], safe[15]
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

    @Override
    protected Object nmsBlockState(Material material) {
        return CraftMagicNumbers.getBlock(material).defaultBlockState();
    }

    // ---- NBT hooks (Optional-based NBT API, same as 1.21.11+) ----

    @Override
    protected Object readNbtFromFile(String filePath) {
        try {
            return NbtIo.readCompressed(new java.io.File(filePath).toPath(), NbtAccounter.unlimitedHeap());
        } catch (final Exception e) {
            NmsLogger.debug("Failed to read NBT file " + filePath + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    protected int nbtGetShort(Object tag, String key) {
        return ((CompoundTag) tag).getShort(key).orElse((short) 0);
    }

    @Override
    protected Object nbtGetCompound(Object tag, String key) {
        return ((CompoundTag) tag).getCompound(key).orElse(null);
    }

    @Override
    protected byte[] nbtGetByteArray(Object tag, String key) {
        return ((CompoundTag) tag).getByteArray(key).orElse(null);
    }

    @Override
    protected Set<String> nbtGetKeys(Object tag) {
        return ((CompoundTag) tag).keySet();
    }

    @Override
    protected int nbtGetInt(Object tag, String key) {
        return ((CompoundTag) tag).getInt(key).orElse(0);
    }

    @Override
    protected boolean nbtContains(Object tag, String key) {
        return ((CompoundTag) tag).contains(key);
    }

    // ---- Text ----

    private Component parseVanillaText(String text) {
        return PaperAdventure.asVanilla(HologramColorUtil.toComponent(text == null ? "" : text));
    }

    // ============================================================
    // Version-specific overrides
    // ============================================================

    @Override
    public void sendSystemMessage(Player player, String message) {
        try {
            ((ServerPlayer) ((CraftPlayer) player).getHandle()).sendSystemMessage(Component.literal(message));
        } catch (RuntimeException ex) {
            player.sendMessage(message);
        }
    }

    @Override
    public void applyEntityGlow(Player player, Entity entity, String colorName) {
        if (!(player instanceof CraftPlayer cp) || !(entity instanceof CraftEntity ce)) {
            super.applyEntityGlow(player, entity, colorName);
            return;
        }
        final ChatColor cc = NmsAdapter.resolveGlowChatColor(colorName);
        if (cc == null) return;
        try {
            ce.getHandle().setGlowingTag(true);
            final String tn = "mmbg" + Integer.toString(Math.max(0, entity.getEntityId()), 36);
            final PlayerTeam t = new PlayerTeam(new Scoreboard(), tn);
            t.setColor(net.minecraft.ChatFormatting.getByCode(cc.getChar()));
            t.setCollisionRule(Team.CollisionRule.NEVER);
            cp.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(t, true));
            cp.getHandle().connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                    t, entity.getUniqueId().toString(), ClientboundSetPlayerTeamPacket.Action.ADD));
        } catch (RuntimeException ex) {
            super.applyEntityGlow(player, entity, colorName);
        }
    }

    // ============================================================
    // Private helpers
    // ============================================================

    // ============================================================
    // Inner classes
    // ============================================================

    private static final class StaticItemEntity extends ItemEntity {
        StaticItemEntity(Level l, double x, double y, double z, net.minecraft.world.item.ItemStack s) {
            super(l, x, y, z, s);
            setNoGravity(true); setSilent(true); setNeverPickUp();
            age = 6000; setDeltaMovement(Vec3.ZERO);
        }
        @Override public void tick() {}
        @Override public void inactiveTick() {}
    }
}
