package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import me.chyxelmc.mmoblock.platform.scheduler.SchedulerTask;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class PacketNmsBackend implements HologramRuntimeService.HologramBackend {


        private final MMOBlock plugin;
        private final NmsAdapter nmsAdapter;
        private final Scheduler scheduler;
        private final Map<UUID, HologramPacketSession> sessions = new ConcurrentHashMap<>();
        private final HologramSyncQueue syncQueue = new HologramSyncQueue();
        private final HologramPacketLineFactory packetLineFactory = new HologramPacketLineFactory();
        private final HologramPacketLineResolver packetLineResolver;
        private final HologramPacketViewerSync packetViewerSync;
        private final HologramPlaceholderResolver placeholderResolver;
        private final HologramVisibilityPolicy visibilityPolicy = new HologramVisibilityPolicy();
        private final HologramScanScheduler scanScheduler;
        private final AtomicLong sessionRevision = new AtomicLong();
        private final SchedulerTask flushTask;

    PacketNmsBackend(final MMOBlock plugin, final NmsAdapter nmsAdapter, final Scheduler scheduler) {
            this.plugin = plugin;
            this.nmsAdapter = nmsAdapter;
            this.scheduler = scheduler;
            this.placeholderResolver = new HologramPlaceholderResolver(plugin);
            this.packetLineResolver = new HologramPacketLineResolver(
                    scheduler,
                    this.sessions,
                    this.packetLineFactory,
                    this::enqueueCurrentViewersAndNearby
            );
            this.packetViewerSync = new HologramPacketViewerSync(
                    plugin,
                    nmsAdapter,
                    this.sessions,
                    this.packetLineFactory,
                    this.packetLineResolver,
                    this.placeholderResolver,
                    this.visibilityPolicy
            );
            this.scanScheduler = new HologramScanScheduler(plugin, this.sessions, this.visibilityPolicy, this::enqueueSync);
            this.flushTask = this.scheduler.runTimer(this::flushPendingSync, 1L, 1L);
        }

        @Override
        public String name() {
            return "packet-nms";
        }

        @Override
        public void upsert(
                final PlacedBlockModel block,
                final Location baseLocation,
                final List<RenderedHologramLine> lines,
                final HologramPlaceholderValues placeholderValues,
                final BlockDefinitionModel definition
        ) {
            final World world = baseLocation.getWorld();
            if (world == null) {
                return;
            }

            final long revision = this.sessionRevision.incrementAndGet();
            final boolean animated = HologramRuntimeService.hasAnimatedText(lines);
            final boolean dynamicPlaceholders = HologramRuntimeService.hasPlaceholderApiTokens(lines);
            final HologramPacketSession session = new HologramPacketSession(
                    block.world(),
                    baseLocation.clone(),
                    new ArrayList<>(lines),
                    new HashSet<>(),
                    List.of(),
                    revision,
                    animated,
                    dynamicPlaceholders,
                    placeholderValues,
                    definition
            );

            // Clear old caches when creating new session
            final HologramPacketSession oldSession = this.sessions.get(block.uniqueId());
            if (oldSession != null) {
                oldSession.lastSentState().clear();
                oldSession.lastSentResolvedLines().clear();
            }

            this.sessions.put(block.uniqueId(), session);

            if (animated) {
                session.setPacketLines(this.packetLineFactory.toPacketLines(session.lines(), HologramRuntimeService.currentAnimationStep()));
                for (final Player viewer : world.getPlayers()) {
                    enqueueSync(viewer.getUniqueId(), block.uniqueId(), HologramSyncAction.UPSERT);
                }
                return;
            }

            this.packetLineResolver.resolveInitial(block.uniqueId(), revision, session);
        }

        @Override
        public void remove(final PlacedBlockModel block) {
            final HologramPacketSession session = this.sessions.remove(block.uniqueId());
            if (session == null) {
                return;
            }
            // Clear caches when removing hologram
            session.lastSentState().clear();
            session.lastSentResolvedLines().clear();
            final World world = this.plugin.getServer().getWorld(session.worldName());
            if (world == null) {
                return;
            }
            for (final Player viewer : world.getPlayers()) {
                enqueueSync(viewer.getUniqueId(), block.uniqueId(), HologramSyncAction.REMOVE);
            }
        }

        @Override
        public void clearAll() {
            final List<UUID> ids = new ArrayList<>(this.sessions.keySet());
            for (final UUID id : ids) {
                final HologramPacketSession session = this.sessions.remove(id);
                if (session == null) {
                    continue;
                }
                // Clear all caches
                session.lastSentState().clear();
                session.lastSentResolvedLines().clear();
                final World world = this.plugin.getServer().getWorld(session.worldName());
                if (world == null) {
                    continue;
                }
                for (final Player viewer : world.getPlayers()) {
                    enqueueSync(viewer.getUniqueId(), id, HologramSyncAction.REMOVE);
                }
            }
        }

        @Override
        public void shutdown() {
            clearAll();
            flushPendingSync();
            this.syncQueue.clear();
            this.flushTask.cancel();
        }

        @Override
        public void syncForPlayer(final Player player, final Collection<PlacedBlockModel> blocks) {
            for (final PlacedBlockModel block : blocks) {
                enqueueSync(player.getUniqueId(), block.uniqueId(), HologramSyncAction.UPSERT);
            }
        }

        @Override
        public void handleViewerQuit(final UUID playerUniqueId) {
            for (final HologramPacketSession session : this.sessions.values()) {
                session.viewers().remove(playerUniqueId);
                session.lastSentState().remove(playerUniqueId);
                session.lastSentResolvedLines().remove(playerUniqueId);
            }
            this.syncQueue.removePlayer(playerUniqueId);
        }

        private void enqueueCurrentViewersAndNearby(final UUID hologramUniqueId, final HologramPacketSession session) {
            for (final UUID viewerId : new HashSet<>(session.viewers())) {
                enqueueSync(viewerId, hologramUniqueId, HologramSyncAction.UPSERT);
            }

            final World world = this.plugin.getServer().getWorld(session.worldName());
            if (world == null) {
                return;
            }
            for (final Player player : world.getPlayers()) {
                try {
                    if (this.visibilityPolicy.isViewerInRange(player, world, session)) {
                        enqueueSync(player.getUniqueId(), hologramUniqueId, HologramSyncAction.UPSERT);
                    }
                } catch (final Exception ignored) {
                    // expected - reflection fallback
                }
            }
        }

        private void enqueueSync(final UUID playerUniqueId, final UUID hologramUniqueId, final HologramSyncAction action) {
            this.syncQueue.enqueue(playerUniqueId, hologramUniqueId, action);
        }

        private void flushPendingSync() {
            this.scanScheduler.enqueueBeforeFlush();
            final int budget = Math.max(16, this.plugin.getConfig().getInt("hologram.packet.maxUpdatesPerTick", 200));
            this.syncQueue.flush(this.plugin, this.nmsAdapter, this.sessions, budget, this.packetViewerSync::sync);
        }

}
