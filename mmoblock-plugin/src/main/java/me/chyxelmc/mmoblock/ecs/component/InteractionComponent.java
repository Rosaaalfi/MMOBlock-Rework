package me.chyxelmc.mmoblock.ecs.component;

import me.chyxelmc.mmoblock.ecs.Component;
import org.bukkit.NamespacedKey;

import java.util.UUID;

public final class InteractionComponent implements Component {

    private final float width;
    private final float height;
    private final NamespacedKey uniqueIdKey;
    private final UUID blockUniqueId;
    private UUID spawnedInteraction;

    public InteractionComponent(final float width, final float height, final NamespacedKey uniqueIdKey, final UUID blockUniqueId) {
        this.width = width;
        this.height = height;
        this.uniqueIdKey = uniqueIdKey;
        this.blockUniqueId = blockUniqueId;
    }

    public float width() {
        return this.width;
    }

    public float height() {
        return this.height;
    }

    public NamespacedKey uniqueIdKey() {
        return this.uniqueIdKey;
    }

    public UUID blockUniqueId() {
        return this.blockUniqueId;
    }

    public UUID spawnedInteraction() {
        return this.spawnedInteraction;
    }

    public void setSpawnedInteraction(final UUID spawnedInteraction) {
        this.spawnedInteraction = spawnedInteraction;
    }
}
