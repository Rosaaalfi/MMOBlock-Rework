package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class HologramScanScheduler {

    private final MMOBlock plugin;
    private final Map<UUID, HologramPacketSession> sessions;
    private final HologramVisibilityPolicy visibilityPolicy;
    private final EnqueueSync enqueueSync;
    private final AtomicLong animationTick = new AtomicLong();
    private final AtomicLong proximityTick = new AtomicLong();

    HologramScanScheduler(
            final MMOBlock plugin,
            final Map<UUID, HologramPacketSession> sessions,
            final HologramVisibilityPolicy visibilityPolicy,
            final EnqueueSync enqueueSync
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.visibilityPolicy = visibilityPolicy;
        this.enqueueSync = enqueueSync;
    }

    void enqueueBeforeFlush() {
        enqueueAnimatedUpdates();
        proximityRescanIfDue();
    }

    private void enqueueAnimatedUpdates() {
        final int interval = Math.max(1, this.plugin.getConfig().getInt("hologram.packet.animationUpdateIntervalTicks", 1));
        final long tick = this.animationTick.incrementAndGet();
        if (tick % interval != 0L) {
            return;
        }

        for (final Map.Entry<UUID, HologramPacketSession> entry : this.sessions.entrySet()) {
            final HologramPacketSession session = entry.getValue();
            if ((!session.animated() && !session.dynamicPlaceholders()) || session.viewers().isEmpty()) {
                continue;
            }
            for (final UUID viewerId : session.viewers()) {
                this.enqueueSync.enqueue(viewerId, entry.getKey(), HologramSyncAction.UPSERT);
            }
        }
    }

    private void proximityRescanIfDue() {
        final int interval = Math.max(1, this.plugin.getConfig().getInt("hologram.packet.proximityScanIntervalTicks", 20));
        final long tick = this.proximityTick.incrementAndGet();
        if (tick % interval != 0L) {
            return;
        }

        for (final Map.Entry<UUID, HologramPacketSession> entry : this.sessions.entrySet()) {
            final UUID hologramId = entry.getKey();
            final HologramPacketSession session = entry.getValue();

            final World world = this.plugin.getServer().getWorld(session.worldName());
            if (world == null) {
                continue;
            }

            for (final UUID viewerId : new HashSet<>(session.viewers())) {
                final Player player = this.plugin.getServer().getPlayer(viewerId);
                try {
                    if (player == null || !this.visibilityPolicy.isViewerInRange(player, world, session)) {
                        this.enqueueSync.enqueue(viewerId, hologramId, HologramSyncAction.REMOVE);
                    }
                } catch (final Exception ignored) {
                    this.enqueueSync.enqueue(viewerId, hologramId, HologramSyncAction.REMOVE);
                }
            }

            for (final Player player : world.getPlayers()) {
                try {
                    if (!session.viewers().contains(player.getUniqueId())
                            && this.visibilityPolicy.isViewerInRange(player, world, session)) {
                        this.enqueueSync.enqueue(player.getUniqueId(), hologramId, HologramSyncAction.UPSERT);
                    }
                } catch (final Exception ignored) {
                    // expected - reflection fallback
                }
            }
        }
    }

    @FunctionalInterface
    interface EnqueueSync {
        void enqueue(UUID playerUniqueId, UUID hologramUniqueId, HologramSyncAction action);
    }
}
