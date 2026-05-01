package me.chyxelmc.mmoblock.nmsloader.ecs.systems;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.SystemBase;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System responsible for upserting packet holograms for players and removing
 * them when the component is marked removed.
 */
public final class HologramSystem extends SystemBase {

    private final NmsAdapter adapter;
    private final Map<SyncKey, Long> sentRevisions = new ConcurrentHashMap<>();

    public HologramSystem(final NmsAdapter adapter) {
        super("HologramSystem");
        this.adapter = adapter;
    }

    @Override
    public void tick(final EntityManager entityManager, final long tick) {
        final List<UUID> candidates = entityManager.getEntitiesWith(HologramComponent.class);
        for (final UUID id : candidates) {
            final HologramComponent holo = entityManager.getComponent(id, HologramComponent.class);
            if (holo == null) continue;

            final World world = holo.baseLocation().getWorld();
            if (world == null) continue;

            // If component is marked removed, instruct adapter to remove holograms
            if (holo.removed()) {
                for (final Player player : world.getPlayers()) {
                    try {
                        adapter.removePacketHologram(player, holo.hologramUniqueId());
                        sentRevisions.remove(new SyncKey(player.getUniqueId(), holo.hologramUniqueId()));
                    } catch (final RuntimeException ex) {
                        // swallow
                    }
                }
                sentRevisions.keySet().removeIf(key -> key.hologramUniqueId().equals(holo.hologramUniqueId()));
                // remove component by removing entity
                entityManager.removeEntity(id);
                continue;
            }

            // Otherwise upsert only when this player has not seen the hologram yet
            // or when the component content/location changed.
            for (final Player player : world.getPlayers()) {
                final SyncKey key = new SyncKey(player.getUniqueId(), holo.hologramUniqueId());
                if (sentRevisions.getOrDefault(key, Long.MIN_VALUE) == holo.revision()) {
                    continue;
                }
                try {
                    adapter.upsertPacketHologram(player, holo.hologramUniqueId(), holo.baseLocation(), holo.lines());
                    sentRevisions.put(key, holo.revision());
                } catch (final RuntimeException ex) {
                    // swallow and continue
                }
            }
        }
    }

    private record SyncKey(UUID playerUniqueId, UUID hologramUniqueId) {
    }
}
