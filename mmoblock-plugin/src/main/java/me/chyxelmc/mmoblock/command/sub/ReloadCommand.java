package me.chyxelmc.mmoblock.command.sub;
import me.chyxelmc.mmoblock.runtime.block.ReconcileResult;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.utils.TextColor;
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
        // Also reload translations
        ctx.plugin().translationService().reload();
        final ReconcileResult result = ctx.runtimeCoordinator().reconcileAfterConfigReload(true);
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.reload.all", "&aReloaded config, blocks, drops, lang, tools, and nodes."
        ));
        sender.sendMessage(formatValidation("blocks", ctx.configService().lastBlockReport()));
        sender.sendMessage(formatValidation("tools", ctx.configService().lastToolReport()));
        sender.sendMessage(formatValidation("drops", ctx.configService().lastDropReport()));
        if (ctx.nodeRuntimeService() != null) {
            sender.sendMessage(formatNodeValidation(ctx.nodeConfigService().lastNodeReport()));
        }
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
        final int langFiles = ctx.configService().reloadLanguages();
        final int i18nFiles = ctx.plugin().translationService().reload();
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.reload.lang",
                "&aReloaded {lang_files} language files and {i18n_files} translation entries.",
                java.util.Map.of(
                        "{lang_files}", String.valueOf(langFiles),
                        "{i18n_files}", String.valueOf(i18nFiles)
                )
        ));
        return true;
    }

    private boolean handleToolsReload(final CommandSender sender) {
        sender.sendMessage(Component.text("Reloaded tools folder: " + ctx.configService().reloadTools() + " files"));
        sender.sendMessage(formatValidation("tools", ctx.configService().lastToolReport()));
        sender.sendMessage(formatReconcileResult(ctx.runtimeCoordinator().reconcileAfterConfigReload(false)));
        return true;
    }

    private boolean handleNodesReload(final CommandSender sender) {
        if (ctx.nodeRuntimeService() == null) {
            sender.sendMessage(Component.text("Node runtime not available."));
            return true;
        }
        final int loaded = ctx.nodeRuntimeService().reloadNodes();
        sender.sendMessage(Component.text("Reloaded nodes folder: " + loaded + " entries"));
        sender.sendMessage(formatNodeValidation(ctx.nodeConfigService().lastNodeReport()));
        return true;
    }

    // -------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------

    private Component formatValidation(final String type, final BlockConfigLoader.ValidationReport report) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Validation ")
                .append(type)
                .append(" -> errors=")
                .append(report.errorCount())
                .append(", warnings=")
                .append(report.warningCount());
        if (report.errorCount() > 0) {
            sb.append(" \n");
            for (final String err : report.errors()) {
                sb.append("  &c✗ ").append(err).append("\n");
            }
        }
        if (report.warningCount() > 0) {
            sb.append(" \n");
            for (final String warn : report.warnings()) {
                sb.append("  &e⚠ ").append(warn).append("\n");
            }
        }
        return TextColor.toComponent(sb.toString().trim());
    }

    private Component formatNodeValidation(final NodeConfigLoader.ValidationReport report) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Validation nodes")
                .append(" -> errors=")
                .append(report.errorCount())
                .append(", warnings=")
                .append(report.warningCount());
        if (report.errorCount() > 0) {
            sb.append(" \n");
            for (final String err : report.errors()) {
                sb.append("  &c✗ ").append(err).append("\n");
            }
        }
        if (report.warningCount() > 0) {
            sb.append(" \n");
            for (final String warn : report.warnings()) {
                sb.append("  &e⚠ ").append(warn).append("\n");
            }
        }
        return TextColor.toComponent(sb.toString().trim());
    }

    private Component formatReconcileResult(final ReconcileResult result) {
        return Component.text("Runtime sync -> rebound=" + result.reboundInteractions()
                + ", cleaned=" + result.cleanedMissingDefinitions()
                + ", rescheduled=" + result.rescheduledRespawns()
                + ", failed=" + result.failedRebinds());
    }
}
