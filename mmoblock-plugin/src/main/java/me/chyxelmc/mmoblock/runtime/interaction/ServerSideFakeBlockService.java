package me.chyxelmc.mmoblock.runtime.interaction;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.runtime.FakeBlockRegistry;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;

public final class ServerSideFakeBlockService {

    private static final double DEMOTE_RADIUS_PADDING = 1.5D;
    private static final int DEMOTE_WORLD_RESTORE_DELAY_TICKS = 2;
    private static final int DEMOTE_FAKE_REFRESH_TICKS = 3;

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final BlockStateRegistry stateRegistry;
    private final Set<PositionKey> promotedPositions = ConcurrentHashMap.newKeySet();
    private final Set<PositionKey> demotingPositions = ConcurrentHashMap.newKeySet();
    private final java.util.Map<PositionKey, PromotedBlock> promotedBlocks = new ConcurrentHashMap<>();

    public ServerSideFakeBlockService(final MMOBlock plugin, final NmsAdapter nmsAdapter, final Scheduler scheduler, final BlockStateRegistry stateRegistry) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
        this.stateRegistry = stateRegistry;
    }

    public void syncForPlayer(final Player player, final double radiusSquared) {
        if (player == null || !player.isOnline()) {
            return;
        }

        final World world = player.getWorld();
        final Location playerLocation = player.getLocation();
        final Set<PositionKey> nearPositions = collectNearFakeBlocks(world, playerLocation, radiusSquared);
        for (final PositionKey position : nearPositions) {
            promote(world, position);
        }
        demoteStalePositions(world, radiusSquared);
    }

    public void syncWorld(final World world, final double radiusSquared) {
        if (world == null) {
            return;
        }
        demoteStalePositions(world, radiusSquared);
    }

    public void syncNearbyPlayers(final World world, final Location center, final double radiusSquared) {
        if (world == null || center == null) {
            return;
        }
        for (final Player player : world.getPlayers()) {
            syncForPlayer(player, radiusSquared);
        }
        demoteStalePositions(world, radiusSquared);
    }

    public void reconcile(final Iterable<? extends Player> players, final double radiusSquared) {
        final Set<String> visitedWorlds = new HashSet<>();
        for (final Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            visitedWorlds.add(player.getWorld().getName());
            // Must run on the player's region thread for world/block operations (Folia)
            this.scheduler.runAtLocation(player.getLocation(), () -> syncForPlayer(player, radiusSquared));
        }
        for (final PositionKey position : Set.copyOf(this.promotedPositions)) {
            if (visitedWorlds.contains(position.worldName())) {
                continue;
            }
            final PromotedBlock promoted = this.promotedBlocks.get(position);
            if (promoted != null && promoted.world() != null) {
                final World world = promoted.world();
                // Must run on the region thread for world/block operations (Folia)
                this.scheduler.runAtLocation(position.base(world), () -> demote(world, position, false));
            }
        }
    }

    public void demoteAll() {
        for (final PositionKey position : Set.copyOf(this.promotedPositions)) {
            final PromotedBlock promoted = this.promotedBlocks.get(position);
            if (promoted == null) {
                this.promotedPositions.remove(position);
                continue;
            }
            final World world = promoted.world();
            if (world != null) {
                demote(world, position, false);
            }
        }
    }

    public void demoteChunk(final World world, final int chunkX, final int chunkZ) {
        if (world == null) {
            return;
        }
        for (final PositionKey position : Set.copyOf(this.promotedPositions)) {
            if (!position.worldName().equals(world.getName())) {
                continue;
            }
            if ((position.x() >> 4) == chunkX && (position.z() >> 4) == chunkZ) {
                demote(world, position, false);
            }
        }
    }

    public void demoteBlock(final PlacedBlockModel block) {
        if (block == null) {
            return;
        }
        final PromotedBlock promoted = findPromotedBlock(block);
        final World world = promoted != null && promoted.world() != null ? promoted.world() : null;
        if (world == null) {
            return;
        }
        for (final PositionKey position : Set.copyOf(this.promotedPositions)) {
            if (!position.worldName().equals(block.world())) {
                continue;
            }
            final PlacedBlockModel owner = this.stateRegistry.blockAt(position.worldName(), position.x(), position.y(), position.z());
            if (owner != null && owner.uniqueId().equals(block.uniqueId())) {
                demote(world, position, false);
            }
        }
        demote(world, new PositionKey(block.world(), (int) Math.floor(block.x()), (int) Math.floor(block.y()), (int) Math.floor(block.z())), false);
    }

    public boolean isPromoted(final String worldName, final int x, final int y, final int z) {
        return this.promotedPositions.contains(new PositionKey(worldName, x, y, z));
    }

    private Set<PositionKey> collectNearFakeBlocks(final World world, final Location playerLocation, final double radiusSquared) {
        final Set<PositionKey> result = new HashSet<>();
        for (final String rawKey : FakeBlockRegistry.positionsForWorld(world.getName())) {
            final PositionKey position = PositionKey.parse(rawKey);
            if (position == null) {
                continue;
            }
            if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                continue;
            }
            if (position.center(world).distanceSquared(playerLocation) <= radiusSquared) {
                result.add(position);
            }
        }
        return result;
    }

    private void promote(final World world, final PositionKey position) {
        if (!this.promotedPositions.add(position)) {
            return;
        }

        final String visualMaterialName = FakeBlockRegistry.getMaterial(position.worldName(), position.x(), position.y(), position.z());
        final String serverMaterialName = FakeBlockRegistry.getServerMaterial(position.worldName(), position.x(), position.y(), position.z());
        final Material serverMaterial = serverMaterialName == null ? null : Material.matchMaterial(serverMaterialName);
        if (serverMaterial == null || !serverMaterial.isBlock()) {
            this.promotedPositions.remove(position);
            return;
        }

        if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            this.promotedPositions.remove(position);
            return;
        }

        final Block block = world.getBlockAt(position.x(), position.y(), position.z());
        final BlockData originalData = block.getBlockData();
        this.promotedBlocks.put(position, new PromotedBlock(
                world,
                originalData,
                visualMaterialName != null ? visualMaterialName : serverMaterial.name(),
                serverMaterial.name()
        ));
        FakeBlockRegistry.remove(position.worldName(), position.x(), position.y(), position.z());
        FakeBlockRegistry.markForRestore(
                position.worldName(), position.x(), position.y(), position.z(),
                originalData.getAsString()
        );
        savePendingRestores();
        block.setBlockData(serverMaterial.createBlockData(), false);
    }

    private void demoteStalePositions(final World world, final double radiusSquared) {
        final double demoteRadiusSquared = demoteRadiusSquared(radiusSquared);
        for (final PositionKey position : Set.copyOf(this.promotedPositions)) {
            if (!position.worldName().equals(world.getName())) {
                continue;
            }
            if (hasNearbyPlayer(world, position, demoteRadiusSquared)) {
                continue;
            }
            demote(world, position, true);
        }
    }

    private boolean hasNearbyPlayer(final World world, final PositionKey position, final double radiusSquared) {
        final Location center = position.center(world);
        for (final Player player : world.getPlayers()) {
            if (player.isOnline() && player.getLocation().distanceSquared(center) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void demote(final World world, final PositionKey position, final boolean smoothTransition) {
        final PromotedBlock promoted = this.promotedBlocks.get(position);
        if (promoted == null) {
            this.promotedPositions.remove(position);
            this.demotingPositions.remove(position);
            return;
        }

        final Material material = Material.matchMaterial(promoted.visualMaterialName());
        if (smoothTransition && this.plugin.isEnabled()) {
            if (!this.demotingPositions.add(position)) {
                return;
            }
            if (!registerAndShowFake(world, position, promoted, material)) {
                this.demotingPositions.remove(position);
                return;
            }
            this.scheduler.runAtLocationLater(position.base(world), () -> {
                if (!this.demotingPositions.remove(position)) {
                    return;
                }
                if (this.promotedBlocks.get(position) != promoted) {
                    return;
                }
                completeDemotion(world, position, promoted, material, true);
            }, DEMOTE_WORLD_RESTORE_DELAY_TICKS);
            return;
        }

        this.demotingPositions.remove(position);
        completeDemotion(world, position, promoted, material, false);
    }

    private void completeDemotion(
            final World world,
            final PositionKey position,
            final PromotedBlock promoted,
            final Material material,
            final boolean scheduleDelayedRefresh
    ) {
        boolean fakeRegistered = false;
        try {
            // Register and send the fake visual before restoring the real world data.
            // The packet handler can then replace the outbound restore update with the
            // fake state, avoiding a one-frame flash of air/original terrain.
            FakeBlockRegistry.add(
                    position.worldName(),
                    position.x(),
                    position.y(),
                    position.z(),
                    promoted.visualMaterialName(),
                    promoted.serverMaterialName()
            );
            fakeRegistered = true;
            if (material != null && material.isBlock()) {
                this.nmsAdapter.showFakeBlock(world, position.base(world), material);
            }

            final Block block = world.getBlockAt(position.x(), position.y(), position.z());
            block.setBlockData(promoted.originalData(), false);
            this.promotedBlocks.remove(position);
            this.promotedPositions.remove(position);
            FakeBlockRegistry.clearPendingRestore(position.worldName(), position.x(), position.y(), position.z());
            savePendingRestores();
        } catch (final RuntimeException ignored) {
            // Folia may reject world access from the shutdown/global thread or
            // expose partially unloaded region data. Release transient state but
            // retain the journal entry for location-scheduled startup recovery.
            if (fakeRegistered) {
                FakeBlockRegistry.remove(position.worldName(), position.x(), position.y(), position.z());
            }
            this.promotedBlocks.remove(position);
            this.promotedPositions.remove(position);
            return;
        }
        if (material != null && material.isBlock()) {
            if (!scheduleDelayedRefresh || !this.plugin.isEnabled()) {
                return;
            }
            for (int delay = 1; delay <= DEMOTE_FAKE_REFRESH_TICKS; delay++) {
                this.scheduler.runAtLocationLater(position.base(world), () -> {
                    if (FakeBlockRegistry.contains(position.worldName(), position.x(), position.y(), position.z())) {
                        this.nmsAdapter.showFakeBlock(world, position.base(world), material);
                    }
                }, delay);
            }
        }
    }

    private boolean registerAndShowFake(
            final World world,
            final PositionKey position,
            final PromotedBlock promoted,
            final Material material
    ) {
        try {
            FakeBlockRegistry.add(
                    position.worldName(), position.x(), position.y(), position.z(),
                    promoted.visualMaterialName(), promoted.serverMaterialName()
            );
            if (material != null && material.isBlock()) {
                this.nmsAdapter.showFakeBlock(world, position.base(world), material);
            }
            return true;
        } catch (final RuntimeException ignored) {
            FakeBlockRegistry.remove(position.worldName(), position.x(), position.y(), position.z());
            return false;
        }
    }

    private static double demoteRadiusSquared(final double promoteRadiusSquared) {
        final double promoteRadius = Math.sqrt(promoteRadiusSquared);
        final double demoteRadius = promoteRadius + DEMOTE_RADIUS_PADDING;
        return demoteRadius * demoteRadius;
    }

    private PromotedBlock findPromotedBlock(final PlacedBlockModel block) {
        for (final java.util.Map.Entry<PositionKey, PromotedBlock> entry : this.promotedBlocks.entrySet()) {
            if (entry.getKey().worldName().equals(block.world())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private record PromotedBlock(World world, BlockData originalData, String visualMaterialName, String serverMaterialName) {
    }

    private void savePendingRestores() {
        FakeBlockRegistry.savePendingRestores(
                new java.io.File(this.plugin.getDataFolder(), "promoted-blocks.json")
        );
    }

    private record PositionKey(String worldName, int x, int y, int z) {
        private static PositionKey parse(final String rawKey) {
            final String[] parts = rawKey.split(":");
            if (parts.length != 4) {
                return null;
            }
            try {
                return new PositionKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (final NumberFormatException ignored) {
                return null;
            }
        }

        private Location base(final World world) {
            return new Location(world, this.x, this.y, this.z);
        }

        private Location center(final World world) {
            return new Location(world, this.x + 0.5D, this.y + 0.5D, this.z + 0.5D);
        }
    }
}
