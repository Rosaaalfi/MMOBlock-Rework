package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.command.sub.BlockCommand;
import me.chyxelmc.mmoblock.command.sub.DebugCommand;
import me.chyxelmc.mmoblock.command.sub.NodeCommand;
import me.chyxelmc.mmoblock.command.sub.ReloadCommand;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Thin router — the sole {@link CommandExecutor} + {@link TabCompleter} for
 * the {@code /mmoblock} command.
 * <p>
 * All routing logic is delegated to {@link CommandManager}, which dispatches
 * to individual {@link SubCommand} implementations. This class is responsible
 * only for:
 * <ul>
 *   <li>Building the shared {@link CommandContext}</li>
 *   <li>Registering all subcommands with the manager</li>
 *   <li>Showing the top-level usage help</li>
 *   <li>Forwarding Bukkit's lifecycle calls to the manager</li>
 * </ul>
 * </p>
 * <p>
 * This cleanly splits the 890-line God Object into seven focused classes
 * (~100–200 lines each), each with a single responsibility.
 * </p>
 */
public final class MMOBlockCommand implements CommandExecutor, TabCompleter {

    private final CommandManager manager;
    private final CommandContext ctx;

    public MMOBlockCommand(
            final MMOBlock plugin,
            final BlockConfigLoader configService,
            final NodeConfigLoader nodeConfigService,
            final BlockRuntimeService runtimeService,
            final NodeRuntimeService nodeRuntimeService,
            final RuntimeCoordinator runtimeCoordinator
    ) {
        this.ctx = new CommandContext(
                plugin,
                configService,
                nodeConfigService,
                runtimeService,
                nodeRuntimeService,
                runtimeCoordinator,
                new CustomItemUtil(plugin)
        );

        this.manager = new CommandManager();
        this.manager.register(new BlockCommand(this.ctx), "block");
        this.manager.register(new NodeCommand(this.ctx), "node");
        this.manager.register(new ReloadCommand(this.ctx), "reload");
        this.manager.register(new DebugCommand(this.ctx), "debug");
    }

    @Override
    public boolean onCommand(
            final @NotNull CommandSender sender,
            final @Nullable Command command,
            final @NotNull String label,
            final @NotNull String[] args
    ) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }

        final boolean handled = this.manager.execute(sender, args);
        if (!handled) {
            this.ctx.sendMessage(sender, "commands.unknown_subcommand", "&cUnknown subcommand: {subcommand}",
                    java.util.Map.of("{subcommand}", args[0]));
            showUsage(sender);
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(
            final @NotNull CommandSender sender,
            final @Nullable Command command,
            final @NotNull String alias,
            final @NotNull String[] args
    ) {
        if (args.length == 0) {
            return this.manager.subcommandNames();
        }
        return this.manager.tabComplete(sender, args);
    }

    // -------------------------------------------------------------
    // Usage help
    // -------------------------------------------------------------

    private void showUsage(final CommandSender sender) {
        this.ctx.sendMessage(sender, "commands.usage.block",
                "§e§lMMOBlock Commands:\n§7/mmoblock block §8- Manage blocks (place, remove, get, list)");
        this.ctx.sendMessage(sender, "commands.usage.node",
                "§7/mmoblock node §8- Manage nodes (place, remove, get, list)");
        this.ctx.sendMessage(sender, "commands.usage.reload",
                "§7/mmoblock reload §8- Reload subsystems (config, blocks, drops, lang, tools, nodes)");
        this.ctx.sendMessage(sender, "commands.usage.debug",
                "§7/mmoblock debug §8- Debug utilities (placeholder parse)");
    }
}
