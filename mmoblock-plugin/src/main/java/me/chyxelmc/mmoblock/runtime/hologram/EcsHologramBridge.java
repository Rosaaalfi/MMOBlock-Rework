package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.ecs.component.PacketHologramComponent;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class EcsHologramBridge {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Map<UUID, UUID> hologramEntities = new ConcurrentHashMap<>();
    private EntityManager entityManager;

    EcsHologramBridge(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
    }

    void setEntityManager(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    boolean available() {
        return this.entityManager != null;
    }

    boolean upsertStatic(
            final UUID hologramUniqueId,
            final String worldName,
            final Location location,
            final List<NmsAdapter.HologramLine> packetLines
    ) {
        if (this.entityManager == null) {
            return false;
        }

        final UUID ecsId = this.hologramEntities.computeIfAbsent(hologramUniqueId, ignored -> UUID.randomUUID());
        this.entityManager.submit(entityManager -> {
            if (!entityManager.hasEntity(ecsId)) {
                entityManager.addEntity(ecsId);
                entityManager.addComponent(ecsId, new PacketHologramComponent(hologramUniqueId, location, packetLines));
                sendToWorldPlayers(worldName, hologramUniqueId, location, packetLines);
                return;
            }

            final PacketHologramComponent component = entityManager.getComponent(ecsId, PacketHologramComponent.class);
            if (component != null && !component.removed()) {
                component.update(location, packetLines);
                sendToWorldPlayers(worldName, hologramUniqueId, location, packetLines);
                return;
            }

            entityManager.removeEntity(ecsId);
            entityManager.addEntity(ecsId);
            entityManager.addComponent(ecsId, new PacketHologramComponent(hologramUniqueId, location, packetLines));
            sendToWorldPlayers(worldName, hologramUniqueId, location, packetLines);
        });
        return true;
    }

    void remove(final UUID hologramUniqueId) {
        final UUID ecsId = this.hologramEntities.remove(hologramUniqueId);
        if (ecsId == null || this.entityManager == null) {
            return;
        }
        this.entityManager.submit(entityManager -> {
            final PacketHologramComponent component = entityManager.getComponent(ecsId, PacketHologramComponent.class);
            if (component != null) {
                component.markRemoved();
                return;
            }
            entityManager.removeEntity(ecsId);
        });
    }

    void clearAll() {
        if (this.entityManager == null) {
            return;
        }
        final List<UUID> entityIds = List.copyOf(this.hologramEntities.values());
        this.hologramEntities.clear();
        this.entityManager.submit(entityManager -> {
            for (final UUID entityId : entityIds) {
                final PacketHologramComponent component = entityManager.getComponent(entityId, PacketHologramComponent.class);
                if (component != null) {
                    removePacketHologramForViewers(component);
                    component.markRemoved();
                } else {
                    entityManager.removeEntity(entityId);
                }
            }
        });
    }

    void shutdown() {
        if (this.entityManager == null) {
            return;
        }
        final List<UUID> entityIds = List.copyOf(this.hologramEntities.values());
        this.hologramEntities.clear();
        this.entityManager.submit(entityManager -> {
            for (final UUID entityId : entityIds) {
                final PacketHologramComponent component = entityManager.getComponent(entityId, PacketHologramComponent.class);
                if (component != null) {
                    removePacketHologramForViewers(component);
                    component.markRemoved();
                }
                entityManager.removeEntity(entityId);
            }
        });
    }

    void syncForPlayer(final Player player, final Collection<UUID> hologramUniqueIds) {
        if (this.entityManager == null) {
            return;
        }
        final List<UUID> entityIds = new java.util.ArrayList<>();
        for (final UUID hologramUniqueId : hologramUniqueIds) {
            final UUID entityId = this.hologramEntities.get(hologramUniqueId);
            if (entityId != null) {
                entityIds.add(entityId);
            }
        }
        final List<UUID> snapshot = List.copyOf(entityIds);
        this.entityManager.submit(entityManager -> snapshot.forEach(entityId -> {
            final PacketHologramComponent component = entityManager.getComponent(entityId, PacketHologramComponent.class);
            if (component == null || component.lines() == null || component.lines().isEmpty()) {
                return;
            }
            try {
                this.nmsAdapter.upsertPacketHologram(player, component.hologramUniqueId(), component.baseLocation(), component.lines());
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }
        }));
    }

    private void sendToWorldPlayers(
            final String worldName,
            final UUID hologramUniqueId,
            final Location location,
            final List<NmsAdapter.HologramLine> packetLines
    ) {
        final World world = this.plugin.getServer().getWorld(worldName);
        if (world == null) {
            return;
        }
        for (final Player player : world.getPlayers()) {
            try {
                this.nmsAdapter.upsertPacketHologram(player, hologramUniqueId, location, packetLines);
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }      }
    }

    private void removePacketHologramForViewers(
            final PacketHologramComponent component
    ) {
        if (component == null || component.baseLocation() == null || component.baseLocation().getWorld() == null) {
            return;
        }
        for (final Player player : component.baseLocation().getWorld().getPlayers()) {
            try {
                this.nmsAdapter.removePacketHologram(player, component.hologramUniqueId());
            } catch (final Exception e) {
                MMOBlockLogger.debug("Reflection fallback: " + e.getMessage());
            }      }
    }
}
