package me.chyxelmc.mmoblock.runtime.hologram;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Reserves non-overlapping screen-plane slots for each player's drop popups. */
public final class DropPopupSlotAllocator {

    private static final int COLUMN_COUNT = 3;
    private static final double HORIZONTAL_SPACING = 0.9D;
    private static final double VERTICAL_SPACING = 0.42D;

    private final Map<UUID, BitSet> slotsByPlayer = new ConcurrentHashMap<>();

    public Reservation reserve(final UUID playerId) {
        final BitSet slots = this.slotsByPlayer.computeIfAbsent(playerId, ignored -> new BitSet());
        synchronized (slots) {
            final int slot = slots.nextClearBit(0);
            slots.set(slot);
            final int row = slot / COLUMN_COUNT;
            final int column = slot % COLUMN_COUNT;
            final double horizontalOffset = switch (column) {
                case 1 -> -HORIZONTAL_SPACING;
                case 2 -> HORIZONTAL_SPACING;
                default -> 0.0D;
            };
            final double verticalOffset = row * VERTICAL_SPACING;
            return new Reservation(slot, horizontalOffset, verticalOffset);
        }
    }

    public void release(final UUID playerId, final Reservation reservation) {
        if (reservation == null) {
            return;
        }
        final BitSet slots = this.slotsByPlayer.get(playerId);
        if (slots == null) {
            return;
        }
        synchronized (slots) {
            slots.clear(reservation.slot());
            if (slots.isEmpty()) {
                this.slotsByPlayer.remove(playerId, slots);
            }
        }
    }

    public record Reservation(int slot, double horizontalOffset, double verticalOffset) {
    }
}
