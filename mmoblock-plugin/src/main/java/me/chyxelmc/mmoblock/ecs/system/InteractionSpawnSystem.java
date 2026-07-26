package me.chyxelmc.mmoblock.ecs.system;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.ecs.EntityManager;
import me.chyxelmc.mmoblock.ecs.SystemBase;
import me.chyxelmc.mmoblock.ecs.component.InteractionComponent;
import me.chyxelmc.mmoblock.ecs.component.PositionComponent;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class InteractionSpawnSystem extends SystemBase {

    private final NmsAdapter nmsAdapter;
    private final BiConsumer<UUID, UUID> onSpawn;

    public InteractionSpawnSystem(final NmsAdapter nmsAdapter, final BiConsumer<UUID, UUID> onSpawn) {
        super("InteractionSpawnSystem");
        this.nmsAdapter = nmsAdapter;
        this.onSpawn = onSpawn;
    }

    @Override
    public void tick(final EntityManager entityManager, final long tick) {
        final List<UUID> candidates = entityManager.getEntitiesWith(PositionComponent.class, InteractionComponent.class);
        for (final UUID id : candidates) {
            final PositionComponent position = entityManager.getComponent(id, PositionComponent.class);
            final InteractionComponent interaction = entityManager.getComponent(id, InteractionComponent.class);
            if (position == null || interaction == null || interaction.spawnedInteraction() != null) {
                continue;
            }

            final Location location = position.location();
            final World world = location.getWorld();
            if (world == null) {
                continue;
            }

            final NmsAdapter.SpawnResult result = this.nmsAdapter.spawnInteraction(
                    world,
                    location,
                    interaction.width(),
                    interaction.height(),
                    interaction.uniqueIdKey(),
                    interaction.blockUniqueId()
            );
            if (!result.success() || result.interactionUniqueId() == null) {
                continue;
            }

            interaction.setSpawnedInteraction(result.interactionUniqueId());
            if (this.onSpawn != null) {
                try {
                    this.onSpawn.accept(interaction.blockUniqueId(), result.interactionUniqueId());
                } catch (final Exception ignored) {
                    // expected - callback owner may already be shutting down
                }
            }
        }
    }
}
