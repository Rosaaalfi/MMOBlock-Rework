package me.chyxelmc.mmoblock.command.sub;

import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Placeholder subcommand for {@code /mmoblock db}.
 * <p>
 * The database changepassword command was removed in favor of
 * changing the password only via config.yml + server restart.
 * </p>
 */
public final class DbCommand implements SubCommand {

    private final CommandContext ctx;

    public DbCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        sender.sendMessage(Component.text("§cThe 'db changepassword' command has been removed. "
                + "To change the database password, edit config.yml (databases.h2.password) and restart the server."));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        return List.of();
    }
}
