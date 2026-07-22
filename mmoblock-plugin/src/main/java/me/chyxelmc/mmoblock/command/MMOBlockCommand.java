package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MMOBlockCommand implements CommandExecutor, TabCompleter {

    // String constants (declared before List.of() to avoid forward reference issues)
    private static final String CMD_BLOCK = "block";
    private static final String CMD_RELOAD = "reload";
    private static final String CMD_NODE = "node";
    private static final String CMD_PLACE = "place";
    private static final String CMD_REMOVE = "remove";
    private static final String CMD_LIST = "list";
    private static final String CMD_GET = "get";
    private static final String CMD_BLOCKS = "blocks";
    private static final String CMD_DROPS = "drops";
    private static final String CMD_TOOLS = "tools";
    private static final String CMD_NORTH = "north";
    private static final String KEY_WORLD = "{world}";
    private static final String NODE_UNAVAILABLE = "Node runtime not available.";
    private static final String GET_REMOVER_TOKEN = "remover";
    private static final String SUFFIX_FILES = " files";

    private static final List<String> ROOT_SUBCOMMANDS = List.of(CMD_BLOCK, CMD_NODE, CMD_RELOAD);
    private static final List<String> RELOAD_SUBCOMMANDS = List.of("config", CMD_BLOCKS, CMD_DROPS, "lang", CMD_TOOLS, "nodes");
    private static final List<String> NODE_SUBCOMMANDS = List.of(CMD_PLACE, CMD_REMOVE, CMD_LIST, CMD_GET);
    private static final List<String> BLOCK_SUBCOMMANDS = List.of(CMD_PLACE, CMD_REMOVE, CMD_LIST, CMD_GET);
    private static final List<String> FACINGS = List.of(CMD_NORTH, "south", "east", "west");

    private final MMOBlock plugin;
    private final BlockConfigLoader configService;
    private final NodeConfigLoader nodeConfigService;
    private final BlockRuntimeService runtimeService;
    private final me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService;
    private final RuntimeCoordinator runtimeCoordinator;
    private final CustomItemUtil customItemUtil;

    public MMOBlockCommand(
            final MMOBlock plugin,
            final BlockConfigLoader configService,
            final NodeConfigLoader nodeConfigService,
            final BlockRuntimeService runtimeService,
            final me.chyxelmc.mmoblock.runtime.NodeRuntimeService nodeRuntimeService,
            final RuntimeCoordinator runtimeCoordinator
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.nodeConfigService = nodeConfigService;
        this.runtimeService = runtimeService;
        this.nodeRuntimeService = nodeRuntimeService;
        this.runtimeCoordinator = runtimeCoordinator;
        this.customItemUtil = new CustomItemUtil(plugin);
    }

    @Override
    public boolean onCommand(final @NotNull CommandSender sender, final @Nullable Command command, final @NotNull String label, final @NotNull String[] args) {
        if (args.length == 0) {
            send(sender, this.configService.messageComponent("commands.usage.block", "/mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]"));
            send(sender, this.configService.messageComponent("commands.usage.node", "/mmoblock node <place|remove|get|list> <nodeId> x y z [world] [facing]"));
            send(sender, this.configService.messageComponent("commands.usage.reload", "/mmoblock reload [config|blocks|drops|lang|tools|nodes]"));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case CMD_RELOAD -> handleReload(sender, args);
            case CMD_NODE -> handleNodes(sender, args);
            case CMD_BLOCK -> handleBlock(sender, args);
            default -> {
                send(sender, this.configService.messageComponent("commands.unknown_subcommand", "Unknown subcommand."));
                yield true;
            }
        };
    }

    private boolean handleBlock(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            send(sender, this.configService.messageComponent("commands.block.usage", "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]"));
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case CMD_PLACE -> handleBlockPlace(sender, args);
            case CMD_REMOVE -> handleBlockRemove(sender, args);
            case CMD_LIST -> handleBlockList(sender, args);
            case CMD_GET -> handleBlockGet(sender, args);
            default -> {
                send(sender, this.configService.messageComponent("commands.block.usage", "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]"));
                yield true;
            }
        };
    }

    private boolean handleBlockPlace(final CommandSender sender, final String[] args) {
        if (args.length < 6 || args.length > 8) {
            send(sender, this.configService.messageComponent("commands.block.place.usage", "Usage: /mmoblock block place <blockId> x y z [world] [facing]"));
            return true;
        }

        final String blockId = args[2];
        final Location senderLocation = resolveSenderLocation(sender);
        final Double x = parseCoordinate(args[3], sender, "x", senderLocation);
        final Double y = parseCoordinate(args[4], sender, "y", senderLocation);
        final Double z = parseCoordinate(args[5], sender, "z", senderLocation);
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = resolveWorld(sender, args.length >= 7 ? args[6] : null);
        if (world == null) {
            return true;
        }

        final String facing = resolveFacing(sender, args.length >= 8 ? args[7] : null);
        if (facing == null) {
            return true;
        }

        final BlockRuntimeService.PlaceResult result = this.runtimeService.place(blockId, world, x, y, z, facing);
        if (!result.success()) {
            send(sender, this.configService.messageComponent("commands.place.failed", "Failed to place block: {reason}", java.util.Map.of("{reason}", result.message())));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.place.success", "Placed {id} at {world} {x} {y} {z}", java.util.Map.of(
                "{id}", result.placedBlock().type(),
                KEY_WORLD, world.getName(),
                "{x}", String.valueOf(x),
                "{y}", String.valueOf(y),
                "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleBlockRemove(final CommandSender sender, final String[] args) {
        if (args.length < 6 || args.length > 7) {
            send(sender, this.configService.messageComponent("commands.block.remove.usage", "Usage: /mmoblock block remove <blockId> x y z [world]"));
            return true;
        }

        final String blockId = args[2];
        final Location senderLocation = resolveSenderLocation(sender);
        final Double x = parseCoordinate(args[3], sender, "x", senderLocation);
        final Double y = parseCoordinate(args[4], sender, "y", senderLocation);
        final Double z = parseCoordinate(args[5], sender, "z", senderLocation);
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = resolveWorld(sender, args.length >= 7 ? args[6] : null);
        if (world == null) {
            return true;
        }

        final boolean removed = this.runtimeService.remove(blockId, world, x, y, z);
        if (!removed) {
            send(sender, this.configService.messageComponent("commands.remove.not_found", "Block not found at that location."));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.remove.success", "Removed {id} at {world} {x} {y} {z}", java.util.Map.of(
                "{id}", blockId,
                KEY_WORLD, world.getName(),
                "{x}", String.valueOf(x),
                "{y}", String.valueOf(y),
                "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleBlockGet(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, Component.text("Player only command."));
            return true;
        }
        if (args.length < 3) {
            send(sender, this.configService.messageComponent("commands.block.get.usage", "Usage: /mmoblock block get <blockId|remover>"));
            return true;
        }
        final String token = args[2];
        if (GET_REMOVER_TOKEN.equalsIgnoreCase(token)) {
            final ItemStack item = this.customItemUtil.createBlockRemover();
            player.getInventory().addItem(item);
            return true;
        }
        final var definition = this.configService.findBlock(token);
        if (definition == null) {
            send(sender, Component.text("Block not found: " + token));
            return true;
        }
        final ItemStack item = this.customItemUtil.createBlockItem(definition);
        if (item == null) {
            send(sender, Component.text("Block item is not configured for: " + token));
            return true;
        }
        player.getInventory().addItem(item);
        return true;
    }

    private boolean handleBlockList(final CommandSender sender, final String[] args) {
        final List<me.chyxelmc.mmoblock.domain.PlacedBlockModel> blocks = new ArrayList<>(this.runtimeService.placedBlocks());
        if (blocks.isEmpty()) {
            send(sender, Component.text("No blocks placed."));
            return true;
        }
        blocks.sort((a, b) -> {
            final int world = a.world().compareToIgnoreCase(b.world());
            if (world != 0) return world;
            final int x = Double.compare(a.x(), b.x());
            if (x != 0) return x;
            final int y = Double.compare(a.y(), b.y());
            if (y != 0) return y;
            return Double.compare(a.z(), b.z());
        });

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (final NumberFormatException ignored) {}
        }

        final int maxPerPage = 5;
        final int totalPages = (int) Math.ceil((double) blocks.size() / maxPerPage);
        if (page > totalPages) page = totalPages;

        final int fromIndex = (page - 1) * maxPerPage;
        final int toIndex = Math.min(fromIndex + maxPerPage, blocks.size());
        final List<me.chyxelmc.mmoblock.domain.PlacedBlockModel> pageBlocks = blocks.subList(fromIndex, toIndex);

        send(sender, TextColor.toComponent("&eBlock List:"));

        for (final me.chyxelmc.mmoblock.domain.PlacedBlockModel block : pageBlocks) {
            final String line = "&e- &8[&a" + block.type() + "&8]"
                + " &8[&4" + formatListCoord(block.x()) + "&8]"
                + " &8[&a" + formatListCoord(block.y()) + "&8]"
                + " &8[&9" + formatListCoord(block.z()) + "&8]"
                + " &8[&e" + block.world() + "&8]";
            final Component base = TextColor.toComponent(line);
            final String removeCommand = "/mmoblock block remove " + block.type() + " "
                + formatExactCoord(block.x()) + " " + formatExactCoord(block.y()) + " " + formatExactCoord(block.z()) + " " + block.world();
            send(sender, buildListEntry(sender, base, block.world(), block.x(), block.y(), block.z(), removeCommand));
        }

        if (totalPages > 1) {
            send(sender, buildPagination("/mmoblock block list ", page, totalPages));
        }
        return true;
    }

    private boolean handleNodes(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            send(sender, this.configService.messageComponent("commands.node.usage", "Usage: /mmoblock node <place|remove|get|list> <nodeId> x y z [world] [facing]"));
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        if (CMD_PLACE.equals(action)) {
            return handleNodePlace(sender, args);
        }
        if (CMD_REMOVE.equals(action)) {
            return handleNodeRemove(sender, args);
        }
        if (CMD_LIST.equals(action)) {
            return handleNodeList(sender, args);
        }
        if (CMD_GET.equals(action)) {
            return handleNodeGet(sender, args);
        }
        send(sender, this.configService.messageComponent("commands.node.usage", "Usage: /mmoblock node <place|remove|get|list> <nodeId> x y z [world] [facing]"));
        return true;
    }

    private boolean handleNodePlace(final CommandSender sender, final String[] args) {
        if (args.length < 6 || args.length > 8) {
            send(sender, this.configService.messageComponent("commands.node.place.usage", "Usage: /mmoblock node place <nodeId> x y z [world] [facing]"));
            return true;
        }
        if (this.nodeRuntimeService == null) {
            send(sender, Component.text(NODE_UNAVAILABLE));
            return true;
        }

        final String nodeId = args[2];
        final Location senderLocation = resolveSenderLocation(sender);
        final Double x = parseCoordinate(args[3], sender, "x", senderLocation);
        final Double y = parseCoordinate(args[4], sender, "y", senderLocation);
        final Double z = parseCoordinate(args[5], sender, "z", senderLocation);
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = resolveWorld(sender, args.length >= 7 ? args[6] : null);
        if (world == null) {
            return true;
        }

        if (args.length >= 8) {
            final String facing = resolveFacing(sender, args[7]);
            if (facing == null) {
                return true;
            }
        }

        final me.chyxelmc.mmoblock.runtime.NodeRuntimeService.PlaceResult result = this.nodeRuntimeService.placeNode(nodeId, world, x, y, z, true);
        if (!result.success()) {
            send(sender, this.configService.messageComponent("commands.nodes.place.failed", "Failed to place node: {reason}", java.util.Map.of("{reason}", result.message())));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.nodes.place.success", "Placed node {id} at {world} {x} {y} {z}", java.util.Map.of(
                "{id}", nodeId,
                KEY_WORLD, world.getName(),
                "{x}", String.valueOf(x),
                "{y}", String.valueOf(y),
                "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleNodeRemove(final CommandSender sender, final String[] args) {
        if (args.length < 6 || args.length > 7) {
            send(sender, this.configService.messageComponent("commands.node.remove.usage", "Usage: /mmoblock node remove <nodeId> x y z [world]"));
            return true;
        }
        if (this.nodeRuntimeService == null) {
            send(sender, Component.text(NODE_UNAVAILABLE));
            return true;
        }

        final String nodeId = args[2];
        final Location senderLocation = resolveSenderLocation(sender);
        final Double x = parseCoordinate(args[3], sender, "x", senderLocation);
        final Double y = parseCoordinate(args[4], sender, "y", senderLocation);
        final Double z = parseCoordinate(args[5], sender, "z", senderLocation);
        if (x == null || y == null || z == null) {
            return true;
        }

        final World world = resolveWorld(sender, args.length >= 7 ? args[6] : null);
        if (world == null) {
            return true;
        }

        final boolean removed = this.nodeRuntimeService.removeNode(nodeId, world, x, y, z);
        if (!removed) {
            send(sender, this.configService.messageComponent("commands.nodes.remove.not_found", "Node not found at that location."));
            return true;
        }

        send(sender, this.configService.messageComponent("commands.nodes.remove.success", "Removed node {id} at {world} {x} {y} {z}", java.util.Map.of(
                "{id}", nodeId,
                KEY_WORLD, world.getName(),
                "{x}", String.valueOf(x),
                "{y}", String.valueOf(y),
                "{z}", String.valueOf(z)
        )));
        return true;
    }

    private boolean handleNodeList(final CommandSender sender, final String[] args) {
        if (this.nodeRuntimeService == null) {
            send(sender, Component.text(NODE_UNAVAILABLE));
            return true;
        }
        final List<me.chyxelmc.mmoblock.domain.PlacedNodeModel> nodes = new ArrayList<>(this.nodeRuntimeService.placedNodes());
        if (nodes.isEmpty()) {
            send(sender, Component.text("No nodes placed."));
            return true;
        }
        nodes.sort((a, b) -> {
            final int id = a.nodeId().compareToIgnoreCase(b.nodeId());
            if (id != 0) return id;
            final int world = a.world().compareToIgnoreCase(b.world());
            if (world != 0) return world;
            final int x = Double.compare(a.x(), b.x());
            if (x != 0) return x;
            final int y = Double.compare(a.y(), b.y());
            if (y != 0) return y;
            return Double.compare(a.z(), b.z());
        });

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (final NumberFormatException ignored) {}
        }

        final int maxPerPage = 5;
        final int totalPages = (int) Math.ceil((double) nodes.size() / maxPerPage);
        if (page > totalPages) page = totalPages;

        final int fromIndex = (page - 1) * maxPerPage;
        final int toIndex = Math.min(fromIndex + maxPerPage, nodes.size());
        final List<me.chyxelmc.mmoblock.domain.PlacedNodeModel> pageNodes = nodes.subList(fromIndex, toIndex);

        send(sender, TextColor.toComponent("&eNode List:"));

        for (final me.chyxelmc.mmoblock.domain.PlacedNodeModel node : pageNodes) {
            final String line = "&e- &8[&a" + node.nodeId() + "&8]"
                + " &8[&4" + formatListCoord(node.x()) + "&8]"
                + " &8[&a" + formatListCoord(node.y()) + "&8]"
                + " &8[&9" + formatListCoord(node.z()) + "&8]"
                + " &8[&e" + node.world() + "&8]";
            final Component base = TextColor.toComponent(line);
            final String removeCommand = "/mmoblock node remove " + node.nodeId() + " "
                + formatExactCoord(node.x()) + " " + formatExactCoord(node.y()) + " " + formatExactCoord(node.z()) + " " + node.world();
            send(sender, buildListEntry(sender, base, node.world(), node.x(), node.y(), node.z(), removeCommand));
        }

        if (totalPages > 1) {
            send(sender, buildPagination("/mmoblock node list ", page, totalPages));
        }
        return true;
    }

    private boolean handleNodeGet(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, Component.text("Player only command."));
            return true;
        }
        if (args.length < 3) {
            send(sender, this.configService.messageComponent("commands.node.get.usage", "Usage: /mmoblock node get <nodeId|remover>"));
            return true;
        }
        final String token = args[2];
        if (GET_REMOVER_TOKEN.equalsIgnoreCase(token)) {
            final ItemStack item = this.customItemUtil.createNodeRemover();
            player.getInventory().addItem(item);
            return true;
        }
        final var definition = this.nodeConfigService.findNode(token);
        if (definition == null) {
            send(sender, Component.text("Node not found: " + token));
            return true;
        }
        final ItemStack item = this.customItemUtil.createNodeItem(definition);
        if (item == null) {
            send(sender, Component.text("Node item is not configured for: " + token));
            return true;
        }
        player.getInventory().addItem(item);
        return true;
    }

    private boolean handleReload(final CommandSender sender, final String[] args) {
        if (!this.plugin.isReady()) {
            send(sender, Component.text("§cCannot reload while plugin is still starting up. Please wait."));
            return true;
        }
        if (args.length == 1) {
            this.configService.reloadAll();
            if (this.nodeRuntimeService != null) {
                this.nodeRuntimeService.reloadNodes();
            }
            final BlockRuntimeService.ReconcileResult result = this.runtimeCoordinator.reconcileAfterConfigReload(true);
            send(sender, this.configService.messageComponent("commands.reload.all", "Reloaded config, blocks, drops, lang, and tools."));
            send(sender, formatValidation(CMD_BLOCKS, this.configService.lastBlockReport()));
            send(sender, formatValidation(CMD_TOOLS, this.configService.lastToolReport()));
            send(sender, formatValidation(CMD_DROPS, this.configService.lastDropReport()));
            return true;
        }

        final String type = args[1].toLowerCase(Locale.ROOT);
        switch (type) {
            case "config" -> {
                this.plugin.reloadConfig();
                send(sender, this.configService.messageComponent("commands.reload.config", "Reloaded config.yml"));
            }
            case CMD_BLOCKS -> {
                send(sender, Component.text("Reloaded blocks folder: " + this.configService.reloadBlocks() + " entries"));
                send(sender, formatValidation(CMD_BLOCKS, this.configService.lastBlockReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(true)));
            }
            case CMD_DROPS -> {
                send(sender, Component.text("Reloaded drops folder: " + this.configService.reloadDrops() + SUFFIX_FILES));
                send(sender, formatValidation(CMD_DROPS, this.configService.lastDropReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(false)));
            }
            case "lang" -> send(sender, Component.text("Reloaded lang folder: " + this.configService.reloadLanguages() + SUFFIX_FILES));
            case CMD_TOOLS -> {
                send(sender, Component.text("Reloaded tools folder: " + this.configService.reloadTools() + SUFFIX_FILES));
                send(sender, formatValidation(CMD_TOOLS, this.configService.lastToolReport()));
                send(sender, formatReconcileResult(this.runtimeCoordinator.reconcileAfterConfigReload(false)));
            }
            case "nodes" -> {
                final int loaded = this.nodeRuntimeService != null ? this.nodeRuntimeService.reloadNodes() : 0;
                send(sender, Component.text("Reloaded nodes folder: " + loaded + " entries"));
            }
            default -> send(sender, Component.text("Unknown reload target. Use config, blocks, drops, lang, tools, or nodes."));
        }
        return true;
    }

    private Component formatValidation(final String type, final BlockConfigLoader.ValidationReport report) {
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
        if (CMD_RELOAD.equals(sub) && args.length == 2) {
            return filter(RELOAD_SUBCOMMANDS, args[1]);
        }
        if (CMD_NODE.equals(sub) && args.length == 2) {
            return filter(NODE_SUBCOMMANDS, args[1]);
        }
        if (CMD_NODE.equals(sub) && args.length >= 2) {
            final String action = args[1].toLowerCase(Locale.ROOT);
            if (CMD_PLACE.equals(action) || CMD_REMOVE.equals(action)) {
                if (args.length == 3) {
                    final List<String> nodeIds = this.nodeRuntimeService != null ? this.nodeRuntimeService.nodeIds() : List.of();
                    return filter(nodeIds, args[2]);
                }
                if (args.length >= 4 && args.length <= 6) {
                    return filter(suggestCoordinate(sender, args.length - 3), args[args.length - 1]);
                }
                if (args.length == 7) {
                    return filter(this.configService.knownWorlds().stream().toList(), args[6]);
                }
                if (CMD_PLACE.equals(action) && args.length == 8) {
                    return filter(FACINGS, args[7]);
                }
            }
            if (CMD_GET.equals(action) && args.length == 3) {
                final List<String> nodeIds = this.nodeRuntimeService != null ? this.nodeRuntimeService.nodeIds() : List.of();
                final List<String> tokens = new ArrayList<>(nodeIds);
                tokens.add(GET_REMOVER_TOKEN);
                return filter(tokens, args[2]);
            }
        }
        if (CMD_BLOCK.equals(sub) && args.length == 2) {
            return filter(BLOCK_SUBCOMMANDS, args[1]);
        }
        if (CMD_BLOCK.equals(sub) && args.length >= 2) {
            final String action = args[1].toLowerCase(Locale.ROOT);
            if (CMD_PLACE.equals(action) || CMD_REMOVE.equals(action)) {
                if (args.length == 3) {
                    return filter(new ArrayList<>(this.runtimeService.blockIds()), args[2]);
                }
                if (args.length >= 4 && args.length <= 6) {
                    return filter(suggestCoordinate(sender, args.length - 3), args[args.length - 1]);
                }
                if (args.length == 7) {
                    return filter(this.configService.knownWorlds().stream().toList(), args[6]);
                }
                if (CMD_PLACE.equals(action) && args.length == 8) {
                    return filter(FACINGS, args[7]);
                }
            }
            if (CMD_GET.equals(action) && args.length == 3) {
                final List<String> blockIds = new ArrayList<>(this.runtimeService.blockIds());
                blockIds.add(GET_REMOVER_TOKEN);
                return filter(blockIds, args[2]);
            }
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

    private Double parseCoordinate(final String raw, final CommandSender sender, final String name, final @Nullable Location origin) {
        return parseDouble(raw, sender, name);
    }

    private @Nullable Location resolveSenderLocation(final CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getLocation();
        }
        return null;
    }

    private @Nullable World resolveWorld(final CommandSender sender, final @Nullable String worldName) {
        if (worldName != null) {
            final World world = Bukkit.getWorld(worldName);
            if (world == null) {
                send(sender, this.configService.messageComponent("commands.world_not_found", "World not found: {world}", java.util.Map.of(KEY_WORLD, worldName)));
            }
            return world;
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        send(sender, Component.text("World is required for console usage."));
        return null;
    }

    private @Nullable String resolveFacing(final CommandSender sender, final @Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            if (sender instanceof Player player) {
                return yawToFacing(player.getLocation().getYaw());
            }
            return CMD_NORTH;
        }
        final String facing = raw.toLowerCase(Locale.ROOT);
        if (!FACINGS.contains(facing)) {
            send(sender, this.configService.messageComponent("commands.place.invalid_facing", "Invalid facing: {facing} (use north, south, east, west)", java.util.Map.of("{facing}", facing)));
            return null;
        }
        return facing;
    }

    private String yawToFacing(final float yaw) {
        final float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 45.0F && normalized < 135.0F) {
            return "west";
        }
        if (normalized >= 135.0F && normalized < 225.0F) {
            return CMD_NORTH;
        }
        if (normalized >= 225.0F && normalized < 315.0F) {
            return "east";
        }
        return "south";
    }

    private Component buildPagination(final String commandPrefix, final int currentPage, final int totalPages) {
        final Component prev;
        if (currentPage > 1) {
            prev = TextColor.toComponent("&8[&a<-&8]")
                .clickEvent(ClickEvent.runCommand(commandPrefix + (currentPage - 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Previous page")));
        } else {
            prev = TextColor.toComponent("&8[&7<-&8]");
        }

        final Component pageInfo = TextColor.toComponent(" &8[&e" + currentPage + "&8] ");

        final Component next;
        if (currentPage < totalPages) {
            next = TextColor.toComponent("&8[&a->&8]")
                .clickEvent(ClickEvent.runCommand(commandPrefix + (currentPage + 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Next page")));
        } else {
            next = TextColor.toComponent("&8[&7->&8]");
        }

        return prev.append(pageInfo).append(next);
    }

    private Component buildListEntry(
            final CommandSender sender,
            final Component base,
            final String world,
            final double x,
            final double y,
            final double z,
            final String removeCommand
    ) {
        final Component teleport = TextColor.toComponent(" &8[&aTeleport&8]")
            .clickEvent(ClickEvent.runCommand(buildTeleportCommand(world, x, y, z)))
            .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));
        final Component remove = TextColor.toComponent(" &8[&cRemove&8]")
            .clickEvent(ClickEvent.runCommand(removeCommand))
            .hoverEvent(HoverEvent.showText(Component.text("Click to remove")));
        if (!(sender instanceof Player)) {
            return base.append(teleport).append(remove);
        }
        return base.append(teleport).append(remove);
    }

    private String buildTeleportCommand(final String world, final double x, final double y, final double z) {
        final String dimension = Bukkit.getWorld(world) != null ? Bukkit.getWorld(world).getKey().toString() : world;
        final double offsetX = x + 2.0D;
        final double offsetY = y + 1.0D;
        final double offsetZ = z;
        return "/execute in " + dimension + " run tp @s " + formatCoord(offsetX) + " " + formatCoord(offsetY) + " " + formatCoord(offsetZ);
    }

    private String formatCoord(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatExactCoord(final double value) {
        return Double.toString(value);
    }

    private String formatListCoord(final double value) {
        return String.valueOf(Math.round(value));
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

    private List<String> suggestCoordinate(final CommandSender sender, final int axisIndex) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        final Location loc = player.getLocation();
        final double value = switch (axisIndex) {
            case 1 -> loc.getX();
            case 2 -> loc.getY();
            case 3 -> loc.getZ();
            default -> 0.0D;
        };
        final String coord = formatCoord(value);
        return List.of(coord, String.valueOf(Math.round(value)));
    }
}
