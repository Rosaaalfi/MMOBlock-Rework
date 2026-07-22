package me.chyxelmc.mmoblock.placeholder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HologramPlaceholderContextStore {

    private final Map<UUID, ContextValues> contexts = new ConcurrentHashMap<>();

    public void set(final UUID playerUniqueId, final ContextValues values) {
        this.contexts.put(playerUniqueId, values);
    }

    public ContextValues get(final UUID playerUniqueId) {
        return this.contexts.get(playerUniqueId);
    }

    public void clear(final UUID playerUniqueId) {
        this.contexts.remove(playerUniqueId);
    }

    public void clear() {
        this.contexts.clear();
    }

    public record ContextValues(int progress, int maxProgress, long respawnTimeSeconds) {
    }
}
