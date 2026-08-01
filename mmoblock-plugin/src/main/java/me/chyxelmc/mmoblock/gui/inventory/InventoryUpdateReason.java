package me.chyxelmc.mmoblock.gui.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryEvent;

public sealed interface InventoryUpdateReason permits InventoryUpdateReason.Programmatic, InventoryUpdateReason.PlayerAction {
    record Programmatic(Object source) implements InventoryUpdateReason { }

    record PlayerAction(Player player, InventoryEvent event) implements InventoryUpdateReason {
        public PlayerAction(final Player player) { this(player, null); }
    }
}
