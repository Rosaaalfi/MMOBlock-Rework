package me.chyxelmc.mmoblock.nms.v1_21_11;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.RayTraceResult;

/**
 * FakeBlockPacketHandler for 1.21.11.
 */
public final class FakeBlockPacketHandler extends ChannelDuplexHandler {

    private final WeakReference<Player> playerRef;
    private final String pipelineName;

    public FakeBlockPacketHandler(final Player player) {
        this.playerRef = new WeakReference<>(player);
        this.pipelineName = "mmoblock-fakeblock-" + player.getUniqueId().toString().toLowerCase(Locale.ROOT);
    }

    private static final Map<UUID, FakeBlockPacketHandler> INSTANCES = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface FakeBlockChecker {
        boolean isFake(Player player, BlockPos pos);
    }

    private static volatile FakeBlockChecker CHECKER = null;

    public static void setFakeChecker(final FakeBlockChecker checker) {
        CHECKER = checker;
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
        if (h != null) {
            h.uninject();
        }
    }

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
                                    final org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOBlock");
                                    if (pl != null) pl.getLogger().fine("Sent fake-block refresh to " + player.getName() + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                                } catch (final Throwable ignored) {
                                }
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
                                                            final org.bukkit.plugin.Plugin pl = org.bukkit.Bukkit.getPluginManager().getPlugin("MMOBlock");
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

    private Channel resolveChannel(final ServerPlayer handle) {
        try {
            final Object packetListener = handle.connection; // ServerGamePacketListenerImpl
            if (packetListener == null) return null;

            for (final Field f : packetListener.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    final Object val = f.get(packetListener);
                    if (val == null) continue;
                    if (val instanceof Channel ch) return ch;
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

    private BlockPos extractBlockPos(final Object packet) {
        if (packet == null) return null;
        try {
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
                    if (val instanceof Integer) {
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

