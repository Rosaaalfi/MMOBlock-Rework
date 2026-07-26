package me.chyxelmc.mmoblock.nms;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import me.chyxelmc.mmoblock.nms.utils.NmsLogger;
import me.chyxelmc.mmoblock.nms.utils.ReflectionUtil;
import me.chyxelmc.mmoblock.platform.scheduler.FoliaSafeScheduler;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"unchecked", "rawtypes", "java:S101", "java:S3008", "java:S3077"})
public abstract class AbstractFakeBlockPacketHandler extends ChannelDuplexHandler {

    protected static final String PLUGIN_NAME = "MMOBlock";
    protected static final String FAKE_BLOCK_REGISTRY_CLASS = "me.chyxelmc.mmoblock.runtime.FakeBlockRegistry";

    // Static INSTANCES map — accessible by concrete subclasses for inject/uninject
    protected static final Map<UUID, AbstractFakeBlockPacketHandler> INSTANCES = new ConcurrentHashMap<>();

    protected final WeakReference<Player> playerRef;
    protected final String pipelineName;

    // ============================================================
    // HOOK METHODS (12 total)
    // ============================================================

    protected abstract Object getServerPlayer(Player player);
    protected abstract Object getServerLevel(World world);
    protected abstract Object getNmsBlock(Material material);
    protected abstract Object nmsDefaultBlockState(Object nmsBlock);
    protected abstract boolean isClientBoundBlockUpdate(Object msg);
    protected abstract boolean isServerBoundPlayerAction(Object msg);
    protected abstract boolean isServerBoundUseItemOn(Object msg);
    protected abstract boolean isServerBoundUseItem(Object msg);
    protected abstract Object createBlockUpdatePacket(Object blockPos, Object blockState);
    protected abstract Object getBlockState(Object serverLevel, Object blockPos);
    protected abstract Object createBlockPos(int x, int y, int z);
    protected abstract void sendPacket(Object serverPlayer, Object packet);

    // BlockPos coordinate extraction — default uses reflection
    protected int posX(Object blockPos) {
        try { return (int) blockPos.getClass().getMethod("getX").invoke(blockPos); }
        catch (Exception e) { return 0; }
    }
    protected int posY(Object blockPos) {
        try { return (int) blockPos.getClass().getMethod("getY").invoke(blockPos); }
        catch (Exception e) { return 0; }
    }
    protected int posZ(Object blockPos) {
        try { return (int) blockPos.getClass().getMethod("getZ").invoke(blockPos); }
        catch (Exception e) { return 0; }
    }

    // ============================================================
    // Constructor
    // ============================================================

    public AbstractFakeBlockPacketHandler(final Player player) {
        this.playerRef = new WeakReference<>(player);
        this.pipelineName = "mmoblock-fakeblock-" + player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    }

    // ============================================================
    // Static state
    // ============================================================

    private static volatile Class<?> REG_CLASS = null;
    private static volatile Method REG_CONTAINS = null;
    private static volatile Method REG_GET_MATERIAL = null;
    private static volatile Method REG_GET_KEYS = null;
    private static final Object REG_LOCK = new Object();
    private static final Map<String, Long> INTERACTION_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long INTERACTION_DEBOUNCE_MS = 200L;
    private static final Map<UUID, Long> USEITEM_PROCESS_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long USEITEM_PROCESS_DEBOUNCE_MS = 350L;

    @FunctionalInterface
    public interface FakeBlockChecker {
        boolean isFake(Player player, Object blockPos);
    }

    private static volatile FakeBlockChecker CHECKER = null;

    public static void setFakeChecker(final FakeBlockChecker checker) {
        CHECKER = checker;
    }

    // ============================================================
    // Instance injection / uninjection
    // ============================================================

