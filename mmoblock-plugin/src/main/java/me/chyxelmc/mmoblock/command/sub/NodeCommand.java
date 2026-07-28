package me.chyxelmc.mmoblock.command.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.model.PlacedNodeModel;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public final class NodeCommand implements SubCommand {

    private static final String GET_REMOVER_TOKEN = "remover";
    private static final List<String> ACTIONS = List.of("place", "remove", "get", "list");

    private final CommandContext ctx;

    public NodeCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 2) {
            this.ctx.sendMessage(sender, "commands.node.usage",
                    "Usage: /mmoblock node <place|remove|get|list> <nodeId> x y z [world] [facing]");
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "place" -> handlePlace(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender, args);
            default -> {
                this.ctx.sendMessage(sender, "commands.node.usage",
                        "Usage: /mmoblock node <place|remove|get|list> <nodeId> x y z [world] [facing]");
                yield true;
            }
        };
    }

    private boolean handlePlace(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 8) {
            this.ctx.sendMessage(sender, "commands.node.place.usage",
                    "Usage: /mmoblock node place <nodeId> x y z [world] [facing]");
            return true;
        }
        if (this.ctx.nodeRuntimeService() == null) {
            this.ctx.sendMessage(sender, "commands.node_runtime_unavailable", "&cNode runtime not available.");
            return true;
        }
        final String nodeId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5, this.ctx);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, this.ctx);
        if (world == null) return true;

        if (args.length >= 8) {
            final String facing = CommandArgs.resolveFacing(sender, args[7], this.ctx);
            if (facing == null) return true;
        }

        final NodeRuntimeService.PlaceResult result = this.ctx.nodeRuntimeService().placeNode(nodeId, world, xyz[0], xyz[1], xyz[2], true);
        if (!result.success()) {
            this.ctx.sendMessage(sender, "commands.nodes.place.failed", "Failed to place node: {reason}",
                    Map.of("{reason}", result.message()));
            return true;
        }
        this.ctx.sendMessage(sender, "commands.nodes.place.success", "Placed node {id} at {world} {x} {y} {z}",
                Map.of("{id}", nodeId, "{world}", world.getName(),
                        "{x}", String.valueOf(xyz[0]), "{y}", String.valueOf(xyz[1]),
                        "{z}", String.valueOf(xyz[2])));
        return true;
    }

    private boolean handleRemove(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 7) {
            this.ctx.sendMessage(sender, "commands.node.remove.usage",
                    "Usage: /mmoblock node remove <nodeId> x y z [world]");
            return true;
        }
        if (this.ctx.nodeRuntimeService() == null) {
            this.ctx.sendMessage(sender, "commands.node_runtime_unavailable", "&cNode runtime not available.");
            return true;
        }
        final String nodeId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5, this.ctx);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, this.ctx);
        if (world == null) return true;

        final boolean removed = this.ctx.nodeRuntimeService().removeNode(nodeId, world, xyz[0], xyz[1], xyz[2]);
        if (!removed) {
            this.ctx.sendMessage(sender, "commands.nodes.remove.not_found", "Node not found at that location.");
            return true;
        }
        this.ctx.sendMessage(sender, "commands.nodes.remove.success", "Removed node {id} at {world} {x} {y} {z}",
                Map.of("{id}", nodeId, "{world}", world.getName(),
                        "{x}", String.valueOf(xyz[0]), "{y}", String.valueOf(xyz[1]),
                        "{z}", String.valueOf(xyz[2])));
        return true;
    }

    private boolean handleGet(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            this.ctx.sendMessage(sender, "commands.player_only", "&cPlayer only command.");
            return true;
        }
        if (args.length < 3) {
            this.ctx.sendMessage(sender, "commands.node.get.usage",
                    "Usage: /mmoblock node get <nodeId|remover>");
            return true;
        }
        final String token = args[2];
        if (GET_REMOVER_TOKEN.equalsIgnoreCase(token)) {
            if (this.ctx.nodeRuntimeService() == null) {
                this.ctx.sendMessage(sender, "commands.node_runtime_unavailable", "&cNode runtime not available.");
                return true;
            }
            final ItemStack item = this.ctx.customItemUtil().createNodeRemover();
            player.getInventory().addItem(item);
            return true;
        }
        final var definition = this.ctx.nodeConfigService().findNode(token);
        if (definition == null) {
            this.ctx.sendMessage(sender, "commands.node.not_found", "Node not found: {token}", Map.of("{token}", token));
            return true;
        }
        final ItemStack item = this.ctx.customItemUtil().createNodeItem(definition);
        if (item == null) {
            this.ctx.sendMessage(sender, "commands.node.no_item", "Node item is not configured for: {token}", Map.of("{token}", token));
            return true;
        }
        player.getInventory().addItem(item);
        return true;
    }

    private boolean handleList(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (this.ctx.nodeRuntimeService() == null) {
            this.ctx.sendMessage(sender, "commands.node_runtime_unavailable", "&cNode runtime not available.");
            return true;
        }
        final List<PlacedNodeModel> nodes = new ArrayList<>(this.ctx.nodeRuntimeService().placedNodes());
        if (nodes.isEmpty()) {
            this.ctx.sendMessage(sender, "commands.node.list_empty", "&eNo nodes placed.");
            return true;
        }
        nodes.sort(this::compareNode);

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (final NumberFormatException ignored) {}
        }

        final int[] pagination = CommandArgs.paginate(nodes.size(), page, 5);
        if (pagination == null) return true;
        final int fromIndex = pagination[0], toIndex = pagination[1];
        final int currentPage = pagination[2], totalPages = pagination[3];

        final List<PlacedNodeModel> pageNodes = nodes.subList(fromIndex, toIndex);
        this.ctx.sendMessage(sender, "commands.node.list_header", "&eNode List:");

        for (final PlacedNodeModel node : pageNodes) {
            final String line = "&e- &8[&a" + node.nodeId() + "&8]"
                    + " &8[&4" + CommandArgs.formatListCoord(node.x()) + "&8]"
                    + " &8[&a" + CommandArgs.formatListCoord(node.y()) + "&8]"
                    + " &8[&9" + CommandArgs.formatListCoord(node.z()) + "&8]"
                    + " &8[&e" + node.world() + "&8]";
            final Component base = TextColor.toComponent(line);
            final String removeCommand = "/mmoblock node remove " + node.nodeId() + " "
                    + CommandArgs.formatExactCoord(node.x()) + " "
                    + CommandArgs.formatExactCoord(node.y()) + " "
                    + CommandArgs.formatExactCoord(node.z()) + " " + node.world();
            sender.sendMessage(CommandArgs.buildListEntry(base, node.world(), node.x(), node.y(), node.z(), removeCommand));
        }

        if (totalPages > 1) {
            sender.sendMessage(CommandArgs.buildPagination("/mmoblock node list ", currentPage, totalPages));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 2) return CommandArgs.filter(ACTIONS, args[1]);

        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("get") && args.length == 3) {
            final List<String> ids = this.ctx.nodeRuntimeService() != null ? this.ctx.nodeRuntimeService().nodeIds() : List.of();
            final List<String> tokens = new ArrayList<>(ids);
            tokens.add(GET_REMOVER_TOKEN);
            return CommandArgs.filter(tokens, args[2]);
        }

        if (action.equals("place") || action.equals("remove")) {
            if (args.length == 3) {
                final List<String> ids = this.ctx.nodeRuntimeService() != null ? this.ctx.nodeRuntimeService().nodeIds() : List.of();
                return CommandArgs.filter(ids, args[2]);
            }
            if (args.length >= 4 && args.length <= 6) return CommandArgs.suggestCoordinate(sender, args.length - 3);
            if (args.length == 7) return CommandArgs.filter(this.ctx.configService().knownWorlds(), args[6]);
            if (action.equals("place") && args.length == 8) return CommandArgs.filter(CommandArgs.FACINGS, args[7]);
        }

        return List.of();
    }

    private int compareNode(final PlacedNodeModel a, final PlacedNodeModel b) {
        final int id = a.nodeId().compareToIgnoreCase(b.nodeId());
        if (id != 0) return id;
        final int w = a.world().compareToIgnoreCase(b.world());
        if (w != 0) return w;
        final int x = Double.compare(a.x(), b.x());
        if (x != 0) return x;
        final int y = Double.compare(a.y(), b.y());
        if (y != 0) return y;
        return Double.compare(a.z(), b.z());
    }
}
