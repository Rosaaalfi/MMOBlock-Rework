package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.PlacedBlockModel;
import me.chyxelmc.mmoblock.utils.HologramAnimationUtil;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HologramRuntimeService {

    static final double PACKET_SYNC_RADIUS_SQUARED = 128.0D * 128.0D;
    static final String PH_MAX_PROGRESS = "%mmoblock_max_progress%";
    static final String PH_RESPAWN_TIME = "%mmoblock_respawn_time%";
    static final String STATE_ACTIVE = "ACTIVE";
    static final String DISPLAY_ACTIVE = "Active";

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Scheduler scheduler;
    private final HologramBackend backend;
    private final EcsHologramBridge ecsBridge;
    private final HologramLineComposer lineComposer = new HologramLineComposer();
    private final HologramPacketLineFactory packetLineFactory = new HologramPacketLineFactory();

    public HologramRuntimeService(final MMOBlock plugin, final NmsAdapter nmsAdapter, final Scheduler scheduler) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.scheduler = scheduler;
        this.backend = chooseBackend();
        this.ecsBridge = new EcsHologramBridge(plugin, nmsAdapter);
        // logging removed
    }

    public void setEntityManager(final me.chyxelmc.mmoblock.ecs.EntityManager entityManager) {
        this.ecsBridge.setEntityManager(entityManager);
    }

    public void showActive(final PlacedBlockModel block, final BlockDefinitionModel definition) {
        render(block, definition, HologramRenderState.ACTIVE, "", "", 0, 0, 0L);
    }

    public void showProgress(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final String progressBar,
            final int progress,
            final int maxProgress
    ) {
        render(block, definition, HologramRenderState.PROGRESS, progressBar, "", progress, maxProgress, 0L);
    }

    public void showDead(final PlacedBlockModel block, final BlockDefinitionModel definition, final long seconds) {
        if (block.respawnAt() == null) {
            block.setRespawnAt(System.currentTimeMillis() + seconds * 1000L);
        }
        render(block, definition, HologramRenderState.DEAD, "", String.valueOf(seconds), 0, 0, seconds);
    }

    public void updateDeadRespawnTime(final PlacedBlockModel block, final BlockDefinitionModel definition) {
        if (block.respawnAt() == null) {
            remove(block);
            return;
        }
        final long remaining = Math.max(0L, (block.respawnAt() - System.currentTimeMillis()) / 1000L);
        render(block, definition, HologramRenderState.DEAD, "", String.valueOf(remaining), 0, 0, remaining);
    }

    public void remove(final PlacedBlockModel block) {
        this.ecsBridge.remove(block.uniqueId());
        this.backend.remove(block);
    }

    public void clearAll() {
        this.ecsBridge.clearAll();
        this.backend.clearAll();
    }

    public void shutdown() {
        this.ecsBridge.shutdown();
        this.backend.shutdown();
    }

    public void syncForPlayer(final Player player, final Collection<PlacedBlockModel> blocks) {
        this.ecsBridge.syncForPlayer(player, blocks.stream().map(PlacedBlockModel::uniqueId).toList());
        this.backend.syncForPlayer(player, blocks);
    }

    public void handleViewerQuit(final UUID playerUniqueId) {
        this.backend.handleViewerQuit(playerUniqueId);
    }

    public boolean hasNearbyPlayers(final PlacedBlockModel block, final double radius) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return false;
        }
        final Location center = new Location(world, block.originX() + 0.5D, block.originY() + 0.5D, block.originZ() + 0.5D);
        return !world.getNearbyPlayers(center, radius).isEmpty();
    }

    private void render(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final HologramRenderState state,
            final String progressBar,
            final String respawnTime,
            final int progress,
            final int maxProgress,
            final long respawnTimeSeconds
    ) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null || definition.displayLines().isEmpty()) {
            remove(block);
            return;
        }

        final HologramPacketLayout layout = packetLayoutFromConfig();
        final List<RenderedHologramLine> renderedLines = this.lineComposer.compose(
                definition.displayLines(),
                layout,
                state,
                progressBar,
                respawnTime,
                progress,
                maxProgress,
                respawnTimeSeconds
        );

        if (renderedLines.isEmpty()) {
            remove(block);
            return;
        }

        final Location location = resolveBaseLocation(block, definition, state, world);
        final boolean animated = this.packetLineFactory.hasAnimatedText(renderedLines);
        final boolean containsPlaceholderApiTokens = this.packetLineFactory.hasPlaceholderApiTokens(renderedLines);
        final HologramPlaceholderValues placeholderValues = new HologramPlaceholderValues(progress, maxProgress, respawnTimeSeconds, state.name());

        // If ECS integration available, create/update a HologramComponent entity.
        // Animated holograms are handled by packet backend because they need periodic
        // text recomposition.
        if (this.ecsBridge.available() && !animated && !containsPlaceholderApiTokens) {
            this.backend.remove(block);
            final java.util.List<NmsAdapter.HologramLine> packetLines = this.packetLineFactory.toPacketLines(renderedLines, currentAnimationStep());
            if (!this.ecsBridge.upsertStatic(block.uniqueId(), block.world(), location, packetLines)) {
                this.backend.upsert(block, location, renderedLines, placeholderValues, definition);
            }
            return;
        }

        if (this.ecsBridge.available()) {
            this.ecsBridge.remove(block.uniqueId());
        }

        this.backend.upsert(block, location, renderedLines, placeholderValues, definition);
    }

    static boolean hasAnimatedText(final List<RenderedHologramLine> lines) {
        return new HologramPacketLineFactory().hasAnimatedText(lines);
    }

    static boolean hasPlaceholderApiTokens(final List<RenderedHologramLine> lines) {
        return new HologramPacketLineFactory().hasPlaceholderApiTokens(lines);
    }

    static long currentAnimationStep() {
        return HologramAnimationUtil.currentSystemStep();
    }

    private HologramPacketLayout packetLayoutFromConfig() {
        return new HologramPacketLayout(
                this.plugin.getConfig().getDouble("hologram.packet.spacing.text", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.item", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.spacing.block", 0.25D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.text", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.item", 0.0D),
                this.plugin.getConfig().getDouble("hologram.packet.offset.block", 0.0D)
        );
    }

    private Location resolveBaseLocation(final PlacedBlockModel block, final BlockDefinitionModel definition, final HologramRenderState state, final World world) {
        final boolean dead = state == HologramRenderState.DEAD;
        final double baseX = dead ? block.originX() : block.x();
        final double baseY = dead ? block.originY() : block.y();
        final double baseZ = dead ? block.originZ() : block.z();
        return new Location(world, baseX + 0.5D, baseY + definition.displayHeight(), baseZ + 0.5D);
    }

    private HologramBackend chooseBackend() {
        if (!this.nmsAdapter.supportsPacketHolograms()) {
            throw new IllegalStateException("Current NMS adapter does not support packet holograms");
        }
        return new PacketNmsBackend(this.plugin, this.nmsAdapter, this.scheduler);
    }

    interface HologramBackend {

        String name();

        void upsert(
                PlacedBlockModel block,
                Location baseLocation,
                List<RenderedHologramLine> lines,
                HologramPlaceholderValues placeholderValues,
                BlockDefinitionModel definition
        );

        void remove(PlacedBlockModel block);

        void clearAll();

        default void shutdown() {
            clearAll();
        }

        default void syncForPlayer(final Player player, final Collection<PlacedBlockModel> blocks) {
            // Optional hook for backends that need packet-driven per-player synchronization.
        }

        default void handleViewerQuit(final UUID playerUniqueId) {
            // Optional hook for backends that keep per-viewer runtime state.
        }
    }


    static final Pattern CONDITION_PATTERN = Pattern.compile("\\{condition_(\\d+)}");
}
