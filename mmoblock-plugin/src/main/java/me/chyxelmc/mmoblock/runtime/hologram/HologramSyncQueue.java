package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class HologramSyncQueue {

    private final Map<HologramSyncKey, HologramSyncAction> pendingSync = new ConcurrentHashMap<>();

    void enqueue(final UUID playerUniqueId, final UUID hologramUniqueId, final HologramSyncAction action) {
        final HologramSyncKey key = new HologramSyncKey(playerUniqueId, hologramUniqueId);
        this.pendingSync.compute(key, (ignored, previous) -> {
            if (action == HologramSyncAction.REMOVE) {
                return HologramSyncAction.REMOVE;
            }
            if (previous == HologramSyncAction.REMOVE) {
                return HologramSyncAction.REMOVE;
            }
            return HologramSyncAction.UPSERT;
        });
    }

    void removePlayer(final UUID playerUniqueId) {
        this.pendingSync.keySet().removeIf(key -> key.playerUniqueId().equals(playerUniqueId));
    }

    void clear() {
        this.pendingSync.clear();
    }

    void flush(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Map<UUID, HologramPacketSession> sessions,
            final int budget,
            final SyncViewer syncViewer
    ) {
        int processed = 0;
        for (final Map.Entry<HologramSyncKey, HologramSyncAction> entry : this.pendingSync.entrySet()) {
            if (processed >= budget) {
                break;
            }
            if (!this.pendingSync.remove(entry.getKey(), entry.getValue())) {
                continue;
            }

            final Player player = plugin.getServer().getPlayer(entry.getKey().playerUniqueId());
            if (player != null) {
                if (entry.getValue() == HologramSyncAction.REMOVE) {
                    removeForPlayer(nmsAdapter, sessions, entry.getKey(), player);
                } else {
                    syncViewer.sync(entry.getKey().hologramUniqueId(), player);
                }
            }
            processed++;
        }
    }

    private static void removeForPlayer(
            final NmsAdapter nmsAdapter,
            final Map<UUID, HologramPacketSession> sessions,
            final HologramSyncKey key,
            final Player player
    ) {
        final HologramPacketSession session = sessions.get(key.hologramUniqueId());
        if (session != null) {
            session.viewers().remove(key.playerUniqueId());
            session.lastSentState().remove(key.playerUniqueId());
            session.lastSentResolvedLines().remove(key.playerUniqueId());
        }
        nmsAdapter.removePacketHologram(player, key.hologramUniqueId());
    }

    @FunctionalInterface
    interface SyncViewer {
        void sync(UUID hologramUniqueId, Player player);
    }
}
