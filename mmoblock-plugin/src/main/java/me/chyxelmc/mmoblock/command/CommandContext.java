package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.i18n.TranslationService;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Immutable context object that carries all shared dependencies to every
 * {@link SubCommand} implementation.
 */
public record CommandContext(
        MMOBlock plugin,
        BlockConfigLoader configService,
        NodeConfigLoader nodeConfigService,
        BlockRuntimeService runtimeService,
        NodeRuntimeService nodeRuntimeService,
        RuntimeCoordinator runtimeCoordinator,
        CustomItemUtil customItemUtil
) {

    /**
     * Get the translation service for i18n messaging.
     */
    @NotNull
    public TranslationService translationService() {
        return this.plugin.translationService();
    }

    /**
     * Send a localized message to a sender, detecting locale if sender is a Player.
     */
    public void sendMessage(@NotNull final CommandSender sender, @NotNull final String key,
                            @NotNull final String defaultMessage) {
        final Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(translationService().translateComponent(player, key, defaultMessage));
    }

    /**
     * Send a localized message to a sender with placeholders.
     */
    public void sendMessage(@NotNull final CommandSender sender, @NotNull final String key,
                            @NotNull final String defaultMessage,
                            @NotNull final Map<String, String> placeholders) {
        final Player player = sender instanceof Player p ? p : null;
        sender.sendMessage(translationService().translateComponent(player, key, defaultMessage, placeholders));
    }

    /**
     * Get a localized Component for a sender (detects locale if Player).
     */
    @NotNull
    public Component translate(@Nullable final Player player, @NotNull final String key,
                                @NotNull final String defaultMessage) {
        return translationService().translateComponent(player, key, defaultMessage);
    }

    @NotNull
    public Component translate(@Nullable final Player player, @NotNull final String key,
                                @NotNull final String defaultMessage,
                                @NotNull final Map<String, String> placeholders) {
        return translationService().translateComponent(player, key, defaultMessage, placeholders);
    }

    /**
     * Get the player from a sender, or null if console.
     */
    @Nullable
    public static Player getPlayer(@NotNull final CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
