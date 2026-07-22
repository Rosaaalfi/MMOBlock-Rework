package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.ecs.SystemBase;
import me.chyxelmc.mmoblock.ecs.component.PacketHologramComponent;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketHologramSyncSystem extends SystemBase {

    private final NmsAdapter nmsAdapter;
    private final Map<SyncKey, Long> sentRevisions = new ConcurrentHashMap<>();

    public PacketHologramSyncSystem(final NmsAdapter nmsAdapter) {
        super("PacketHologramSyncSystem");
        this.nmsAdapter = nmsAdapter;
    }

    @Override
    public void tick(final EntityManager entityManager, final long tick) {
        final List<UUID> candidates = entityManager.getEntitiesWith(PacketHologramComponent.class);
        for (final UUID id : candidates) {
            final PacketHologramComponent hologram = entityManager.getComponent(id, PacketHologramComponent.class);
            if (hologram == null) {
                continue;
            }

            final World world = hologram.baseLocation().getWorld();
            if (world == null) {
                continue;
            }

            if (hologram.removed()) {
                removeForWorld(world, hologram.hologramUniqueId());
                this.sentRevisions.keySet().removeIf(key -> key.hologramUniqueId().equals(hologram.hologramUniqueId()));
                entityManager.removeEntity(id);
                continue;
            }

            for (final Player player : world.getPlayers()) {
                final SyncKey key = new SyncKey(player.getUniqueId(), hologram.hologramUniqueId());
                if (this.sentRevisions.getOrDefault(key, Long.MIN_VALUE) == hologram.revision()) {
                    continue;
                }
                try {
                    this.nmsAdapter.upsertPacketHologram(
                            player,
                            hologram.hologramUniqueId(),
                            hologram.baseLocation(),
                            hologram.lines()
                    );
                    this.sentRevisions.put(key, hologram.revision());
                } catch (final RuntimeException ignored) {
                    // expected - player may have disconnected mid-sync
                }
            }
        }
    }

    public void removePlayerEntries(final UUID playerId) {
        this.sentRevisions.keySet().removeIf(key -> key.playerUniqueId().equals(playerId));
    }

    private void removeForWorld(final World world, final UUID hologramUniqueId) {
        for (final Player player : world.getPlayers()) {
            try {
                this.nmsAdapter.removePacketHologram(player, hologramUniqueId);
            } catch (final RuntimeException ignored) {
                // expected - player may have disconnected mid-sync
            }
        }
    }

    private record SyncKey(UUID playerUniqueId, UUID hologramUniqueId) {
    }
}