    public void inject() {
        final Player player = this.playerRef.get();
        if (player == null) return;
        try {
            final Object handle = getServerPlayer(player);
            final Channel channel = resolveChannel(handle);
            if (channel == null) return;
            final Channel pipelineChannel = channel;
            final UUID uid = player.getUniqueId();
            pipelineChannel.eventLoop().execute(() -> {
                try {
                    if (pipelineChannel.pipeline().get(pipelineName) == null) {
                        pipelineChannel.pipeline().addLast(pipelineName, this);
                        logDebug("Injected FakeBlockPacketHandler for " + player.getName());
                        try {
                            pipelineChannel.closeFuture().addListener(f -> {
                                INSTANCES.remove(uid, this);
                                if (pipelineChannel.pipeline().get(pipelineName) != null) {
                                    try { pipelineChannel.pipeline().remove(pipelineName); } catch (Exception e) {
                                        NmsLogger.debug("Failed to remove pipeline handler on close: " + e.getMessage());
                                    }
                                }
                            });
                        } catch (Exception e) {
                            NmsLogger.debug("Failed to add closeFuture listener: " + e.getMessage());
                        }
                    }
                } catch (final Exception t) {
                    NmsLogger.warning("Error while injecting pipeline handler for " + player.getName(), t);
                }
            });
        } catch (final Exception ignored) {
            NmsLogger.warning("Failed to inject handler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    public void uninject() {
        final Player player = this.playerRef.get();
        if (player == null) return;
        try {
            final Object handle = getServerPlayer(player);
            final Channel channel = resolveChannel(handle);
            if (channel == null) return;
            final Channel pipelineChannel = channel;
            pipelineChannel.eventLoop().execute(() -> {
                if (pipelineChannel.pipeline().get(pipelineName) != null) {
                    try {
                        pipelineChannel.pipeline().remove(pipelineName);
                        logDebug("Uninjected FakeBlockPacketHandler for " + player.getName());
                    } catch (Exception ignored) {
                        NmsLogger.warning("Error while removing pipeline handler for " + player.getName(), ignored);
                    }
                }
            });
        } catch (final Exception ignored) {
            NmsLogger.warning("Failed to uninject handler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    // ============================================================
    // Packet interception (channelRead)
    // ============================================================

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (isInteractPacket(msg)) handleInterceptedPacket(msg);
        super.channelRead(ctx, msg);
    }

    private boolean isInteractPacket(final Object msg) {
        return isServerBoundPlayerAction(msg) || isServerBoundUseItemOn(msg) || isServerBoundUseItem(msg);
    }

    private void handleInterceptedPacket(final Object msg) {
        final Player player = this.playerRef.get();
        if (player == null) return;
        final Object pos = extractBlockPos(msg);
        if (pos == null) return;
        try {
            if (isFakeBlockOrContains(player, pos)) {
                sendFakeRefreshToPlayer(player, getServerLevel(player.getWorld()), pos);
            } else if (isServerBoundUseItem(msg)) {
                handleUseItemPacket(player);
            }
        } catch (final Exception t) {
            NmsLogger.warning("Error handling intercepted packet for " + player.getName(), t);
        }
    }

    // ============================================================
    // Packet output interception (write)
    // ============================================================

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        final Player player = this.playerRef.get();
        if (player == null) { super.write(ctx, msg, promise); return; }
        if (trySuppressBlockUpdate(ctx, msg, promise, player)) return;
        resendForChunkPackets(msg, player);
        super.write(ctx, msg, promise);
    }

    private boolean trySuppressBlockUpdate(ChannelHandlerContext ctx, Object msg, ChannelPromise promise, Player player) throws Exception {
        if (!isClientBoundBlockUpdate(msg)) return false;
        final Object pos = extractBlockPos(msg);
        if (pos == null) return false;
        final int px = posX(pos), py = posY(pos), pz = posZ(pos);
        if (!registryContains(player.getWorld().getName(), px, py, pz)) return false;
        final String matName = registryGetMaterial(player.getWorld().getName(), px, py, pz);
        if (matName != null) {
            try {
                final Material mat = Material.valueOf(matName);
                final Object nmsBlock = getNmsBlock(mat);
                if (nmsBlock != null) {
                    super.write(ctx, createBlockUpdatePacket(pos, nmsDefaultBlockState(nmsBlock)), promise);
                    return true;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return true;
    }

    // ============================================================
    // Chunk resend logic
    // ============================================================

    private void resendForChunkPackets(Object msg, Player player) {
        try {
            final String cls = msg.getClass().getSimpleName();
            if (cls.contains("SectionBlocksUpdate") || cls.contains("MultiBlockChange")
                    || cls.contains("Chunk") || cls.contains("LevelChunk")) {
                int[] chunk = tryExtractChunkCoords(msg);
                if (chunk != null) scheduleResendForChunk(player, player.getWorld().getName(), chunk[0], chunk[1]);
                else scheduleResendForWorld(player, player.getWorld().getName());
            }
        } catch (Exception e) {
            NmsLogger.debug("resendForChunkPackets failed: " + e.getMessage());
        }
    }

    private void scheduleResendForChunk(Player player, String worldName, int chunkX, int chunkZ) {
        try {
            Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            Object level = getServerLevel(player.getWorld());
            for (String key : keys) {
                try {
                    String[] parts = key.split(":");
                    if (parts.length != 4) continue;
                    int bx = Integer.parseInt(parts[1]), by = Integer.parseInt(parts[2]), bz = Integer.parseInt(parts[3]);
                    if ((bx >> 4) != chunkX || (bz >> 4) != chunkZ) continue;
                    Object bp = createBlockPos(bx, by, bz);
                    Object sendState = computeSendState(level, bp, registryGetMaterial(worldName, bx, by, bz));
                    if (sendState == null) continue;
                    sendFakeRefreshToPlayer(player, level, bp, sendState);
                } catch (Exception e) {
                    NmsLogger.debug("scheduleResendForChunk inner loop: " + e.getMessage());
                }
            }
        } catch (Exception t) { NmsLogger.debug("Failed to schedule resend for chunk", t); }
    }

    private void scheduleResendForWorld(Player player, String worldName) {
        try {
            Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            Object level = getServerLevel(player.getWorld());
            for (String key : keys) {
                try {
                    String[] parts = key.split(":");
                    if (parts.length != 4) continue;
                    int bx = Integer.parseInt(parts[1]), by = Integer.parseInt(parts[2]), bz = Integer.parseInt(parts[3]);
                    Object bp = createBlockPos(bx, by, bz);
                    Object sendState = computeSendState(level, bp, registryGetMaterial(worldName, bx, by, bz));
                    if (sendState == null) continue;
                    sendFakeRefreshToPlayer(player, level, bp, sendState);
                } catch (Exception e) {
                    NmsLogger.debug("scheduleResendForWorld inner loop: " + e.getMessage());
                }
            }
        } catch (Exception t) { NmsLogger.debug("Failed to schedule resend for world", t); }
    }

    private Object computeSendState(Object level, Object pos, String materialName) {
        if (materialName != null) {
            try {
                Material mat = Material.valueOf(materialName);
                Object nb = getNmsBlock(mat);
                if (nb != null) return nmsDefaultBlockState(nb);
            } catch (IllegalArgumentException ignored) { }
        }
        try { return getBlockState(level, pos); }
        catch (Exception t) { NmsLogger.debug("Failed to get block state", t); return null; }
    }

    // ============================================================
    // Fake block refresh
    // ============================================================

    private void sendFakeRefreshToPlayer(Player player, Object level, Object pos) {
        sendFakeRefreshToPlayer(player, level, pos, null);
    }

    private void sendFakeRefreshToPlayer(Player player, Object level, Object pos, Object forcedState) {
        if (player == null) return;
        try {
            UUID pu = player.getUniqueId();
            String wn = player.getWorld().getName();
            int px = posX(pos), py = posY(pos), pz = posZ(pos);
            if (isDebouncedAndMark(pu, wn, px, py, pz)) return;
            Object handle = getServerPlayer(player);
            Object state = forcedState == null ? getBlockState(level, pos) : forcedState;
            sendPacket(handle, createBlockUpdatePacket(pos, state));
            Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin != null) FoliaSafeScheduler.runTaskLater(plugin, () -> {
                try { sendPacket(getServerPlayer(player), createBlockUpdatePacket(pos, state)); } catch (Exception e) {
                    NmsLogger.debug("Delayed fake refresh send failed: " + e.getMessage());
                }
            }, 1L);
        } catch (Exception t) { NmsLogger.debug("Error sending fake refresh", t); }
    }

    private static boolean isDebouncedAndMark(UUID pu, String w, int x, int y, int z) {
        String key = pu + ":" + w + ":" + x + ":" + y + ":" + z;
        long now = System.currentTimeMillis();
        Long prev = INTERACTION_DEBOUNCE.get(key);
        if (prev != null && (now - prev) < INTERACTION_DEBOUNCE_MS) return true;
        INTERACTION_DEBOUNCE.put(key, now);
        return false;
    }

    // ============================================================
    // Use-item handling
    // ============================================================

    private void handleUseItemPacket(Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin != null && !isUseItemDebounced(player.getUniqueId())) {
            FoliaSafeScheduler.runTask(plugin, () -> {
                try {
                    Player p = this.playerRef.get();
                    if (p == null) return;
                    RayTraceResult trace = p.getWorld().rayTraceBlocks(
                            p.getEyeLocation(), p.getEyeLocation().getDirection(), 8.0D, FluidCollisionMode.NEVER, true);
                    if (trace != null && trace.getHitBlock() != null) {
                        org.bukkit.block.Block b = trace.getHitBlock();
                        if (registryContains(p.getWorld().getName(), b.getX(), b.getY(), b.getZ())) {
                            sendFakeRefreshToPlayer(p, getServerLevel(p.getWorld()), createBlockPos(b.getX(), b.getY(), b.getZ()));
                        }
                    }
                } catch (Exception e) {
                    NmsLogger.debug("handleUseItemPacket raytrace failed: " + e.getMessage());
                }
            });
        }
    }

    private static boolean isUseItemDebounced(UUID pu) {
        long now = System.currentTimeMillis();
        Long prev = USEITEM_PROCESS_DEBOUNCE.get(pu);
        if (prev != null && (now - prev) < USEITEM_PROCESS_DEBOUNCE_MS) return true;
        USEITEM_PROCESS_DEBOUNCE.put(pu, now);
        return false;
    }

    // ============================================================
    // Fake block check
    // ============================================================

    private boolean isFakeBlockOrContains(Player player, Object pos) {
        if (CHECKER != null) {
            try { if (CHECKER.isFake(player, pos)) return true; } catch (Exception e) {
                NmsLogger.debug("FakeBlockChecker failed: " + e.getMessage());
            }
        }
        try { return registryContains(player.getWorld().getName(), posX(pos), posY(pos), posZ(pos)); }
        catch (Exception e) { NmsLogger.debug("registryContains in isFakeBlockOrContains failed: " + e.getMessage()); return false; }
    }

    // ============================================================
    // Registry reflection helpers
    // ============================================================

    private static void ensureRegistryReflectiveInit() {
        if (REG_CLASS != null) return;
        synchronized (REG_LOCK) {
            if (REG_CLASS != null) return;
            try {
                REG_CLASS = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
                REG_CONTAINS = REG_CLASS.getMethod("contains", String.class, int.class, int.class, int.class);
                REG_GET_MATERIAL = REG_CLASS.getMethod("getMaterial", String.class, int.class, int.class, int.class);
                try { REG_GET_KEYS = REG_CLASS.getMethod("positionsForWorld", String.class); }
                catch (NoSuchMethodException ignored) { REG_GET_KEYS = null; }
            } catch (Exception t) {
                REG_CLASS = null; REG_CONTAINS = null; REG_GET_MATERIAL = null;
                NmsLogger.debug("Failed to init FakeBlockRegistry reflectors", t);
            }
        }
    }

    private static boolean registryContains(String world, int x, int y, int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_CONTAINS != null) return Boolean.TRUE.equals(REG_CONTAINS.invoke(null, world, x, y, z));
            Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            return Boolean.TRUE.equals(c.getMethod("contains", String.class, int.class, int.class, int.class).invoke(null, world, x, y, z));
        } catch (Exception t) { NmsLogger.debug("registryContains failed", t); return false; }
    }

    private static String registryGetMaterial(String world, int x, int y, int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_MATERIAL != null) {
                Object res = REG_GET_MATERIAL.invoke(null, world, x, y, z);
                return res instanceof String s ? s : null;
            }
            Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            Object res = c.getMethod("getMaterial", String.class, int.class, int.class, int.class).invoke(null, world, x, y, z);
            return res instanceof String s ? s : null;
        } catch (Exception t) { NmsLogger.debug("registryGetMaterial failed", t); return null; }
    }

