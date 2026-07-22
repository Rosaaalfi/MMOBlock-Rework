package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.platform.scheduler.Scheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class HologramPacketLineResolver {

    private final Scheduler scheduler;
    private final Map<UUID, HologramPacketSession> sessions;
    private final HologramPacketLineFactory packetLineFactory;
    private final ResolvedSessionConsumer resolvedSessionConsumer;

    HologramPacketLineResolver(
            final Scheduler scheduler,
            final Map<UUID, HologramPacketSession> sessions,
            final HologramPacketLineFactory packetLineFactory,
            final ResolvedSessionConsumer resolvedSessionConsumer
    ) {
        this.scheduler = scheduler;
        this.sessions = sessions;
        this.packetLineFactory = packetLineFactory;
        this.resolvedSessionConsumer = resolvedSessionConsumer;
    }

    void resolveInitial(final UUID hologramUniqueId, final long revision, final HologramPacketSession session) {
        resolveAsync(hologramUniqueId, session, true, revision);
    }

    boolean ensureResolved(final UUID hologramUniqueId, final HologramPacketSession session) {
        if (!session.packetLines().isEmpty()) {
            return true;
        }
        if (session.startResolving()) {
            resolveAsync(hologramUniqueId, session, false, session.revision());
        }
        return false;
    }

    private void resolveAsync(
            final UUID hologramUniqueId,
            final HologramPacketSession session,
            final boolean initialResolve,
            final long revision
    ) {
        CompletableFuture
                .supplyAsync(() -> this.packetLineFactory.toPacketLines(session.lines(), HologramRuntimeService.currentAnimationStep()))
                .whenComplete((packetLines, throwable) -> this.scheduler.run(() -> {
                    try {
                        applyResolvedLines(hologramUniqueId, revision, packetLines, throwable);
                    } finally {
                        final HologramPacketSession current = this.sessions.get(hologramUniqueId);
                        if (!initialResolve && current != null) {
                            current.finishResolving();
                        }
                    }
                }));
    }

    private void applyResolvedLines(
            final UUID hologramUniqueId,
            final long revision,
            final List<NmsAdapter.HologramLine> packetLines,
            final Throwable throwable
    ) {
        final HologramPacketSession current = this.sessions.get(hologramUniqueId);
        if (current == null || current.revision() != revision || throwable != null || packetLines == null || packetLines.isEmpty()) {
            return;
        }

        current.setPacketLines(packetLines);
        this.resolvedSessionConsumer.accept(hologramUniqueId, current);
    }

    @FunctionalInterface
    interface ResolvedSessionConsumer {
        void accept(UUID hologramUniqueId, HologramPacketSession session);
    }
}
