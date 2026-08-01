package me.chyxelmc.mmoblock.gui.inventory;

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

/** Applies supported cursor interactions to virtual slots without exposing Bukkit inventory storage. */
public final class InventoryTransactions {
    public static Result apply(
            final VirtualInventory inventory,
            final int slot,
            final ItemStack cursor,
            final InventoryAction action,
            final InventoryUpdateReason reason
    ) {
        final ItemStack current = inventory.get(slot);
        return switch (action) {
            case PICKUP_ALL -> inventory.set(slot, null, reason) ? new Result(true, current) : Result.REJECTED;
            case PICKUP_HALF -> pickupHalf(inventory, slot, current, reason);
            case PLACE_ALL, SWAP_WITH_CURSOR -> inventory.set(slot, cursor, reason) ? new Result(true, current) : Result.REJECTED;
            case PLACE_ONE -> place(inventory, slot, cursor, 1, reason);
            case PLACE_SOME -> place(inventory, slot, cursor, cursor == null ? 0 : cursor.getAmount(), reason);
            default -> Result.REJECTED;
        };
    }

    private static Result pickupHalf(final VirtualInventory inventory, final int slot, final ItemStack current, final InventoryUpdateReason reason) {
        if (current == null) return Result.REJECTED;
        final int pickup = (current.getAmount() + 1) / 2;
        final ItemStack cursor = current.clone();
        cursor.setAmount(pickup);
        final ItemStack remaining = current.clone();
        remaining.setAmount(current.getAmount() - pickup);
        return inventory.set(slot, remaining.getAmount() == 0 ? null : remaining, reason) ? new Result(true, cursor) : Result.REJECTED;
    }

    private static Result place(final VirtualInventory inventory, final int slot, final ItemStack cursor, final int amount, final InventoryUpdateReason reason) {
        if (cursor == null || amount < 1) return Result.REJECTED;
        final ItemStack current = inventory.get(slot);
        if (current != null && !current.isSimilar(cursor)) return Result.REJECTED;
        final int existing = current == null ? 0 : current.getAmount();
        final int capacity = Math.min(cursor.getMaxStackSize(), inventory.maxStackSize()) - existing;
        final int placed = Math.min(amount, Math.max(0, capacity));
        if (placed == 0) return Result.REJECTED;
        final ItemStack target = cursor.clone();
        target.setAmount(existing + placed);
        if (!inventory.set(slot, target, reason)) return Result.REJECTED;
        final ItemStack remaining = cursor.clone();
        remaining.setAmount(cursor.getAmount() - placed);
        return new Result(true, remaining.getAmount() == 0 ? null : remaining);
    }

    public record Result(boolean accepted, ItemStack cursor) {
        public static final Result REJECTED = new Result(false, null);
    }

    private InventoryTransactions() { }
}
