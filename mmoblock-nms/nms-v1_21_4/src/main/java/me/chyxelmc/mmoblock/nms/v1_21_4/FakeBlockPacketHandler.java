package me.chyxelmc.mmoblock.nms.v1_21_4;

import me.chyxelmc.mmoblock.nms.AbstractFakeBlockPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakeBlockPacketHandler extends AbstractFakeBlockPacketHandler {

    private static final Map<Material, Block> NMS_BLOCK_CACHE = new ConcurrentHashMap<>();

    public FakeBlockPacketHandler(Player player) {
        super(player);
    }

    // ---- Static inject/uninject (calls base class static INSTANCES) ----

    public static void inject(Player player) {
        if (player == null) return;
        INSTANCES.compute(player.getUniqueId(), (k, existing) -> {
            if (existing != null) return existing;
            var h = new FakeBlockPacketHandler(player);
            h.inject();
            return h;
        });
    }

    public static void uninject(Player player) {
        if (player == null) return;
        var h = INSTANCES.remove(player.getUniqueId());
        if (h != null) h.uninject();
    }

    // ---- Hook implementations ----

    @Override
    protected Object getServerPlayer(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    @Override
    protected Object getServerLevel(org.bukkit.World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    protected Object getNmsBlock(Material material) {
        if (material == null) return null;
        Block cached = NMS_BLOCK_CACHE.get(material);
        if (cached != null) return cached;
        Block block = CraftMagicNumbers.getBlock(material);
        if (block != null) NMS_BLOCK_CACHE.put(material, block);
        return block;
    }

    @Override
    protected Object nmsDefaultBlockState(Object nmsBlock) {
        return ((Block) nmsBlock).defaultBlockState();
    }

    @Override
    protected boolean isClientBoundBlockUpdate(Object msg) {
        return msg instanceof ClientboundBlockUpdatePacket;
    }

    @Override
    protected boolean isServerBoundPlayerAction(Object msg) {
        return msg instanceof ServerboundPlayerActionPacket;
    }

    @Override
    protected boolean isServerBoundUseItemOn(Object msg) {
        return msg instanceof ServerboundUseItemOnPacket;
    }

    @Override
    protected boolean isServerBoundUseItem(Object msg) {
        return msg instanceof ServerboundUseItemPacket;
    }

    @Override
    protected Object createBlockUpdatePacket(Object blockPos, Object blockState) {
        return new ClientboundBlockUpdatePacket((BlockPos) blockPos, (BlockState) blockState);
    }

    @Override
    protected Object getBlockState(Object serverLevel, Object blockPos) {
        return ((ServerLevel) serverLevel).getBlockState((BlockPos) blockPos);
    }

    @Override
    protected Object createBlockPos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Override
    protected void sendPacket(Object serverPlayer, Object packet) {
        ((ServerPlayer) serverPlayer).connection.send((net.minecraft.network.protocol.Packet<?>) packet);
    }
}
