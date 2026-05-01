package me.chyxelmc.mmoblock.nms.mojang.v1_20_4;

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
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.RayTraceResult;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers;
import net.minecraft.world.level.block.Block;

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

    private final WeakReference<Player> playerRef;
    private final String pipelineName;

    // Logger for debugging packet handling / injection issues
    private static final Logger LOG = Bukkit.getLogger();

    public FakeBlockPacketHandler(final Player player) {
        this.playerRef = new WeakReference<>(player);
        this.pipelineName = "mmoblock-fakeblock-" + player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    }

    // Try to extract chunk coordinates (chunkX, chunkZ) from common packet fields.
    // Returns null if extraction fails.
    private int[] tryExtractChunkCoords(final Object packet) {
        if (packet == null) return null;
        try {
            // Try common accessor methods
            try {
                final java.lang.reflect.Method mx = packet.getClass().getDeclaredMethod("getChunkX");
                final java.lang.reflect.Method mz = packet.getClass().getDeclaredMethod("getChunkZ");
                mx.setAccessible(true);
                mz.setAccessible(true);
                final Object ox = mx.invoke(packet);
                final Object oz = mz.invoke(packet);
                if (ox instanceof Integer ix && oz instanceof Integer iz) return new int[]{ix, iz};
            } catch (final Throwable ignored) {
            }

            // Try common field names (chunkX, chunkZ, x, z)
            Integer cx = null, cz = null;
            for (final Field f : packet.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    final String name = f.getName().toLowerCase(Locale.ROOT);
                    final Object val = f.get(packet);
                    if (val == null) continue;
                    if ((name.contains("chunkx") || name.equals("x") || name.equals("chunkx")) && val instanceof Integer ix) cx = ix;
                    if ((name.contains("chunkz") || name.equals("z") || name.equals("chunkz")) && val instanceof Integer iz) cz = iz;
                    if (cx != null && cz != null) return new int[]{cx, cz};
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    // Schedule resend for all fake positions inside the specified chunk for the given player.
    private void scheduleResendForChunk(final Player player, final String worldName, final int chunkX, final int chunkZ) {
        try {
            final java.util.Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
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
                    // Attempt to obtain material and send a fake-state; otherwise send level state
                    final String matName = registryGetMaterial(worldName, bx, by, bz);
                    final net.minecraft.world.level.block.state.BlockState sendState = computeSendState(level, bp, matName);
                    if (sendState == null) continue;
                    // Use debounced sender so we don't duplicate sends that originate
                    // from other packet handlers for the same interaction.
                    sendFakeRefreshToPlayer(player, level, bp, sendState);
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "Failed to schedule resend for chunk", t);
        }
    }

    // Schedule resend for all fake positions in the world (fallback).
    private void scheduleResendForWorld(final Player player, final String worldName) {
        try {
            final java.util.Set<String> keys = registryGetKeysForWorld(worldName);
            if (keys == null || keys.isEmpty()) return;
            final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
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
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "Failed to schedule resend for world", t);
        }
    }

    // Compute block state to send based on optional materialName; falls back to level state.
    private net.minecraft.world.level.block.state.BlockState computeSendState(final ServerLevel level, final BlockPos pos, final String materialName) {
        try {
            if (materialName != null) {
                try {
                    final Material mat = Material.valueOf(materialName);
                    final Block nb = getNmsBlockForMaterial(mat);
                    if (nb != null) return nb.defaultBlockState();
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }
        try {
            return level.getBlockState(pos);
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "Failed to get level block state for " + pos, t);
            return null;
        }
    }

    /* Static manager state ------------------------------------------------*/
    private static final Map<UUID, FakeBlockPacketHandler> INSTANCES = new ConcurrentHashMap<>();

    // Reflection/NMS caches to avoid expensive Class.forName/getMethod calls per-packet.
    // Initialized lazily via ensureRegistryReflectiveInit().
    private static volatile Class<?> REG_CLASS = null;
    private static volatile java.lang.reflect.Method REG_CONTAINS = null;
    private static volatile java.lang.reflect.Method REG_GET_MATERIAL = null;
    private static volatile java.lang.reflect.Method REG_GET_KEYS = null;
    private static final Object REG_LOCK = new Object();

    // Cache Material -> NMS Block reference to avoid repeated CraftMagicNumbers lookups.
    // Using ConcurrentHashMap for lock-free concurrent reads from Netty event-loop.
    private static final Map<Material, Block> NMS_BLOCK_CACHE = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface FakeBlockChecker {
        boolean isFake(Player player, BlockPos pos);
    }

    private static volatile FakeBlockChecker CHECKER = null;

    public static void setFakeChecker(final FakeBlockChecker checker) {
        CHECKER = checker;
    }

    // Lazily initialize reflective accessors for FakeBlockRegistry. Uses
    // double-checked locking to avoid race conditions and repeated lookups.
    private static void ensureRegistryReflectiveInit() {
        if (REG_CLASS != null) return;
        synchronized (REG_LOCK) {
            if (REG_CLASS != null) return;
            try {
                REG_CLASS = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
                REG_CONTAINS = REG_CLASS.getMethod("contains", String.class, int.class, int.class, int.class);
                REG_GET_MATERIAL = REG_CLASS.getMethod("getMaterial", String.class, int.class, int.class, int.class);
                // optional method to enumerate positions for chunk-level handling
                try {
                    REG_GET_KEYS = REG_CLASS.getMethod("positionsForWorld", String.class);
                } catch (final Throwable ignored) {
                    REG_GET_KEYS = null;
                }
            } catch (final Throwable t) {
                // If reflective init fails, keep fields null and caller will fallback to direct reflect each time.
                REG_CLASS = null;
                REG_CONTAINS = null;
                REG_GET_MATERIAL = null;
                LOG.log(Level.FINE, "Failed to initialize FakeBlockRegistry reflective accessors", t);
            }
        }
    }

    // Helper to check registry membership using cached Method when available.
    private static boolean registryContains(final String world, final int x, final int y, final int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_CONTAINS != null) {
                final Object res = REG_CONTAINS.invoke(null, world, x, y, z);
                return res instanceof Boolean b && b;
            }
            // fallback: try non-cached reflective call
            final Class<?> c = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
            final java.lang.reflect.Method m = c.getMethod("contains", String.class, int.class, int.class, int.class);
            final Object res = m.invoke(null, world, x, y, z);
            return res instanceof Boolean b && b;
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective contains() failed", t);
            return false;
        }
    }

    // Helper to obtain stored material name; returns null if not present or on error.
    private static String registryGetMaterial(final String world, final int x, final int y, final int z) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_MATERIAL != null) {
                final Object res = REG_GET_MATERIAL.invoke(null, world, x, y, z);
                return res instanceof String s ? s : null;
            }
            final Class<?> c = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
            final java.lang.reflect.Method m = c.getMethod("getMaterial", String.class, int.class, int.class, int.class);
            final Object res = m.invoke(null, world, x, y, z);
            return res instanceof String s ? s : null;
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective getMaterial() failed", t);
            return null;
        }
    }

    // Helper to get a snapshot of registry keys for a world; returns null on error.
    private static java.util.Set<String> registryGetKeysForWorld(final String world) {
        try {
            ensureRegistryReflectiveInit();
            if (REG_GET_KEYS != null) {
                final Object res = REG_GET_KEYS.invoke(null, world);
                if (res instanceof java.util.Set) {
                    final java.util.Set<?> raw = (java.util.Set<?>) res;
                    final java.util.Set<String> out = new java.util.HashSet<>();
                    for (final Object o : raw) {
                        if (o instanceof String s) out.add(s);
                    }
                    return out;
                }
            }
            final Class<?> c = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
            final java.lang.reflect.Method m = c.getMethod("positionsForWorld", String.class);
            final Object res = m.invoke(null, world);
            if (res instanceof java.util.Set) {
                final java.util.Set<?> raw = (java.util.Set<?>) res;
                final java.util.Set<String> out = new java.util.HashSet<>();
                for (final Object o : raw) {
                    if (o instanceof String s) out.add(s);
                }
                return out;
            }
            return null;
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "FakeBlockRegistry reflective positionsForWorld() failed", t);
            return null;
        }
    }

    // Convert Bukkit Material -> NMS Block with caching to avoid repeated lookups.
    private static Block getNmsBlockForMaterial(final Material material) {
        if (material == null) return null;
        final Block cached = NMS_BLOCK_CACHE.get(material);
        if (cached != null) return cached;
        try {
            final Block b = CraftMagicNumbers.getBlock(material);
            if (b != null) NMS_BLOCK_CACHE.put(material, b);
            return b;
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "Failed to convert Material to NMS Block: " + material, t);
            return null;
        }
    }

    // Debounce map to prevent duplicate handling of the same interaction (ray-trace)
    // Keyed by playerUUID:world:x:y:z -> timestamp millis
    private static final Map<String, Long> INTERACTION_DEBOUNCE = new ConcurrentHashMap<>();
    // Time window to consider duplicate interactions (ms)
    private static final long INTERACTION_DEBOUNCE_MS = 200L;

    private static boolean isDebouncedAndMark(final UUID playerUuid, final String world, final int x, final int y, final int z) {
        final String key = playerUuid + ":" + world + ":" + x + ":" + y + ":" + z;
        final long now = System.currentTimeMillis();
        final Long prev = INTERACTION_DEBOUNCE.get(key);
        if (prev != null && (now - prev) < INTERACTION_DEBOUNCE_MS) {
            return true;
        }
        INTERACTION_DEBOUNCE.put(key, now);
        return false;
    }

    // Debounce for use-item processing to avoid multiple ray-trace scheduling for
    // a single logical right-click. Keyed by player UUID -> timestamp.
    private static final Map<UUID, Long> USEITEM_PROCESS_DEBOUNCE = new ConcurrentHashMap<>();
    private static final long USEITEM_PROCESS_DEBOUNCE_MS = 350L;

    private static boolean isUseItemDebouncedAndMark(final UUID playerUuid) {
        final long now = System.currentTimeMillis();
        final Long prev = USEITEM_PROCESS_DEBOUNCE.get(playerUuid);
        if (prev != null && (now - prev) < USEITEM_PROCESS_DEBOUNCE_MS) return true;
        USEITEM_PROCESS_DEBOUNCE.put(playerUuid, now);
        return false;
    }

    // Helper to send fake-block refresh to a single player with debounce and a follow-up tick
    private void sendFakeRefreshToPlayer(final Player player, final ServerLevel level, final BlockPos pos) {
        sendFakeRefreshToPlayer(player, level, pos, null);
    }

    // Overload that accepts a precomputed BlockState to send (preferred when we have fake material)
    private void sendFakeRefreshToPlayer(final Player player, final ServerLevel level, final BlockPos pos, final net.minecraft.world.level.block.state.BlockState forcedState) {
        if (player == null) return;
        try {
            final UUID pu = player.getUniqueId();
            final String worldName = player.getWorld().getName();
            if (isDebouncedAndMark(pu, worldName, pos.getX(), pos.getY(), pos.getZ())) return;

            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            final net.minecraft.world.level.block.state.BlockState state = forcedState == null ? level.getBlockState(pos) : forcedState;
            final ClientboundBlockUpdatePacket refresh = new ClientboundBlockUpdatePacket(pos, state);
            handle.connection.send(refresh);
            try {
                final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
                if (plugin != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            final ServerPlayer h2 = ((CraftPlayer) player).getHandle();
                            final ClientboundBlockUpdatePacket r2 = new ClientboundBlockUpdatePacket(pos, state);
                            h2.connection.send(r2);
                        } catch (final Throwable ignored) {
                        }
                    }, 1L);
                }
            } catch (final Throwable ignored) {
            }
        } catch (final Throwable t) {
            LOG.log(Level.FINE, "Error sending fake refresh", t);
        }
    }

    /**
     * Injects a handler for the provided player. Safe to call multiple times.
     */
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

    /**
     * Uninjects and removes the handler for the provided player. Safe to call multiple times.
     */
    public static void uninject(final Player player) {
        if (player == null) return;
        final UUID id = player.getUniqueId();
        final FakeBlockPacketHandler h = INSTANCES.remove(id);
        if (h != null) {
            h.uninject();
        }
    }
    /* End static manager state --------------------------------------------*/

    /**
     * Injects this handler into the player's Netty pipeline. Safe to call multiple times.
     */
    public void inject() {
        final Player player = this.playerRef.get();
        if (player == null) return;
        try {
            final ServerPlayer handle = ((CraftPlayer) player).getHandle();
            final Channel channel = resolveChannel(handle);
            if (channel == null) return;
            final Channel pipelineChannel = channel;
            final UUID uid = player.getUniqueId();
            // Execute pipeline modification on the channel's event loop thread to be thread-safe.
            pipelineChannel.eventLoop().execute(() -> {
                try {
                    if (pipelineChannel.pipeline().get(pipelineName) == null) {
                        pipelineChannel.pipeline().addLast(pipelineName, this);
                        try {
                            final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MMOBlock");
                            if (pl != null) pl.getLogger().fine("Injected FakeBlockPacketHandler for " + player.getName());
                        } catch (final Throwable ignored) {
                            LOG.log(Level.FINE, "Exception while logging injection for " + player.getName(), ignored);
                        }

                        // Attach a closeFuture listener to ensure we remove the handler from
                        // the static registry when the player's channel is closed. This
                        // prevents memory leaks when players disconnect unexpectedly.
                        try {
                            pipelineChannel.closeFuture().addListener(f -> {
                                try {
                                    // remove only if mapping still refers to this instance
                                    INSTANCES.remove(uid, this);
                                    if (pipelineChannel.pipeline().get(pipelineName) != null) {
                                        try {
                                            pipelineChannel.pipeline().remove(pipelineName);
                                        } catch (final Throwable ignoredInner) {
                                        }
                                    }
                                } catch (final Throwable ignoredInner) {
                                }
                            });
                        } catch (final Throwable ignored) {
                            // WARN: If we cannot attach a listener the handler may remain in INSTANCES
                            // until server/plugin explicitly uninjects. Keep mapping removal in
                            // other code paths as a fallback.
                        }
                    }
                } catch (final Throwable t) {
                    LOG.log(Level.WARNING, "Error while injecting pipeline handler for " + player.getName(), t);
                }
            });
        } catch (final Throwable ignored) {
            LOG.log(Level.WARNING, "Failed to inject FakeBlockPacketHandler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    /**
     * Removes this handler from the player's Netty pipeline. Safe to call multiple times.
     */
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
                            final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MMOBlock");
                            if (pl != null) pl.getLogger().fine("Uninjected FakeBlockPacketHandler for " + player.getName());
                        } catch (final Throwable ignored) {
                            LOG.log(Level.FINE, "Exception while logging uninjection for " + player.getName(), ignored);
                        }
                    } catch (final Throwable ignored) {
                        LOG.log(Level.WARNING, "Error while removing pipeline handler for " + player.getName(), ignored);
                    }
                }
            });
        } catch (final Throwable ignored) {
            LOG.log(Level.WARNING, "Failed to uninject FakeBlockPacketHandler for player " + (player != null ? player.getName() : "<null>"), ignored);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        try {
            if (msg instanceof ServerboundPlayerActionPacket
                || msg instanceof ServerboundUseItemOnPacket
                || msg instanceof ServerboundUseItemPacket) {
                final Player player = this.playerRef.get();
                if (player != null) {
                    final BlockPos pos = extractBlockPos(msg);
                    if (pos != null) {
                        try {
                            boolean shouldRefresh = false;
                            if (CHECKER != null) {
                                try {
                                    shouldRefresh = CHECKER.isFake(player, pos);
                                } catch (final Throwable ignored) {
                                }
                            }
                            if (!shouldRefresh) {
                                try {
                                    final String worldName = player.getWorld().getName();
                                    final int x = pos.getX();
                                    final int y = pos.getY();
                                    final int z = pos.getZ();
                                    if (registryContains(worldName, x, y, z)) shouldRefresh = true;
                                } catch (final Throwable ignored) {
                                }
                            }

                            if (shouldRefresh) {
                                final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
                                try {
                                    isDebouncedAndMark(player.getUniqueId(), player.getWorld().getName(), pos.getX(), pos.getY(), pos.getZ());
                                } catch (final Throwable ignored) {
                                }
                                sendFakeRefreshToPlayer(player, level, pos);
                            } else {
                                try {
                                    if (msg instanceof net.minecraft.network.protocol.game.ServerboundUseItemPacket) {
                                        final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
                                        if (plugin != null) {
                                            if (!isUseItemDebouncedAndMark(player.getUniqueId())) {
                                                Bukkit.getScheduler().runTask(plugin, () -> {
                                                try {
                                                    final Player p = this.playerRef.get();
                                                    if (p == null) return;
                                                    final double max = 8.0D;
                                                    final RayTraceResult trace = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), max, FluidCollisionMode.NEVER, true);
                                                    if (trace != null && trace.getHitBlock() != null) {
                                                        final org.bukkit.block.Block b = trace.getHitBlock();
                                                        final int bx = b.getX();
                                                        final int by = b.getY();
                                                        final int bz = b.getZ();
                                                        final boolean regContains = registryContains(p.getWorld().getName(), bx, by, bz);
                                                        if (regContains) {
                                                            try {
                                                                final ServerLevel lvl = ((CraftWorld) p.getWorld()).getHandle();
                                                                final BlockPos bp = new BlockPos(bx, by, bz);
                                                                sendFakeRefreshToPlayer(p, lvl, bp);
                                                            } catch (final Throwable ignored) {
                                                            }
                                                        }
                                                    }
                                                } catch (final Throwable ignored) {
                                                }
                                                });
                                            }
                                        }
                                    }
                                } catch (final Throwable ignored) {
                                }
                            }
                        } catch (final Throwable t) {
                            LOG.log(Level.WARNING, "Error while handling intercepted packet for " + player.getName(), t);
                        }
                    }
                }
            }
        } catch (final Throwable t) {
            LOG.log(Level.WARNING, "Unexpected exception in channelRead", t);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        try {
            final Player player = this.playerRef.get();
            if (player == null) {
                super.write(ctx, msg, promise);
                return;
            }

            if (msg instanceof ClientboundBlockUpdatePacket) {
                final BlockPos pos = extractBlockPos(msg);
                if (pos != null) {
                            try {
                                final String worldName = player.getWorld().getName();
                                final int x = pos.getX();
                                final int y = pos.getY();
                                final int z = pos.getZ();
                                final boolean regContains = registryContains(worldName, x, y, z);
                                if (regContains) {
                                    final String materialName = registryGetMaterial(worldName, x, y, z);
                            if (materialName != null) {
                                try {
                                    final Material mat = Material.valueOf(materialName);
                                    final Block nmsBlock = getNmsBlockForMaterial(mat);
                                    if (nmsBlock != null) {
                                        final net.minecraft.world.level.block.state.BlockState fakeState = nmsBlock.defaultBlockState();
                                        final ClientboundBlockUpdatePacket replaced = new ClientboundBlockUpdatePacket(pos, fakeState);
                                        super.write(ctx, replaced, promise);
                                        return;
                                    }
                                } catch (final IllegalArgumentException ignored) {
                                }
                            }
                            return;
                        }
                    } catch (final Throwable ignored) {
                    }
                }
            }
            try {
                final String cls = msg.getClass().getSimpleName();
                if (cls.contains("SectionBlocksUpdate") || cls.contains("MultiBlockChange") || cls.contains("Chunk") || cls.contains("LevelChunk")) {
                    final Player p = this.playerRef.get();
                    if (p != null) {
                        final String worldName = p.getWorld().getName();
                        final int[] chunk = tryExtractChunkCoords(msg);
                        if (chunk != null) {
                            scheduleResendForChunk(p, worldName, chunk[0], chunk[1]);
                        } else {
                            // no chunk coords, fall back to resending for all registered
                            // positions in the world (snapshot) — should be rare but safe.
                            scheduleResendForWorld(p, worldName);
                        }
                    }
                }
            } catch (final Throwable ignored) {
            }
        } catch (final Throwable t) {
            LOG.log(Level.WARNING, "Unexpected exception in write()", t);
        }

        super.write(ctx, msg, promise);
    }

    // Attempt to resolve netty Channel from ServerPlayer via reflection. This method tolerates
    // small obfuscation or mapping differences by searching for a Channel instance in fields.
    private Channel resolveChannel(final ServerPlayer handle) {
        try {
            // Common fast-path: listener has a 'connection' field referencing the network manager
            final Object packetListener = handle.connection; // ServerGamePacketListenerImpl
            if (packetListener == null) return null;

            // Inspect fields of the listener for a Channel or for an object that holds one.
            for (final Field f : packetListener.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    final Object val = f.get(packetListener);
                    if (val == null) continue;
                    if (val instanceof Channel ch) return ch;
                    // dive one level deep
                    for (final Field g : val.getClass().getDeclaredFields()) {
                        try {
                            g.setAccessible(true);
                            final Object inner = g.get(val);
                            if (inner instanceof Channel ch2) return ch2;
                        } catch (final Throwable ignored) {
                        }
                    }
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    // Very small heuristic extractor: try to find a BlockPos-typed field in the packet instance.
    private BlockPos extractBlockPos(final Object packet) {
        if (packet == null) return null;
        try {
            // Try common accessor methods first
            try {
                final java.lang.reflect.Method m = packet.getClass().getDeclaredMethod("getPos");
                m.setAccessible(true);
                final Object res = m.invoke(packet);
                if (res instanceof BlockPos bp) return bp;
            } catch (final NoSuchMethodException ignored) {
            }

            for (final Field f : packet.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    final Object val = f.get(packet);
                    if (val instanceof BlockPos) return (BlockPos) val;
                    // Some packets store ints x,y,z instead of BlockPos
                    if (val instanceof Integer) {
                        // try to find neighbouring int fields named x,y,z
                        final Integer x = findIntField(packet.getClass(), packet, "x", "posX", "blockX");
                        final Integer y = findIntField(packet.getClass(), packet, "y", "posY", "blockY");
                        final Integer z = findIntField(packet.getClass(), packet, "z", "posZ", "blockZ");
                        if (x != null && y != null && z != null) return new BlockPos(x, y, z);
                    }
                } catch (final Throwable ignored) {
                }
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    private Integer findIntField(final Class<?> c, final Object instance, final String... names) {
        for (final String name : names) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                final Object o = f.get(instance);
                if (o instanceof Integer) return (Integer) o;
            } catch (final Throwable ignored) {
            }
        }
        // check all int fields as last resort
        for (final Field f : c.getDeclaredFields()) {
            try {
                if (f.getType() == int.class || f.getType() == Integer.class) {
                    f.setAccessible(true);
                    final Object o = f.get(instance);
                    if (o instanceof Integer) return (Integer) o;
                }
            } catch (final Throwable ignored) {
            }
        }
        return null;
    }
}
