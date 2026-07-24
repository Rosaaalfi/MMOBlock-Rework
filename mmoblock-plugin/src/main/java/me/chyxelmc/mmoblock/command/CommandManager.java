package me.chyxelmc.mmoblock.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry that maps subcommand names to {@link SubCommand} instances.
 * <p>
 * Acts as the central dispatcher: the thin {@link MMOBlockCommand} router
 * delegates to this manager, which looks up the appropriate handler and
 * delegates execution and tab-completion to it.
 * </p>
 * <p>
 * This follows the ECS-inspired pattern of having a "system" (the manager)
 * that coordinates stateless operations (command execution) without owning
 * stateful data itself.
 * </p>
 */
public final class CommandManager {

    private final Map<String, SubCommand> subcommands = new HashMap<>();

    /**
     * Register a subcommand under one or more names (e.g. "block" and "b").
     *
     * @param names      one or more aliases for this subcommand
     * @param subcommand the handler instance
     */
    public void register(final SubCommand subcommand, final String... names) {
        for (final String name : names) {
            this.subcommands.put(name.toLowerCase(Locale.ROOT), subcommand);
        }
    }

    /**
     * Execute a root-level subcommand by name.
     *
     * @param sender the command sender
     * @param args   the full argument array (args[0] is the subcommand name)
     * @return true if handled, false otherwise
     */
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 0) {
            return false;
        }
        final SubCommand handler = this.subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (handler == null) {
            return false;
        }
        return handler.execute(sender, args);
    }

    /**
     * Compute tab-completion suggestions for the current argument position.
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @return suggestions for the current (last) argument, or empty list
     */
    @NotNull
    public List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            // Suggest root subcommand names
            return filter(subcommands.keySet(), args[0]);
        }
        // Delegate to the matched subcommand
        final String name = args[0].toLowerCase(Locale.ROOT);
        final SubCommand handler = this.subcommands.get(name);
        if (handler == null) {
            return List.of();
        }
        return handler.tabComplete(sender, args);
    }

    /**
     * Return an unmodifiable view of all registered subcommand names.
     */
    @NotNull
    public List<String> subcommandNames() {
        return Collections.unmodifiableList(
                this.subcommands.keySet().stream().sorted().toList()
        );
    }

    private List<String> filter(final java.util.Collection<String> values, final String input) {
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }
}
