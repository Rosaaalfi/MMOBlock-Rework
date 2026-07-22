package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class HologramPacketSession {

    private final String worldName;
    private final Location baseLocation;
    private final List<RenderedHologramLine> lines;
    private final Set<UUID> viewers;
    private final long revision;
    private final boolean animated;
    private final boolean dynamicPlaceholders;
    private final HologramPlaceholderValues placeholderValues;
    private final BlockDefinitionModel definition;
    private final AtomicBoolean resolving = new AtomicBoolean(false);
    private final Map<UUID, HologramCachedViewerState> lastSentState = new ConcurrentHashMap<>();
    private final Map<UUID, List<NmsAdapter.HologramLine>> lastSentResolvedLines = new ConcurrentHashMap<>();
    private volatile long packetLinesStep;
    private volatile List<NmsAdapter.HologramLine> packetLines;

    HologramPacketSession(
            final String worldName,
            final Location baseLocation,
            final List<RenderedHologramLine> lines,
            final Set<UUID> viewers,
            final List<NmsAdapter.HologramLine> packetLines,
            final long revision,
            final boolean animated,
            final boolean dynamicPlaceholders,
            final HologramPlaceholderValues placeholderValues,
            final BlockDefinitionModel definition
    ) {
        this.worldName = worldName;
        this.baseLocation = baseLocation;
        this.lines = lines;
        this.viewers = viewers;
        this.packetLines = packetLines;
        this.revision = revision;
        this.animated = animated;
        this.dynamicPlaceholders = dynamicPlaceholders;
        this.placeholderValues = placeholderValues;
        this.definition = definition;
        this.packetLinesStep = animated ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    String worldName() {
        return this.worldName;
    }

    Location baseLocation() {
        return this.baseLocation;
    }

    List<RenderedHologramLine> lines() {
        return this.lines;
    }

    Set<UUID> viewers() {
        return this.viewers;
    }

    List<NmsAdapter.HologramLine> packetLines() {
        return this.packetLines;
    }

    void setPacketLines(final List<NmsAdapter.HologramLine> packetLines) {
        this.packetLines = packetLines;
    }

    boolean startResolving() {
        return this.resolving.compareAndSet(false, true);
    }

    void finishResolving() {
        this.resolving.set(false);
    }

    synchronized List<NmsAdapter.HologramLine> packetLinesForStep(
            final long step,
            final java.util.function.LongFunction<List<NmsAdapter.HologramLine>> compute
    ) {
        if (!this.animated) {
            return this.packetLines;
        }
        if (this.packetLinesStep == step && this.packetLines != null && !this.packetLines.isEmpty()) {
            return this.packetLines;
        }
        final List<NmsAdapter.HologramLine> computed = compute.apply(step);
        this.packetLines = computed;
        this.packetLinesStep = step;
        return computed;
    }

    long revision() {
        return this.revision;
    }

    boolean animated() {
        return this.animated;
    }

    boolean dynamicPlaceholders() {
        return this.dynamicPlaceholders;
    }

    HologramPlaceholderValues placeholderValues() {
        return this.placeholderValues;
    }

    BlockDefinitionModel definition() {
        return this.definition;
    }

    Map<UUID, HologramCachedViewerState> lastSentState() {
        return this.lastSentState;
    }

    Map<UUID, List<NmsAdapter.HologramLine>> lastSentResolvedLines() {
        return this.lastSentResolvedLines;
    }

    void cacheResolvedLines(final UUID viewerId, final List<NmsAdapter.HologramLine> lines) {
        this.lastSentResolvedLines.put(viewerId, lines);
    }
}
