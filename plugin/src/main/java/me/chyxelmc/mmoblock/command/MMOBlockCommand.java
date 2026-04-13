package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigService;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MMOBlockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBCOMMANDS = List.of("place", "remove", "reload");
    private static final List<String> RELOAD_SUBCOMMANDS = List.of("config", "blocks", "drops", "lang", "tools");
    private static final List<String> FACINGS = List.of("north", "south", "east", "west");

    private final MMOBlock plugin;
    private final BlockConfigService configService;
    private final BlockRuntimeService runtimeService;
    private final RuntimeCoordinator runtimeCoordinator;

    public MMOBlockCommand(
        final MMOBlock plugin,
        final BlockConfigService configService,
        final BlockRuntimeService runtimeService,
        final RuntimeCoordinator runtimeCoordinator
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.runtimeService = runtimeService;
        this.runtimeCoordinator = runtimeCoordinator;
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @Nullable Command command, final @NotNull String label, final @NotNull String[] args) {
        if (args.length == 0) {
            send(sender, this.configService.messageComponent("commands.usage.place", "/mmoblock place <blockid/entityid> x y z world facing"));
            send(sender, this.configService.messageComponent("commands.usage.remove", "/mmoblock remove <blockid/entityid> x y z world"));
            send(sender, this.configService.messageComponent("commands.usage.reload", "/mmoblock reload [config|blocks|drops|lang|tools]"));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "place" -> handlePlace(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "reload" -> handleReload(sender, args);
            default -> {
                send(sender, this.configService.messageComponent("commands.unknown_subcommand", "Unknown subcommand."));
                yield true;
            }
        };
    }

    private boolean handlePlace(final CommandSender sender, final String[] args) {
        if (args.length != 7) {
            send(sender, this.configService.messageComponent("commands.place.usage", "Usage: /mmoblock place <blockid/entityid> x y z world facing(north, south, east, west)"));
            return true;
        }

        final String blockId = args[1];
        final Double x = parseDouble(args[2], sender, "x");
        final Double y = parseDouble(args[3], sender, "y");
        final Double z = parseDouble(args[4], sender, "z");
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = Bukkit.getWorld(args[5]);
        if (world == null) {
            send(sender, this.configService.messageComponent("commands.world_not_found", "World not found: {world}", java.util.Map.of("{world}", args[5])));
            return true;
        }

        final String facing = args[6].toLowerCase(Locale.ROOT);
        if (!FACINGS.contains(facing)) {
            send(sender, this.configService.messageComponent("commands.place.invalid_facing", "Invalid facing: {facing} (use north, south, east, west)", java.util.Map.of("{facing}", facing)));
            return true;
        }

        final BlockRuntimeService.PlaceResult result = this.runtimeService.place(blockId, world, x, y, z, facing);
        if (!result.success()) {
            send(sender, this.configService.messageComponent("commands.place.failed", "Failed to place block: {reason}", java.util.Map.of("{reason}", result.message())));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.place.success", "Placed {id} at {world} {x} {y} {z}", java.util.Map.of(
            "{id}", result.placedBlock().type(),
            "{world}", world.getName(),
            "{x}", String.valueOf(x),
            "{y}", String.valueOf(y),
            "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleRemove(final CommandSender sender, final String[] args) {
        if (args.length != 6) {
            send(sender, this.configService.messageComponent("commands.remove.usage", "Usage: /mmoblock remove <blockid/entityid> x y z world"));
            return true;
        }

        final String blockId = args[1];
        final Double x = parseDouble(args[2], sender, "x");
        final Double y = parseDouble(args[3], sender, "y");
        final Double z = parseDouble(args[4], sender, "z");
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = Bukkit.getWorld(args[5]);
        if (world == null) {
            send(sender, this.configService.messageComponent("commands.world_not_found", "World not found: {world}", java.util.Map.of("{world}", args[5])));
            return true;
        }

        final boolean removed = this.runtimeService.remove(blockId, world, x, y, z);
        if (!removed) {
            send(sender, this.configService.messageComponent("commands.remove.not_found", "Block not found at that location."));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.remove.success", "Removed {id} at {world} {x} {y} {z}", java.util.Map.of(
            "{id}", blockId,
            "{world}", world.getName(),
            "{x}", String.valueOf(x),
            "{y}", String.valueOf(y),
            "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleReload(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            this.configService.reloadAll();
            final BlockRuntimeService.ReconcileResult result = this.runtimeCoordinator.reconcileAfterConfigReload(true);
            send(sender, this.configService.messageComponent("commands.reload.all", "Reloaded config, blocks, drops, lang, and tools."));
            send(sender, formatValidation("blocks", this.configService.lastBlockReport()));
            send(sender, formatValidation("tools", this.configService.lastToolReport()));
            send(sender, formatValidation("drops", this.configService.lastDropReport()));
            send(sender, formatReconcileResult(result));
            return true;
        }

        final String type = args[1].toLowerCase(Locale.ROOT);
        switch (type) {
            case "config" -> {
                this.plugin.reloadConfig();
                send(sender, this.configService.messageComponent("commands.reload.config", "Reloaded config.yml"));
            }
            case "blocks" -> {
                send(sender, Component.text("Reloaded blocks folder: " + this.configService.reloadBlocks() + " entries"));
                send(sender, formatValidation("blocks", this.configService.lastBlockReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(true)));
            }
            case "drops" -> {
                send(sender, Component.text("Reloaded drops folder: " + this.configService.reloadDrops() + " files"));
                send(sender, formatValidation("drops", this.configService.lastDropReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(false)));
            }
            case "lang" -> send(sender, Component.text("Reloaded lang folder: " + this.configService.reloadLanguages() + " files"));
            case "tools" -> {
                send(sender, Component.text("Reloaded tools folder: " + this.configService.reloadTools() + " files"));
                send(sender, formatValidation("tools", this.configService.lastToolReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(false)));
            }
            default -> send(sender, Component.text("Unknown reload target. Use config, blocks, drops, lang, or tools."));
        }
        return true;
    }

    private Component formatValidation(final String type, final BlockConfigService.ValidationReport report) {
        return Component.text("Validation " + type + " -> errors=" + report.errorCount() + ", warnings=" + report.warningCount());
    }

    private Component formatReconcileResult(final BlockRuntimeService.ReconcileResult result) {
        return Component.text("Runtime sync -> rebound=" + result.reboundInteractions()
            + ", cleaned=" + result.cleanedMissingDefinitions()
            + ", rescheduled=" + result.rescheduledRespawns()
            + ", failed=" + result.failedRebinds());
    }

    @Override
    public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender, final @Nullable Command command, final @NotNull String alias, final @NotNull String[] args) {
        if (args.length == 0) {
            return ROOT_SUBCOMMANDS;
        }

        if (args.length == 1) {
            return filter(ROOT_SUBCOMMANDS, args[0]);
        }

        final String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub) && args.length == 2) {
            return filter(RELOAD_SUBCOMMANDS, args[1]);
        }
        if (("place".equals(sub) || "remove".equals(sub)) && args.length == 2) {
            return filter(new ArrayList<>(this.runtimeService.blockIds()), args[1]);
        }
        if (("place".equals(sub) || "remove".equals(sub)) && args.length == 6) {
            return filter(this.configService.knownWorlds().stream().toList(), args[5]);
        }
        if ("place".equals(sub) && args.length == 7) {
            return filter(FACINGS, args[6]);
        }

        return List.of();
    }

    private Double parseDouble(final String raw, final CommandSender sender, final String name) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException exception) {
            send(sender, Component.text("Invalid " + name + ": " + raw));
            return null;
        }
    }

    private void send(final CommandSender sender, final Component message) {
        sender.sendMessage(message);
    }

    private List<String> filter(final List<String> values, final String input) {
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
            .sorted()
            .toList();
    }
}

