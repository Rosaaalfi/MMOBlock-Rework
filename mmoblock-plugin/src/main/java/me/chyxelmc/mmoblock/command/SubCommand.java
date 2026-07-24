package me.chyxelmc.mmoblock.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base interface for all MMOBlock subcommands.
 * <p>
 * Each subcommand encapsulates both execution and tab-completion logic for
 * a single command action, following the Single Responsibility Principle.
 * Subcommands are registered in {@link CommandManager} and dispatched by
 * the thin {@link MMOBlockCommand} router.
 * </p>
 */
public interface SubCommand {

    /**
     * Execute this subcommand with the given arguments.
     * <p>
     * The {@code args} array starts AFTER the subcommand name.
     * For example, for {@code /mmoblock block place <args...>},
     * the args passed here will be {@code ["place", "<args...>"]}.
     * </p>
     *
     * @param sender the command sender (player or console)
     * @param args   the arguments after the root command name
     * @return true if the command was handled, false otherwise
     */
    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);

    /**
     * Provide tab-completion suggestions for this subcommand.
     * <p>
     * The {@code args} array starts AFTER the root command name.
     * </p>
     *
     * @param sender the command sender
     * @param args   the arguments after the root command name
     * @return list of tab-completion suggestions, or empty list if none
     */
    @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String[] args);
}
