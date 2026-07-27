package me.chyxelmc.mmoblock.command.sub;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.utils.InternalPlaceholderResolver;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /mmoblock debug} command branch.
 *
 * Currently supports: {@code debug placeholder parse  }
 *
 */
public final class DebugCommand implements SubCommand {

    private static final List DEBUG_ACTIONS = List.of("placeholder", "extractDefaultAssets");

    private final CommandContext ctx;

    public DebugCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextColor.toComponent("&7"));
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.debug.usage",
                    "Usage: /mmoblock debug placeholder parse <player> <placeholder>"
            ));
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "placeholder" -> handlePlaceholder(sender, args);
            case "extractdefaultassets" -> handleExtractDefaultAssets(sender);
            default -> {
                sender.sendMessage(ctx.configService().messageComponent(
                        "commands.debug.usage",
                        "Usage: /mmoblock debug placeholder parse <player> <placeholder>"
                ));
                yield true;
            }
        };
    }

    private boolean handleExtractDefaultAssets(@NotNull final CommandSender sender) {
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.debug.extracting",
                "&7Extracting default assets..."
        ));
        ctx.configService().extractAllDefaultAssets();
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.debug.extracted",
                "&aDefault assets extracted successfully. Run &7/mmoblock reload&a to apply."
        ));
        return true;
    }

    private boolean handlePlaceholder(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 4 || !"parse".equalsIgnoreCase(args[2])) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.debug.usage",
                    "Usage: /mmoblock debug placeholder parse <player> <placeholder>"
            ));
            return true;
        }

        final Player target = CommandArgs.resolvePlayer(sender, args[3]);
        if (target == null) {
            if ("@s".equalsIgnoreCase(args[3]) || "me".equalsIgnoreCase(args[3])) {
                sender.sendMessage(TextColor.toComponent("&cConsole must specify a player name."));
            } else {
                sender.sendMessage(TextColor.toComponent("&cPlayer not found: " + args[3]));
            }
            return true;
        }

        // Combine remaining args into placeholder text
        final StringBuilder placeholderBuilder = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            if (placeholderBuilder.length() > 0) placeholderBuilder.append(" ");
            placeholderBuilder.append(args[i]);
        }
        final String placeholderText = placeholderBuilder.toString();
        if (placeholderText.isEmpty()) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.debug.usage",
                    "Usage: /mmoblock debug placeholder parse me {mmocore_level}"
            ));
            return true;
        }

        final String internalResolved = InternalPlaceholderResolver.resolve(target, placeholderText);
        if (!internalResolved.equals(placeholderText)) {
            sender.sendMessage(Component.text(internalResolved));
        }

        return true;
    }

    @Override
    public @NotNull List tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 2) {
            return CommandArgs.filter(DEBUG_ACTIONS, args[1]);
        }
        if (args.length == 3 && "placeholder".equalsIgnoreCase(args[1])) {
            return CommandArgs.filter(List.of("parse"), args[2]);
        }
        if (args.length == 4 && "placeholder".equalsIgnoreCase(args[1]) && "parse".equalsIgnoreCase(args[2])) {
            final List targets = new ArrayList(List.of("@s", "me"));
            targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return CommandArgs.filter(targets, args[3]);
        }
        return List.of();
    }
}