    private static Set<String> registryGetKeysForWorld(String world) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_KEYS != null) {
                Object res = REG_GET_KEYS.invoke(null, world);
                if (res instanceof Set) return extractStringSet((Set<?>) res);
            }
            Class<?> c = Class.forName(FAKE_BLOCK_REGISTRY_CLASS);
            Object res = c.getMethod("positionsForWorld", String.class).invoke(null, world);
            if (res instanceof Set) return extractStringSet((Set<?>) res);
            return Collections.emptySet();
        } catch (Exception t) { NmsLogger.debug("registryGetKeysForWorld failed", t); return Collections.emptySet(); }
    }

    private static Set<String> extractStringSet(Set<?> raw) {
        Set<String> out = new HashSet<>();
        for (Object o : raw) { if (o instanceof String s) out.add(s); }
        return out;
    }

    // ============================================================
    // Chunk coordinate extraction (reflection-based, version-agnostic)
    // ============================================================

    private int[] tryExtractChunkCoords(Object packet) {
        if (packet == null) return null;
        try {
            try {
                Method mx = packet.getClass().getDeclaredMethod("getChunkX");
                Method mz = packet.getClass().getDeclaredMethod("getChunkZ");
                ReflectionUtil.safeSetAccessible(mx, "getChunkX");
                ReflectionUtil.safeSetAccessible(mz, "getChunkZ");
                Object ox = mx.invoke(packet), oz = mz.invoke(packet);
                if (ox instanceof Integer ix && oz instanceof Integer iz) return new int[]{ix, iz};
            } catch (Exception e) {
                NmsLogger.debug("tryExtractChunkCoords getChunkX/Z failed: " + e.getMessage());
            }
            Integer cx = null, cz = null;
            for (Field f : packet.getClass().getDeclaredFields()) {
                try {
                    ReflectionUtil.setAccessibleQuietly(f);
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    Object val = f.get(packet);
                    if (val == null) continue;
                    if ((name.contains("chunkx") || name.equals("x")) && val instanceof Integer ix) cx = ix;
                    if ((name.contains("chunkz") || name.equals("z")) && val instanceof Integer iz) cz = iz;
                    if (cx != null && cz != null) return new int[]{cx, cz};
                } catch (Exception e) {
                    NmsLogger.debug("tryExtractChunkCoords field scan: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            NmsLogger.debug("tryExtractChunkCoords outer failed: " + e.getMessage());
        }
        return null;
    }

    // ============================================================
    // BlockPos extraction (reflection-based, version-agnostic)
    // ============================================================

    private Object extractBlockPos(Object packet) {
        if (packet == null) return null;
        try {
            try {
                Method m = packet.getClass().getDeclaredMethod("getPos");
                ReflectionUtil.safeSetAccessible(m, "NMS getPos");
                Object res = m.invoke(packet);
                if (res != null) return res;
            } catch (NoSuchMethodException ignored) { }
            for (Field f : packet.getClass().getDeclaredFields()) {
                try {
                    ReflectionUtil.setAccessibleQuietly(f);
                    Object val = f.get(packet);
                    if (val != null) {
                        String tn = val.getClass().getName();
                        if (tn.contains("BlockPos")) return val;
                    }
                    if (val instanceof Integer) {
                        Integer x = findIntField(packet.getClass(), packet, "x", "posX", "blockX");
                        Integer y = findIntField(packet.getClass(), packet, "y", "posY", "blockY");
                        Integer z = findIntField(packet.getClass(), packet, "z", "posZ", "blockZ");
                        if (x != null && y != null && z != null) return createBlockPos(x, y, z);
                    }
                } catch (Exception e) {
                    NmsLogger.debug("extractBlockPos field scan: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            NmsLogger.debug("extractBlockPos outer failed: " + e.getMessage());
        }
        return null;
    }

    private Integer findIntField(Class<?> c, Object instance, String... names) {
        for (String name : names) {
            try {
                Field f = c.getDeclaredField(name);
                ReflectionUtil.setAccessibleQuietly(f);
                Object o = f.get(instance);
                if (o instanceof Integer) return (Integer) o;
            } catch (Exception e) {
                NmsLogger.debug("findIntField by name '" + name + "': " + e.getMessage());
            }
        }
        for (Field f : c.getDeclaredFields()) {
            try {
                if (f.getType() == int.class || f.getType() == Integer.class) {
                    ReflectionUtil.setAccessibleQuietly(f);
                    Object o = f.get(instance);
                    if (o instanceof Integer) return (Integer) o;
                }
            } catch (Exception e) {
                NmsLogger.debug("findIntField scan: " + e.getMessage());
            }
        }
        return null;
    }

    // ============================================================
    // Channel resolution (reflection-based, version-agnostic)
    // ============================================================

    private Channel resolveChannel(Object serverPlayer) {
        try {
            Object packetListener = getPublicField(serverPlayer, "connection");
            if (packetListener == null) return null;
            for (Field f : packetListener.getClass().getDeclaredFields()) {
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> ft = f.getType();
                    if (ft.isPrimitive() || isSkippable(ft.getName())) continue;
                    ReflectionUtil.setAccessibleQuietly(f);
                    Object val = f.get(packetListener);
                    if (val instanceof Channel ch) return ch;
                    for (Field g : val.getClass().getDeclaredFields()) {
                        try {
                            if (java.lang.reflect.Modifier.isStatic(g.getModifiers())) continue;
                            if (g.getType().isPrimitive() || isSkippable(g.getType().getName())) continue;
                            ReflectionUtil.setAccessibleQuietly(g);
                            Object inner = g.get(val);
                            if (inner instanceof Channel ch2) return ch2;
                        } catch (Exception e) {
                            NmsLogger.debug("resolveChannel inner field: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    NmsLogger.debug("resolveChannel outer field: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            NmsLogger.debug("resolveChannel failed: " + e.getMessage());
        }
        return null;
    }

    private Object getPublicField(Object obj, String name) {
        try {
            Field f = obj.getClass().getField(name);
            return f.get(obj);
        } catch (Exception e) {
            NmsLogger.debug("getPublicField public access failed for '" + name + "': " + e.getMessage());
            try {
                for (Field f : obj.getClass().getDeclaredFields()) {
                    if (f.getName().equals(name)) {
                        ReflectionUtil.setAccessibleQuietly(f);
                        return f.get(obj);
                    }
                }
            } catch (Exception e2) {
                NmsLogger.debug("getPublicField declared fallback failed for '" + name + "': " + e2.getMessage());
            }
        }
        return null;
    }

    private static boolean isSkippable(String tn) {
        return tn.startsWith("java.") || tn.startsWith("javax.") || tn.startsWith("org.slf4j")
            || tn.startsWith("org.apache.logging") || tn.startsWith("org.bukkit.")
            || tn.startsWith("com.google.") || tn.startsWith("it.unimi.dsi.")
            || tn.startsWith("com.destroystokyo.") || tn.startsWith("org.intellij.")
            || tn.startsWith("org.jetbrains.");
    }

    // ============================================================
    // Logging helper
    // ============================================================

    private void logDebug(String msg) {
        try {
            Plugin pl = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (pl != null) NmsLogger.debug(msg);
        } catch (Exception e) {
            NmsLogger.debug("logDebug failed: " + e.getMessage());
        }
    }
}
