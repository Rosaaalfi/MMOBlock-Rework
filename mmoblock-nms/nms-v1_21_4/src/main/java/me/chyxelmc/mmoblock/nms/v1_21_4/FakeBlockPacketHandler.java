package me.chyxelmc.mmoblock.nms.v1_21_4;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.RayTraceResult;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import net.minecraft.world.level.block.Block;
import me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler;
import me.chyxelmc.mmoblock.nms.utils.ReflectionUtil;

/**
 * Static manager usage:
 * - Register a checker via {@link #setFakeChecker(FakeBlockChecker)} which returns true when a given
 *   (player, blockPos) refers to a fake-block visual that should be re-sent.
 * - Call {@link #inject(Player)} when you want per-player interception enabled.
 * - Call {@link #uninject(Player)} to remove the handler and avoid memory leaks.
 */

/**
 * Netty Channel handler that intercepts client->server interaction packets (dig/use-on)
 * and forces a block update refresh for the clicked position. This prevents ghost/fake
 * blocks disappearing on the client when the client predicts a block change.
 *
 * Usage: create an instance per-player and call {@link #inject()} / {@link #uninject()}.
 * The handler keeps a weak reference to the Bukkit Player to avoid memory leaks.
 */
public final class FakeBlockPacketHandler extends ChannelDuplexHandler {

    private static final String PLUGIN_NAME = "MMOBlock";
    private static final String FAKE_BLOCK_REGISTRY_CLASS = "me.chyxelmc.mmoblock.runtime.FakeBlockRegistry";

    private final WeakReference<Player> playerRef;
    private final String pipelineName;
    private static final Logger LOG = Bukkit.getLogger();

    public FakeBlockPacketHandler(final Player player) {
        this.playerRef = new WeakReference<>(player);
        this.pipelineName = "mmoblock-fakeblock-" + player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    }

    private int[] tryExtractChunkCoords(final Object packet) {
        if (packet == null) return null;
        try {
            try {
                final java.lang.reflect.Method mx = packet.getClass().getDeclaredMethod("getChunkX");
                final java.lang.reflect.Method mz = packet.getClass().getDeclaredMethod("getChunkZ");
                ReflectionUtil.safeSetAccessible(mx, "NMS getChunkX method");
                ReflectionUtil.safeSetAccessible(mz, "NMS getChunkZ method");
                final Object ox = mx.invoke(packet);
                final Object oz = mz.invoke(packet);
                if (ox instanceof Integer ix && oz instanceof Integer iz) return new int[]{ix, iz};
            } catch (final Exception ignored) { }

            Integer cx = null;
            Integer cz = null;
            for (final Field f : packet.getClass().getDeclaredFields()) {
                try {
                    ReflectionUtil.setAccessibleQuietly(f);
                    final String name = f.getName().toLowerCase(Locale.ROOT);
                    final Object val = f.get(packet);
                    if (val == null) continue;
                    if ((name.contains("chunkx") || name.equals("x") || name.equals("chunkx")) && val instanceof Integer ix) cx = ix;
                    if ((name.contains("chunkz") || name.equals("z") || name.equals("chunkz")) && val instanceof Integer iz) cz = iz;
                    if (cx != null && cz != null) return new int[]{cx, cz};
                } catch (final Exception ignored) { }
            }
        } catch (final Exception ignored) { }
        return null;
    }

    private void scheduleResendForChunk(final Player player, final String worldName, final int chunkX, final int chunkZ) {
        try {
            final Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
            for (final String key : keys) {
                try {
                    final String[] parts = key.split(":");
                    if (parts.length != 4) continue;
                    final int bx = Integer.parseInt(parts[1]);
                    final int by = Integer.parseInt(parts[2]);
                    final int bz = Integer.parseInt(parts[3]);
                    final int cx = bx >> 4;
                    final int cz = bz >> 4;
                    if (cx != chunkX || cz != chunkZ) continue;
                    final BlockPos bp = new BlockPos(bx, by, bz);
                    final String matName = registryGetMaterial(worldName, bx, by, bz);
                    final net.minecraft.world.level.block.state.BlockState sendState = computeSendState(level, bp, matName);
                    if (sendState == null) continue;
                    sendFakeRefreshToPlayer(player, level, bp, sendState);
                } catch (final Exception ignored) { }
            }
        } catch (final Exception t) {
            LOG.log(Level.FINE, "Failed to schedule resend for chunk", t);
        }
    }

