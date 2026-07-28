package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class HologramPacketViewerSync {

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Map<UUID, HologramPacketSession> sessions;
    private final HologramPacketLineFactory packetLineFactory;
    private final HologramPacketLineResolver packetLineResolver;
    private final HologramPlaceholderResolver placeholderResolver;
    private final HologramVisibilityPolicy visibilityPolicy;

    HologramPacketViewerSync(
            final MMOBlock plugin,
            final NmsAdapter nmsAdapter,
            final Map<UUID, HologramPacketSession> sessions,
            final HologramPacketLineFactory packetLineFactory,
            final HologramPacketLineResolver packetLineResolver,
            final HologramPlaceholderResolver placeholderResolver,
            final HologramVisibilityPolicy visibilityPolicy
    ) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
        this.sessions = sessions;
        this.packetLineFactory = packetLineFactory;
        this.packetLineResolver = packetLineResolver;
        this.placeholderResolver = placeholderResolver;
        this.visibilityPolicy = visibilityPolicy;
    }

    void sync(final UUID hologramUniqueId, final Player viewer) {
        final HologramPacketSession session = this.sessions.get(hologramUniqueId);
        if (session == null) {
            this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
            return;
        }

        final List<NmsAdapter.HologramLine> linesToSend;
        final long currentAnimationStep;

        if (session.animated()) {
            currentAnimationStep = HologramRuntimeService.currentAnimationStep();
            linesToSend = session.packetLinesForStep(currentAnimationStep, current -> this.packetLineFactory.toPacketLines(session.lines(), current));
        } else if (!this.packetLineResolver.ensureResolved(hologramUniqueId, session)) {
            return;
        } else {
            currentAnimationStep = -1L;
            linesToSend = session.packetLines();
        }

        final World world = this.plugin.getServer().getWorld(session.worldName());
        if (world == null) {
            this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
            return;
        }

        final boolean hasDisplayFacing = this.visibilityPolicy.hasDisplayFacing(session.definition());
        final Location hologramLocation = this.visibilityPolicy.visibleLocation(session, viewer, world);
        if (hologramLocation == null) {
            this.nmsAdapter.removePacketHologram(viewer, hologramUniqueId);
            session.viewers().remove(viewer.getUniqueId());
            session.lastSentState().remove(viewer.getUniqueId());
            session.lastSentResolvedLines().remove(viewer.getUniqueId());
            return;
        }

        final boolean hasPlaceholderTokens = hasPlaceholderApiTokens(linesToSend);
        final List<NmsAdapter.HologramLine> resolvedLines;

        if (session.animated() || hasPlaceholderTokens) {
            resolvedLines = this.placeholderResolver.resolveViewerPlaceholders(
                    viewer,
                    linesToSend,
                    session.placeholderValues(),
                    hologramLocation,
                    hologramUniqueId,
                    session.definition(),
                    currentAnimationStep
            );
        } else {
            resolvedLines = linesToSend;
        }

        final boolean cacheActive = !hasDisplayFacing;
        final HologramCachedViewerState lastState = cacheActive ? session.lastSentState().get(viewer.getUniqueId()) : null;
        final HologramCachedViewerState currentState = new HologramCachedViewerState(currentAnimationStep, session.placeholderValues(), linesToSend);

        if (lastState != null && lastState.equals(currentState) && linesAreEqual(session.lastSentResolvedLines().get(viewer.getUniqueId()), resolvedLines)) {
            session.viewers().add(viewer.getUniqueId());
            return;
        }

        this.nmsAdapter.upsertPacketHologram(viewer, hologramUniqueId, hologramLocation, resolvedLines);
        if (cacheActive) {
            session.lastSentState().put(viewer.getUniqueId(), currentState);
            session.cacheResolvedLines(viewer.getUniqueId(), resolvedLines);
        }
        session.viewers().add(viewer.getUniqueId());
    }

    private static boolean linesAreEqual(final List<NmsAdapter.HologramLine> a, final List<NmsAdapter.HologramLine> b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            final NmsAdapter.HologramLine la = a.get(i);
            final NmsAdapter.HologramLine lb = b.get(i);
            if (la.type() != lb.type()) {
                return false;
            }
            if (!java.util.Objects.equals(la.offsetY(), lb.offsetY())) {
                return false;
            }
            if (la.type() == NmsAdapter.HologramLineType.TEXT) {
                if (!java.util.Objects.equals(la.text(), lb.text())) {
                    return false;
                }
            } else if (la.type() == NmsAdapter.HologramLineType.ITEM || la.type() == NmsAdapter.HologramLineType.BLOCK) {
                if (!java.util.Objects.equals(la.material(), lb.material())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasPlaceholderApiTokens(final List<NmsAdapter.HologramLine> lines) {
        for (final NmsAdapter.HologramLine line : lines) {
            if (line.type() != NmsAdapter.HologramLineType.TEXT || line.text() == null) {
                continue;
            }
            final String text = line.text();
            if (text.contains("%mmoblock_") || text.contains("%") || text.contains("{condition_") || text.contains(HologramRuntimeService.I18N_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}
