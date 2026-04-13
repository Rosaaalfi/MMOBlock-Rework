package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.entity.Player;

public final class InteractionListener implements Listener {

    private static final String CLICK_LEFT = "left_click";
    private static final String CLICK_RIGHT = "right_click";

    private final BlockRuntimeService runtimeService;

    public InteractionListener(final BlockRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventHandler
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        final Component message = this.runtimeService.handleInteraction(event.getRightClicked(), event.getPlayer(), CLICK_RIGHT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(message);
    }

    @EventHandler
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        final Component message = this.runtimeService.handleInteraction(event.getEntity(), player, CLICK_LEFT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message);
    }
}