    private void scheduleResendForWorld(final Player player, final String worldName) {
        try {
            final Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
            for (final String key : keys) {
                try {
                    final String[] parts = key.split(":");
                    if (parts.length != 4) continue;
                    final int bx = Integer.parseInt(parts[1]);
                    final int by = Integer.parseInt(parts[2]);
                    final int bz = Integer.parseInt(parts[3]);
                    final BlockPos bp = new BlockPos(bx, by, bz);
                    final String matName = registryGetMaterial(worldName, bx, by, bz);
                    final net.minecraft.world.level.block.state.BlockState sendState = computeSendState(level, bp, matName);
                    if (sendState == null) continue;
                    sendFakeRefreshToPlayer(player, level, bp, sendState);
                } catch (final Exception ignored) { }
            }
        } catch (final Exception t) {
            LOG.log(Level.FINE, "Failed to schedule resend for world", t);
        }
    }

    private net.minecraft.world.level.block.state.BlockState computeSendState(final ServerLevel level, final BlockPos pos, final String materialName) {
        if (materialName != null) {
            try {
                final Material mat = Material.valueOf(materialName);
                final Block nb = getNmsBlockForMaterial(mat);
                if (nb != null) return nb.defaultBlockState();
            } catch (final IllegalArgumentException ignored) { }
        }
        try {
            return level.getBlockState(pos);
        } catch (final Exception t) {
            LOG.log(Level.FINE, "Failed to get level block state for " + pos, t);
            return null;
        }
    }

    /* Static manager state */
    private static final Map<UUID, FakeBlockPacketHandler> INSTANCES = new ConcurrentHashMap<>();
    @SuppressWarnings({"java:S3008", "java:S3077"})
    private static volatile Class<?> REG_CLASS = null;
    @SuppressWarnings({"java:S3008", "java:S3077"})
    private static volatile java.lang.reflect.Method REG_CONTAINS = null;
    @SuppressWarnings({"java:S3008", "java:S3077"})
    private static volatile java.lang.reflect.Method REG_GET_MATERIAL = null;
    @SuppressWarnings({"java:S3008", "java:S3077"})
    private static volatile java.lang.reflect.Method REG_GET_KEYS = null;
    private static final Object REG_LOCK = new Object();
    private static final Map<Material, Block> NMS_BLOCK_CACHE = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface FakeBlockChecker {
        boolean isFake(Player player, BlockPos pos);
    }

    @SuppressWarnings({"java:S3008", "java:S3077"})
    private static volatile FakeBlockChecker CHECKER = null;

    public static void setFakeChecker(final FakeBlockChecker checker) { CHECKER = checker; }

    private static void ensureRegistryReflectiveInit() {
        if (REG_CLASS != null) return;
        synchronized (REG_LOCK) {
            if (REG_CLASS != null) return;
            try {
                REG_CLASS = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
                REG_CONTAINS = REG_CLASS.getMethod("contains", String.class, int.class, int.class, int.class);
                REG_GET_MATERIAL = REG_CLASS.getMethod("getMaterial", String.class, int.class, int.class, int.class);
                try {
                    REG_GET_KEYS = REG_CLASS.getMethod("positionsForWorld", String.class);
                } catch (final NoSuchMethodException ignored) {
                    REG_GET_KEYS = null;
                }
            } catch (final Exception t) {
                REG_CLASS = null; REG_CONTAINS = null; REG_GET_MATERIAL = null;
                LOG.log(Level.FINE, "Failed to initialize FakeBlockRegistry reflective accessors", t);
            }
        }
    }

