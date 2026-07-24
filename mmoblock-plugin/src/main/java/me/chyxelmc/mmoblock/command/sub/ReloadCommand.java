package me.chyxelmc.mmoblock.command.sub;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handles the {@code /mmoblock reload} command branch.
 * <p>
 * Supports granular reload of specific subsystems: config, blocks, drops,
 * lang, tools, nodes — or a full reload of all subsystems.
 * </p>
 */
public final class ReloadCommand implements SubCommand {

    private static final List<String> RELOAD_TARGETS = List.of("config", "blocks", "drops", "lang", "tools", "nodes");

    private final CommandContext ctx;

    public ReloadCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!ctx.plugin().isReady()) {
            sender.sendMessage(Component.text("§cCannot reload while plugin is still starting up. Please wait."));
            return true;
        }

        if (args.length == 1) {
            return handleFullReload(sender);
        }

        final String type = args[1].toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "config" -> handleConfigReload(sender);
            case "blocks" -> handleBlocksReload(sender);
            case "drops" -> handleDropsReload(sender);
            case "lang" -> handleLangReload(sender);
            case "tools" -> handleToolsReload(sender);
            case "nodes" -> handleNodesReload(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown reload target. Use config, blocks, drops, lang, tools, or nodes."));
                yield true;
            }
        };
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 2) {
            return CommandArgs.filter(RELOAD_TARGETS, args[1]);
        }
        return List.of();
    }

    // -------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------

    private boolean handleFullReload(final CommandSender sender) {
        ctx.configService().reloadAll();
        if (ctx.nodeRuntimeService() != null) {
            ctx.nodeRuntimeService().reloadNodes();
        }
        final BlockRuntimeService.ReconcileResult result = ctx.runtimeCoordinator().reconcileAfterConfigReload(true);
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.reload.all", "Reloaded config, blocks, drops, lang, and tools."
        ));
        sender.sendMessage(formatValidation("blocks", ctx.configService().lastBlockReport()));
        sender.sendMessage(formatValidation("tools", ctx.configService().lastToolReport()));
        sender.sendMessage(formatValidation("drops", ctx.configService().lastDropReport()));
        return true;
    }

    private boolean handleConfigReload(final CommandSender sender) {
        ctx.plugin().reloadConfig();
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.reload.config", "Reloaded config.yml"
        ));
        return true;
    }

    private boolean handleBlocksReload(final CommandSender sender) {
        sender.sendMessage(Component.text("Reloaded blocks folder: " + ctx.configService().reloadBlocks() + " entries"));
        sender.sendMessage(formatValidation("blocks", ctx.configService().lastBlockReport()));
        sender.sendMessage(formatReconcileResult(ctx.runtimeCoordinator().reconcileAfterConfigReload(true)));
        return true;
    }

    private boolean handleDropsReload(final CommandSender sender) {
        sender.sendMessage(Component.text("Reloaded drops folder: " + ctx.configService().reloadDrops() + " files"));
        sender.sendMessage(formatValidation("drops", ctx.configService().lastDropReport()));
        sender.sendMessage(formatReconcileResult(ctx.runtimeCoordinator().reconcileAfterConfigReload(false)));
        return true;
    }

    private boolean handleLangReload(final CommandSender sender) {
        sender.sendMessage(Component.text("Reloaded lang folder: " + ctx.configService().reloadLanguages() + " files"));
        return true;
    }

    private boolean handleToolsReload(final CommandSender sender) {
        sender.sendMessage(Component.text("Reloaded tools folder: " + ctx.configService().reloadTools() + " files"));
        sender.sendMessage(formatValidation("tools", ctx.configService().lastToolReport()));
        sender.sendMessage(formatReconcileResult(ctx.runtimeCoordinator().reconcileAfterConfigReload(false)));
        return true;
    }

    private boolean handleNodesReload(final CommandSender sender) {
        final int loaded = ctx.nodeRuntimeService() != null ? ctx.nodeRuntimeService().reloadNodes() : 0;
        sender.sendMessage(Component.text("Reloaded nodes folder: " + loaded + " entries"));
        return true;
    }

    // -------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------

    private Component formatValidation(final String type, final BlockConfigLoader.ValidationReport report) {
        return Component.text("Validation " + type + " -> errors=" + report.errorCount()
                + ", warnings=" + report.warningCount());
    }

    private Component formatReconcileResult(final BlockRuntimeService.ReconcileResult result) {
        return Component.text("Runtime sync -> rebound=" + result.reboundInteractions()
                + ", cleaned=" + result.cleanedMissingDefinitions()
                + ", rescheduled=" + result.rescheduledRespawns()
                + ", failed=" + result.failedRebinds());
    }
}
