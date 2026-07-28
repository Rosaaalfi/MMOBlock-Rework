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
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.PlaceResult;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public final class BlockCommand implements SubCommand {

    private static final String GET_REMOVER_TOKEN = "remover";
    private static final List<String> ACTIONS = List.of("place", "remove", "get", "list");

    private final CommandContext ctx;

    public BlockCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 2) {
            this.ctx.sendMessage(sender, "commands.block.usage",
                    "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]");
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "place" -> handlePlace(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender, args);
            default -> {
                this.ctx.sendMessage(sender, "commands.block.usage",
                        "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]");
                yield true;
            }
        };
    }

    private boolean handlePlace(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 8) {
            this.ctx.sendMessage(sender, "commands.block.place.usage",
                    "Usage: /mmoblock block place <blockId> x y z [world] [facing]");
            return true;
        }
        final String blockId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5, this.ctx);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, this.ctx);
        if (world == null) return true;

        final String facing = CommandArgs.resolveFacing(sender, args.length >= 8 ? args[7] : null, this.ctx);
        if (facing == null) return true;

        final PlaceResult result = this.ctx.runtimeService().place(blockId, world, xyz[0], xyz[1], xyz[2], facing);
        if (!result.success()) {
            this.ctx.sendMessage(sender, "commands.place.failed", "Failed to place block: {reason}",
                    Map.of("{reason}", result.message()));
            return true;
        }
        this.ctx.sendMessage(sender, "commands.place.success", "Placed {id} at {world} {x} {y} {z}",
                Map.of("{id}", result.placedBlock().type(), "{world}", world.getName(),
                        "{x}", String.valueOf(xyz[0]), "{y}", String.valueOf(xyz[1]),
                        "{z}", String.valueOf(xyz[2])));
        return true;
    }

    private boolean handleRemove(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 7) {
            this.ctx.sendMessage(sender, "commands.block.remove.usage",
                    "Usage: /mmoblock block remove <blockId> x y z [world]");
            return true;
        }
        final String blockId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5, this.ctx);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, this.ctx);
        if (world == null) return true;

        final boolean removed = this.ctx.runtimeService().remove(blockId, world, xyz[0], xyz[1], xyz[2]);
        if (!removed) {
            this.ctx.sendMessage(sender, "commands.remove.not_found", "Block not found at that location.");
            return true;
        }
        this.ctx.sendMessage(sender, "commands.remove.success", "Removed {id} at {world} {x} {y} {z}",
                Map.of("{id}", blockId, "{world}", world.getName(),
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
            this.ctx.sendMessage(sender, "commands.block.get.usage",
                    "Usage: /mmoblock block get <blockId|remover>");
            return true;
        }
        final String token = args[2];
        if (GET_REMOVER_TOKEN.equalsIgnoreCase(token)) {
            final ItemStack item = this.ctx.customItemUtil().createBlockRemover();
            player.getInventory().addItem(item);
            return true;
        }
        final var definition = this.ctx.configService().findBlock(token);
        if (definition == null) {
            this.ctx.sendMessage(sender, "commands.block.not_found", "Block not found: {token}",
                    Map.of("{token}", token));
            return true;
        }
        final ItemStack item = this.ctx.customItemUtil().createBlockItem(definition);
        if (item == null) {
            this.ctx.sendMessage(sender, "commands.block.no_item", "Block item is not configured for: {token}",
                    Map.of("{token}", token));
            return true;
        }
        player.getInventory().addItem(item);
        return true;
    }

    private boolean handleList(@NotNull final CommandSender sender, @NotNull final String[] args) {
        final List<PlacedBlockModel> blocks = new ArrayList<>(this.ctx.runtimeService().placedBlocks());
        if (blocks.isEmpty()) {
            this.ctx.sendMessage(sender, "commands.block.list_empty", "&eNo blocks placed.");
            return true;
        }
        blocks.sort(this::compareBlock);

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
                if (page < 1) page = 1;
            } catch (final NumberFormatException ignored) {
            }
        }

        final int[] pagination = CommandArgs.paginate(blocks.size(), page, 5);
        if (pagination == null) return true;
        final int fromIndex = pagination[0], toIndex = pagination[1];
        final int currentPage = pagination[2], totalPages = pagination[3];

        final List<PlacedBlockModel> pageBlocks = blocks.subList(fromIndex, toIndex);
        this.ctx.sendMessage(sender, "commands.block.list_header", "&eBlock List:");

        for (final PlacedBlockModel block : pageBlocks) {
            final String line = "&e- &8[&a" + block.type() + "&8]"
                    + " &8[&4" + CommandArgs.formatListCoord(block.x()) + "&8]"
                    + " &8[&a" + CommandArgs.formatListCoord(block.y()) + "&8]"
                    + " &8[&9" + CommandArgs.formatListCoord(block.z()) + "&8]"
                    + " &8[&e" + block.world() + "&8]";
            final Component base = TextColor.toComponent(line);
            final String removeCommand = "/mmoblock block remove " + block.type() + " "
                    + CommandArgs.formatExactCoord(block.x()) + " "
                    + CommandArgs.formatExactCoord(block.y()) + " "
                    + CommandArgs.formatExactCoord(block.z()) + " " + block.world();
            sender.sendMessage(CommandArgs.buildListEntry(base, block.world(), block.x(), block.y(), block.z(), removeCommand));
        }

        if (totalPages > 1) {
            sender.sendMessage(CommandArgs.buildPagination("/mmoblock block list ", currentPage, totalPages));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 2) return CommandArgs.filter(ACTIONS, args[1]);

        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("get") && args.length == 3) {
            final List<String> ids = new ArrayList<>(this.ctx.runtimeService().blockIds());
            ids.add(GET_REMOVER_TOKEN);
            return CommandArgs.filter(ids, args[2]);
        }

        if (action.equals("place") || action.equals("remove")) {
            if (args.length == 3) return CommandArgs.filter(new ArrayList<>(this.ctx.runtimeService().blockIds()), args[2]);
            if (args.length >= 4 && args.length <= 6) return CommandArgs.suggestCoordinate(sender, args.length - 3);
            if (args.length == 7) return CommandArgs.filter(this.ctx.configService().knownWorlds(), args[6]);
            if (action.equals("place") && args.length == 8) return CommandArgs.filter(CommandArgs.FACINGS, args[7]);
        }

        return List.of();
    }

    private int compareBlock(final PlacedBlockModel a, final PlacedBlockModel b) {
        final int w = a.world().compareToIgnoreCase(b.world());
        if (w != 0) return w;
        final int x = Double.compare(a.x(), b.x());
        if (x != 0) return x;
        final int y = Double.compare(a.y(), b.y());
        if (y != 0) return y;
        return Double.compare(a.z(), b.z());
    }
}
