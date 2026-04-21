package me.chyxelmc.mmoblock.nms.mojang.v1_19_4;

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
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.RayTraceResult;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static manager usage:
 * - Register a checker via {@link #setFakeChecker(FakeBlockChecker)} which returns true when a given
 *   (player, blockPos) refers to a fake-block visual that should be re-sent.
 * - Call {@link #inject(org.bukkit.entity.Player)} when you want per-player interception enabled.
 * - Call {@link #uninject(org.bukkit.entity.Player)} to remove the handler and avoid memory leaks.
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

    public FakeBlockPacketHandler(final Player player) {
        this.playerRef = new WeakReference<>(player);
        this.pipelineName = "mmoblock-fakeblock-" + player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    }

    /* Static manager state ------------------------------------------------*/
    private static final Map<UUID, FakeBlockPacketHandler> INSTANCES = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface FakeBlockChecker {
        boolean isFake(org.bukkit.entity.Player player, BlockPos pos);
    }

    private static volatile FakeBlockChecker CHECKER = null;

    public static void setFakeChecker(final FakeBlockChecker checker) {
        CHECKER = checker;
    }

    /**
     * Injects a handler for the provided player. Safe to call multiple times.
     */
    public static void inject(final org.bukkit.entity.Player player) {
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
    public static void uninject(final org.bukkit.entity.Player player) {
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
            pipelineChannel.eventLoop().execute(() -> {
                if (pipelineChannel.pipeline().get(pipelineName) == null) {
                    pipelineChannel.pipeline().addLast(pipelineName, this);
                    try {
                        final org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOBlock");
                        if (pl != null) pl.getLogger().fine("Injected FakeBlockPacketHandler for " + player.getName());
                    } catch (final Throwable ignored) {
                    }
                }
            });
        } catch (final Throwable ignored) {
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
                            final org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOBlock");
                            if (pl != null) pl.getLogger().fine("Uninjected FakeBlockPacketHandler for " + player.getName());
                        } catch (final Throwable ignored) {
                        }
                    } catch (final Throwable ignored) {
                    }
                }
            });
        } catch (final Throwable ignored) {
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
                                    final Class<?> reg = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
                                    final java.lang.reflect.Method contains = reg.getMethod("contains", String.class, int.class, int.class, int.class);
                                    final String worldName = player.getWorld().getName();
                                    final int x = pos.getX();
                                    final int y = pos.getY();
                                    final int z = pos.getZ();
                                    final Object res = contains.invoke(null, worldName, x, y, z);
                                    if (res instanceof Boolean b && b) shouldRefresh = true;
                                } catch (final Throwable ignored) {
                                }
                            }

                            if (shouldRefresh) {
                                final ServerPlayer handle = ((CraftPlayer) player).getHandle();
                                final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
                                final ClientboundBlockUpdatePacket refresh = new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos));
                                handle.connection.send(refresh);
                                try {
                                    final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MMOBlock");
                                    if (pl != null) pl.getLogger().fine("Sent fake-block refresh to " + player.getName() + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                                } catch (final Throwable ignored) {
                                }
                                // schedule a second refresh one tick later to be robust against client prediction
                                try {
                                    final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
                                    if (plugin != null) {
                                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                            try {
                                                final ServerPlayer h2 = ((CraftPlayer) player).getHandle();
                                                final ClientboundBlockUpdatePacket r2 = new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos));
                                                h2.connection.send(r2);
                                            } catch (final Throwable ignored) {
                                            }
                                        }, 1L);
                                    }
                                } catch (final Throwable ignored) {
                                }
                            } else {
                                // No direct BlockPos; if this was a UseItem packet, perform a ray-trace on main thread
                                try {
                                    if (msg instanceof net.minecraft.network.protocol.game.ServerboundUseItemPacket) {
                                        final org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOBlock");
                                        if (plugin != null) {
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
                                                                try {
                                                                    final org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MMOBlock");
                                                                    if (pl != null) pl.getLogger().fine("Ray-trace for " + p.getName() + " hit " + bx + "," + by + "," + bz);
                                                                } catch (final Throwable ignored) {
                                                                }
                                                                boolean regContains = false;
                                                        try {
                                                            final Class<?> reg = Class.forName("me.chyxelmc.mmoblock.runtime.FakeBlockRegistry");
                                                            final java.lang.reflect.Method contains = reg.getMethod("contains", String.class, int.class, int.class, int.class);
                                                            final Object res = contains.invoke(null, p.getWorld().getName(), bx, by, bz);
                                                            if (res instanceof Boolean bres && bres) regContains = true;
                                                        } catch (final Throwable ignored) {
                                                        }
                                                        if (regContains) {
                                                            try {
                                                                final ServerPlayer h = ((CraftPlayer) p).getHandle();
                                                                final ServerLevel lvl = ((CraftWorld) p.getWorld()).getHandle();
                                                                final BlockPos bp = new BlockPos(bx, by, bz);
                                                                final ClientboundBlockUpdatePacket r = new ClientboundBlockUpdatePacket(bp, lvl.getBlockState(bp));
                                                                h.connection.send(r);
                                                                // second tick send
                                                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                                                    try {
                                                                        final ServerPlayer h2 = ((CraftPlayer) p).getHandle();
                                                                        final ClientboundBlockUpdatePacket r2 = new ClientboundBlockUpdatePacket(bp, lvl.getBlockState(bp));
                                                                        h2.connection.send(r2);
                                                                    } catch (final Throwable ignored) {
                                                                    }
                                                                }, 1L);
                                                            } catch (final Throwable ignored) {
                                                            }
                                                        }
                                                    }
                                                } catch (final Throwable ignored) {
                                                }
                                            });
                                        }
                                    }
                                } catch (final Throwable ignored) {
                                }
                            }
                        } catch (final Throwable ignored) {
                        }
                    }
                }
            }
        } catch (final Throwable ignored) {
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
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

