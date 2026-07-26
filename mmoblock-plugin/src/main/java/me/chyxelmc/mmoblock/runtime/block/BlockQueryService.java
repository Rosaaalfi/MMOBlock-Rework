package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BlockQueryService {

    private final BlockStateRegistry stateRegistry;
    private final DataCache dataCache;
    private final BlockConfigLoader blockConfigService;
    private final NamespacedKey uniqueIdKey;
    private final BlockLookProtection lookProtection;

    public BlockQueryService(
            final BlockStateRegistry stateRegistry,
            final DataCache dataCache,
            final BlockConfigLoader blockConfigService,
            final NamespacedKey uniqueIdKey,
            final BlockLookProtection lookProtection
    ) {
        this.stateRegistry = stateRegistry;
        this.dataCache = dataCache;
        this.blockConfigService = blockConfigService;
        this.uniqueIdKey = uniqueIdKey;
        this.lookProtection = lookProtection;
    }

    public PlacedBlockModel findPlacedBlock(final UUID uniqueId) {
        PlacedBlockModel block = this.stateRegistry.getBlock(uniqueId);
        if (block != null) {
            return block;
        }
        return this.dataCache.getBlock(uniqueId);
    }

    public UUID resolveBlockUniqueId(final Entity entity) {
        if (entity == null) {
            return null;
        }
        final String uniqueIdRaw = entity.getPersistentDataContainer().get(this.uniqueIdKey, PersistentDataType.STRING);
        if (uniqueIdRaw == null) {
            return null;
        }
        try {
            return UUID.fromString(uniqueIdRaw);
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    public List<String> blockIds() {
        return new ArrayList<>(this.blockConfigService.blockIds());
    }

    public List<PlacedBlockModel> placedBlocks() {
        return Collections.unmodifiableList(new ArrayList<>(this.stateRegistry.blocks()));
    }

    public BlockStateRegistry stateRegistry() {
        return this.stateRegistry;
    }

    public boolean isPlayerLookProtected(final Player player) {
        return this.lookProtection.isProtected(player);
    }
}
