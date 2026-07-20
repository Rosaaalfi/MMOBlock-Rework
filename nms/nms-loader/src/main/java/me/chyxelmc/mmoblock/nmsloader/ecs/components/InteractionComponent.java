package me.chyxelmc.mmoblock.nmsloader.ecs.components;

import me.chyxelmc.mmoblock.nmsloader.ecs.Component;
import org.bukkit.NamespacedKey;

import java.util.UUID;

/**
 * Component that describes an interaction hitbox to be spawned via NMS.
 */
public final class InteractionComponent implements Component {

    private final float width;
    private final float height;
    private final NamespacedKey uniqueIdKey;
    private final UUID blockUniqueId;

    // Optional: store spawned interaction entity UUID so systems can remove it later.
    private UUID spawnedInteraction;

    public InteractionComponent(final float width, final float height, final NamespacedKey uniqueIdKey, final UUID blockUniqueId) {
        this.width = width;
        this.height = height;
        this.uniqueIdKey = uniqueIdKey;
        this.blockUniqueId = blockUniqueId;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public NamespacedKey uniqueIdKey() {
        return uniqueIdKey;
    }

    public UUID blockUniqueId() {
        return blockUniqueId;
    }

    public UUID spawnedInteraction() {
        return spawnedInteraction;
    }

    public void setSpawnedInteraction(final UUID spawnedInteraction) {
        this.spawnedInteraction = spawnedInteraction;
    }
}

