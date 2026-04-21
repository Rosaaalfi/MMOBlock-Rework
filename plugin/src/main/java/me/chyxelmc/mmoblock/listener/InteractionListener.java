package me.chyxelmc.mmoblock.listener;

import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import net.kyori.adventure.text.Component;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.entity.Player;

public final class InteractionListener implements Listener {

    private static final String CLICK_LEFT = "left_click";
    private static final String CLICK_RIGHT = "right_click";
    private static final long LEGACY_CLICK_DEBOUNCE_MS = 350L;

    private final BlockRuntimeService runtimeService;
    private final java.util.Map<java.util.UUID, Long> legacyRightClickDebounce = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> legacyLeftClickDebounce = new java.util.concurrent.ConcurrentHashMap<>();

    public InteractionListener(final BlockRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    private boolean isLegacyClickDebounced(final org.bukkit.entity.Player player,
                                           final java.util.Map<java.util.UUID, Long> debounceMap) {
        final long now = System.currentTimeMillis();
        final Long prev = debounceMap.get(player.getUniqueId());
        if (prev != null && (now - prev) < LEGACY_CLICK_DEBOUNCE_MS) return true;
        debounceMap.put(player.getUniqueId(), now);
        return false;
    }

    @EventHandler
    public void onPlayerInteractAtEntity(final PlayerInteractAtEntityEvent event) {
        final Component message = this.runtimeService.handleInteraction(event.getRightClicked(), event.getPlayer(), CLICK_RIGHT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(message);
        // Ensure fake-block visuals remain for the clicking player (do not vanish on interaction)
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
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
        // Re-send fake-blocks to the player so the visual does not disappear on click
        this.runtimeService.syncFakeBlocksForPlayer(player);
    }

    @EventHandler
    public void onLegacyRightClick(final PlayerInteractEvent event) {
        if (isLegacyClickDebounced(event.getPlayer(), this.legacyRightClickDebounce)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Component message = this.runtimeService.handleLegacyFallbackInteraction(event.getPlayer(), CLICK_RIGHT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(message);
        // Legacy click also should keep fake blocks visible
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onLegacyLeftClick(final PlayerInteractEvent event) {
        if (isLegacyClickDebounced(event.getPlayer(), this.legacyLeftClickDebounce)) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        final Component message = this.runtimeService.handleLegacyFallbackInteraction(event.getPlayer(), CLICK_LEFT);
        if (message == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(message);
        // Legacy click also should keep fake blocks visible
        this.runtimeService.syncFakeBlocksForPlayer(event.getPlayer());
    }
}