    private static boolean registryContains(final String world, final int x, final int y, final int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_CONTAINS != null) {
                final Object res = REG_CONTAINS.invoke(null, world, x, y, z);
                return res instanceof Boolean b && b;
            }
            final Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            final java.lang.reflect.Method m = c.getMethod("contains", String.class, int.class, int.class, int.class);
            final Object res = m.invoke(null, world, x, y, z);
            return res instanceof Boolean b && b;
        } catch (final Exception t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective contains() failed", t);
            return false;
        }
    }

    private static String registryGetMaterial(final String world, final int x, final int y, final int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_MATERIAL != null) {
                final Object res = REG_GET_MATERIAL.invoke(null, world, x, y, z);
                return res instanceof String s ? s : null;
            }
            final Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            final java.lang.reflect.Method m = c.getMethod("getMaterial", String.class, int.class, int.class, int.class);
            final Object res = m.invoke(null, world, x, y, z);
            return res instanceof String s ? s : null;
        } catch (final Exception t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective getMaterial() failed", t);
            return null;
        }
    }

    private static Set<String> registryGetKeysForWorld(final String world) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_KEYS != null) {
                final Object res = REG_GET_KEYS.invoke(null, world);
                if (res instanceof Set) return extractStringSet((Set<?>) res);
            }
            final Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            final java.lang.reflect.Method m = c.getMethod("positionsForWorld", String.class);
            final Object res = m.invoke(null, world);
            if (res instanceof Set) return extractStringSet((Set<?>) res);
            return Collections.emptySet();
        } catch (final Exception t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective positionsForWorld() failed", t);
            return Collections.emptySet();
        }
    }

    private static Set<String> extractStringSet(final Set<?> raw) {
        final Set<String> out = new java.util.HashSet<>();
        for (final Object o : raw) { if (o instanceof String s) out.add(s); }
        return out;
    }

    private static Block getNmsBlockForMaterial(final Material material) {
        if (material == null) return null;
        final Block cached = NMS_BLOCK_CACHE.get(material);
        if (cached != null) return cached;
        try {
            final Block b = CraftMagicNumbers.getBlock(material);
            if (b != null) NMS_BLOCK_CACHE.put(material, b);
            return b;
        } catch (final Exception t) {
            LOG.log(Level.FINE, "Failed to convert Material to NMS Block: " + material, t);
            return null;
        }
    }

    private static final Map<String, Long> INTERACTION_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long INTERACTION_DEBOUNCE_MS = 200L;

    private static boolean isDebouncedAndMark(final UUID playerUuid, final String world, final int x, final int y, final int z) {
        final String key = playerUuid + ":" + world + ":" + x + ":" + y + ":" + z;
        final long now = System.currentTimeMillis();
        final Long prev = INTERACTION_DEBOUNCE.get(key);
        if (prev != null && (now - prev) < INTERACTION_DEBOUNCE_MS) return true;
        INTERACTION_DEBOUNCE.put(key, now);
        return false;
    }

    private static final Map<UUID, Long> USEITEM_PROCESS_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long USEITEM_PROCESS_DEBOUNCE_MS = 350L;

    private static boolean isUseItemDebouncedAndMark(final UUID playerUuid) {
        final long now = System.currentTimeMillis();
        final Long prev = USEITEM_PROCESS_DEBOUNCE.get(playerUuid);
        if (prev != null && (now - prev) < USEITEM_PROCESS_DEBOUNCE_MS) return true;
        USEITEM_PROCESS_DEBOUNCE.put(playerUuid, now);
        return false;
    }

    private void sendFakeRefreshToPlayer(final Player player, final ServerLevel level, final BlockPos pos) {
        sendFakeRefreshToPlayer(player, level, pos, null);
    }

    private void sendFakeRefreshToPlayer(final Player player, final ServerLevel level, final BlockPos pos, final net.minecraft.world.level.block.state.BlockState forcedState) {
        if (player == null) return;
        try {
            final UUID pu = player.getUniqueId();
            final String worldName = player.getWorld().getName();
            if (isDebouncedAndMark(pu, worldName, pos.getX(), pos.getY(), pos.getZ())) return;
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            final net.minecraft.world.level.block.state.BlockState state = forcedState == null ? level.getBlockState(pos) : forcedState;
            handle.connection.send(new ClientboundBlockUpdatePacket(pos, state));
            try {
                final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                if (plugin != null) {
                    FoliaSafeScheduler.runTaskLater(plugin, () -> {
                        try {
                            ((CraftPlayer) player).getHandle().connection.send(new ClientboundBlockUpdatePacket(pos, state));
                        } catch (final Exception ignored) { }
                    }, 1L);
                }
            } catch (final Exception ignored) { }
        } catch (final Exception t) {
            LOG.log(Level.FINE, "Error sending fake refresh", t);
        }
    }

    public static void inject(final Player player) {
        if (player == null) return;
        final UUID id = player.getUniqueId();
        INSTANCES.compute(id, (k, existing) -> {
            if (existing != null) return existing;
            final FakeBlockPacketHandler h = new FakeBlockPacketHandler(player);
            h.inject();
            return h;
        });
    }

    public static void uninject(final Player player) {
        if (player == null) return;
        final UUID id = player.getUniqueId();
        final FakeBlockPacketHandler h = INSTANCES.remove(id);
        if (h != null) h.uninject();
    }

    public void inject() {
        final Player player = this.playerRef.get();
        if (player == null) return;
        try {
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            final Channel channel = resolveChannel(handle);
            if (channel == null) return;
            final Channel pipelineChannel = channel;
            final UUID uid = player.getUniqueId();
            pipelineChannel.eventLoop().execute(() -> {
                try {
                    if (pipelineChannel.pipeline().get(pipelineName) == null) {
                        pipelineChannel.pipeline().addLast(pipelineName, this);
                        try {
                            final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                            if (pl != null) pl.getLogger().fine("Injected FakeBlockPacketHandler for " + player.getName());
                        } catch (final Exception ignored) {
                            LOG.log(Level.FINE, "Exception while logging injection for " + player.getName(), ignored);
                        }
                        try {
                            pipelineChannel.closeFuture().addListener(f -> {
                                try {
                                    INSTANCES.remove(uid, this);
                                    if (pipelineChannel.pipeline().get(pipelineName) != null) {
                                        try { pipelineChannel.pipeline().remove(pipelineName); } catch (final Exception ignoredInner) { }
                                    }
                                } catch (final Exception ignoredInner) { }
                            });
                        } catch (final Exception ignored) { }
                    }
                } catch (final Exception t) {
                    LOG.log(Level.WARNING, "Error while injecting pipeline handler for " + player.getName(), t);
                }
            });
        } catch (final Exception ignored) {
            LOG.log(Level.WARNING, "Failed to inject FakeBlockPacketHandler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    public void uninject() {
        final Player player = this.playerRef.get();
        if (player == null) return;
        try {
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            final Channel channel = resolveChannel(handle);
            if (channel == null) return;
            final Channel pipelineChannel = channel;
            pipelineChannel.eventLoop().execute(() -> {
                if (pipelineChannel.pipeline().get(pipelineName) != null) {
                    try {
                        pipelineChannel.pipeline().remove(pipelineName);
                        try {
                            final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                            if (pl != null) pl.getLogger().fine("Uninjected FakeBlockPacketHandler for " + player.getName());
                        } catch (final Exception ignored) {
                            LOG.log(Level.FINE, "Exception while logging uninjection for " + player.getName(), ignored);
                        }
                    } catch (final Exception ignored) {
                        LOG.log(Level.WARNING, "Error while removing pipeline handler for " + player.getName(), ignored);
                    }
                }
            });
        } catch (final Exception ignored) {
            LOG.log(Level.WARNING, "Failed to uninject FakeBlockPacketHandler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (isInteractPacket(msg)) {
            handleInterceptedPacket(msg);
        }
        super.channelRead(ctx, msg);
    }

    private static boolean isInteractPacket(final Object msg) {
        return msg instanceof ServerboundPlayerActionPacket
            || msg instanceof ServerboundUseItemOnPacket
            || msg instanceof ServerboundUseItemPacket;
    }

    private void handleInterceptedPacket(final Object msg) {
        final Player player = this.playerRef.get();
        if (player == null) return;
        final BlockPos pos = extractBlockPos(msg);
        if (pos == null) return;
        try {
            if (isFakeBlockOrContains(player, pos)) {
                final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
                sendFakeRefreshToPlayer(player, level, pos);
            } else if (msg instanceof ServerboundUseItemPacket) {
                handleUseItemPacket(player);
            }
        } catch (final Exception t) {
            LOG.log(Level.WARNING, "Error while handling intercepted packet for " + player.getName(), t);
        }
    }

    private boolean isFakeBlockOrContains(final Player player, final BlockPos pos) {
        if (CHECKER != null) {
            try {
                if (CHECKER.isFake(player, pos)) return true;
            } catch (final Exception ignored) { }
        }
        try {
            return registryContains(player.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ());
        } catch (final Exception ignored) {
            return false;
        }
    }

    private void handleUseItemPacket(final Player player) {
        final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin != null && !isUseItemDebouncedAndMark(player.getUniqueId())) {
            FoliaSafeScheduler.runTask(plugin, () -> {
                try {
                    final Player p = this.playerRef.get();
                    if (p == null) return;
                    final RayTraceResult trace = p.getWorld().rayTraceBlocks(
                            p.getEyeLocation(), p.getEyeLocation().getDirection(), 8.0D, FluidCollisionMode.NEVER, true);
                    if (trace != null && trace.getHitBlock() != null) {
                        final org.bukkit.block.Block b = trace.getHitBlock();
                        if (registryContains(p.getWorld().getName(), b.getX(), b.getY(), b.getZ())) {
                            sendFakeRefreshToPlayer(p, ((CraftWorld) p.getWorld()).getHandle(), new BlockPos(b.getX(), b.getY(), b.getZ()));
                        }
                    }
                } catch (final Exception ignored) { }
            });
        }
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        final Player player = this.playerRef.get();
        if (player == null) { super.write(ctx, msg, promise); return; }

        if (trySuppressBlockUpdate(ctx, msg, promise, player)) {
            return;
        }

        resendForChunkPackets(msg, player);
        super.write(ctx, msg, promise);
    }

    private boolean trySuppressBlockUpdate(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise, final Player player) throws Exception {
        if (!(msg instanceof ClientboundBlockUpdatePacket packet)) {
            return false;
        }
        final BlockPos pos = extractBlockPos(packet);
        if (pos == null || !registryContains(player.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        final String materialName = registryGetMaterial(player.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ());
        if (materialName != null) {
            try {
                final Material mat = Material.valueOf(materialName);
                final Block nmsBlock = getNmsBlockForMaterial(mat);
                if (nmsBlock != null) {
                    super.write(ctx, new ClientboundBlockUpdatePacket(pos, nmsBlock.defaultBlockState()), promise);
                    return true;
                }
            } catch (final IllegalArgumentException ignored) { }
        }
        return true;
    }

    private void resendForChunkPackets(final Object msg, final Player player) {
        try {
            final String cls = msg.getClass().getSimpleName();
            if (isChunkUpdatePacket(cls)) {
                final int[] chunk = tryExtractChunkCoords(msg);
                if (chunk != null) {
                    scheduleResendForChunk(player, player.getWorld().getName(), chunk[0], chunk[1]);
                } else {
                    scheduleResendForWorld(player, player.getWorld().getName());
                }
            }
        } catch (final Exception ignored) { }
    }

    private static boolean isChunkUpdatePacket(final String className) {
        return className.contains("SectionBlocksUpdate")
            || className.contains("MultiBlockChange")
            || className.contains("Chunk")
            || className.contains("LevelChunk");
    }

    private Channel resolveChannel(final ServerPlayer handle) {
        try {
            final Object packetListener = handle.connection;
            if (packetListener == null) return null;
            for (final Field f : packetListener.getClass().getDeclaredFields()) {
                try {
                    // Skip static fields — they cannot be a per-connection Channel
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    // Skip JDK and internal types that can never hold a Channel
                    final Class<?> ft = f.getType();
                    if (ft.isPrimitive()) continue;
                    final String ftName = ft.getName();
                    if (ftName.startsWith("java.") || ftName.startsWith("javax.") || ftName.startsWith("org.slf4j")
                        || ftName.startsWith("org.apache.logging") || ftName.startsWith("org.bukkit.")
                        || ftName.startsWith("com.google.") || ftName.startsWith("it.unimi.dsi.")
                        || ftName.startsWith("com.destroystokyo.") || ftName.startsWith("org.intellij.")
                        || ftName.startsWith("org.jetbrains.")) continue;

                    ReflectionUtil.setAccessibleQuietly(f);
                    final Object val = f.get(packetListener);
                    if (val == null) continue;
                    if (val instanceof Channel ch) return ch;
                    for (final Field g : val.getClass().getDeclaredFields()) {
                        try {
                            // Skip static fields and JDK types in nested objects too
                            if (java.lang.reflect.Modifier.isStatic(g.getModifiers())) continue;
                            final Class<?> gt = g.getType();
                            if (gt.isPrimitive()) continue;
                            final String gtName = gt.getName();
                            if (gtName.startsWith("java.") || gtName.startsWith("javax.") || gtName.startsWith("org.slf4j")
                                || gtName.startsWith("org.apache.logging") || gtName.startsWith("org.bukkit.")
                                || gtName.startsWith("com.google.") || gtName.startsWith("it.unimi.dsi.")
                                || gtName.startsWith("com.destroystokyo.") || gtName.startsWith("org.intellij.")
                                || gtName.startsWith("org.jetbrains.")) continue;

                            ReflectionUtil.setAccessibleQuietly(g);
                            final Object inner = g.get(val);
                            if (inner instanceof Channel ch2) return ch2;
                        } catch (final Exception ignored) { }
                    }
                } catch (final Exception ignored) { }
            }
        } catch (final Exception ignored) { }
        return null;
    }

    private BlockPos extractBlockPos(final Object packet) {
        if (packet == null) return null;
        try {
            try {
                final java.lang.reflect.Method m = packet.getClass().getDeclaredMethod("getPos");
                ReflectionUtil.safeSetAccessible(m, "NMS packet getPos method");
                final Object res = m.invoke(packet);
                if (res instanceof BlockPos bp) return bp;
            } catch (final NoSuchMethodException ignored) { }
            for (final Field f : packet.getClass().getDeclaredFields()) {
                try {
                    ReflectionUtil.setAccessibleQuietly(f);
                    final Object val = f.get(packet);
                    if (val instanceof BlockPos) return (BlockPos) val;
                    if (val instanceof Integer) {
                        final Integer x = findIntField(packet.getClass(), packet, "x", "posX", "blockX");
                        final Integer y = findIntField(packet.getClass(), packet, "y", "posY", "blockY");
                        final Integer z = findIntField(packet.getClass(), packet, "z", "posZ", "blockZ");
                        if (x != null && y != null && z != null) return new BlockPos(x, y, z);
                    }
                } catch (final Exception ignored) { }
            }
        } catch (final Exception ignored) { }
        return null;
    }

    private Integer findIntField(final Class<?> c, final Object instance, final String... names) {
        for (final String name : names) {
            try {
                final Field f = c.getDeclaredField(name);
                ReflectionUtil.setAccessibleQuietly(f);
                final Object o = f.get(instance);
                if (o instanceof Integer) return (Integer) o;
            } catch (final Exception ignored) { }
        }
        for (final Field f : c.getDeclaredFields()) {
            try {
                if (f.getType() == int.class || f.getType() == Integer.class) {
                    ReflectionUtil.setAccessibleQuietly(f);
                    final Object o = f.get(instance);
                    if (o instanceof Integer) return (Integer) o;
                }
            } catch (final Exception ignored) { }
        }
        return null;
    }
}