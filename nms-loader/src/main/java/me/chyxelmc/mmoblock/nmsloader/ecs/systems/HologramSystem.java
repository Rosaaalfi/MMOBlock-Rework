package me.chyxelmc.mmoblock.nmsloader.ecs.systems;

import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.ecs.EntityManager;
import me.chyxelmc.mmoblock.nmsloader.ecs.SystemBase;
import me.chyxelmc.mmoblock.nmsloader.ecs.components.HologramComponent;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * System responsible for upserting packet holograms for players and removing
 * them when the component is marked removed.
 */
public final class HologramSystem extends SystemBase {

    private final NmsAdapter adapter;

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
                    } catch (final RuntimeException ex) {
                        // swallow
                    }
                }
                // remove component by removing entity
                entityManager.removeEntity(id);
                continue;
            }

            // otherwise upsert for all players in the world
            for (final Player player : world.getPlayers()) {
                try {
                    adapter.upsertPacketHologram(player, holo.hologramUniqueId(), holo.baseLocation(), holo.lines());
                } catch (final RuntimeException ex) {
                    // swallow and continue
                }
            }
        }
    }
}

