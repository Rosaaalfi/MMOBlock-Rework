package me.chyxelmc.mmoblock.gui.management;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.chyxelmc.mmoblock.MMOBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Owns one cancellable, private chat prompt per player. */
final class BlockChatInput implements Listener {
    private final MMOBlock plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    BlockChatInput(final MMOBlock plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void await(final Player player, final Consumer<String> handler) {
        this.pending.put(player.getUniqueId(), handler);
    }

    @EventHandler
    public void onChat(final AsyncPlayerChatEvent event) {
        final Consumer<String> handler = this.pending.get(event.getPlayer().getUniqueId());
        if (handler == null) return;
        event.setCancelled(true);
        this.plugin.scheduler().runForEntity(event.getPlayer(), () -> handler.accept(event.getMessage()),
                () -> this.pending.remove(event.getPlayer().getUniqueId(), handler));
    }

    void complete(final Player player) {
        this.pending.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.pending.remove(event.getPlayer().getUniqueId());
    }
}
