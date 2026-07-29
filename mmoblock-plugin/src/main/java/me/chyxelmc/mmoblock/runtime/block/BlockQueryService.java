package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.persistence.cache.DataCache;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BlockQueryService {

    private final BlockStateRegistry stateRegistry;
    private final DataCache dataCache;
    private final BlockConfigLoader blockConfigService;
    private final BlockLookProtection lookProtection;

    public BlockQueryService(
            final BlockStateRegistry stateRegistry,
            final DataCache dataCache,
            final BlockConfigLoader blockConfigService,
            final BlockLookProtection lookProtection
    ) {
        this.stateRegistry = stateRegistry;
        this.dataCache = dataCache;
        this.blockConfigService = blockConfigService;
        this.lookProtection = lookProtection;
    }

    public PlacedBlockModel findPlacedBlock(final UUID uniqueId) {
        PlacedBlockModel block = this.stateRegistry.getBlock(uniqueId);
        if (block != null) {
            return block;
        }
        return this.dataCache.getBlock(uniqueId);
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
