package me.chyxelmc.mmoblock.command.sub;
import me.chyxelmc.mmoblock.runtime.block.PlaceResult;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /mmoblock block} command branch: place, remove, get, list.
 */
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
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.block.usage",
                    "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]"
            ));
            return true;
        }
        final String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "place" -> handlePlace(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "get" -> handleGet(sender, args);
            case "list" -> handleList(sender, args);
            default -> {
                sender.sendMessage(ctx.configService().messageComponent(
                        "commands.block.usage",
                        "Usage: /mmoblock block <place|remove|get|list> <blockId> x y z [world] [facing]"
                ));
                yield true;
            }
        };
    }

    // -------------------------------------------------------------
    // Place
    // -------------------------------------------------------------

    private boolean handlePlace(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 8) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.block.place.usage",
                    "Usage: /mmoblock block place <blockId> x y z [world] [facing]"
            ));
            return true;
        }
        final String blockId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, ctx.configService());
        if (world == null) return true;

        final String facing = CommandArgs.resolveFacing(sender, args.length >= 8 ? args[7] : null);
        if (facing == null) return true;

        final PlaceResult result = ctx.runtimeService().place(blockId, world, xyz[0], xyz[1], xyz[2], facing);
        if (!result.success()) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.place.failed",
                    "Failed to place block: {reason}",
                    java.util.Map.of("{reason}", result.message())
            ));
            return true;
        }
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.place.success",
                "Placed {id} at {world} {x} {y} {z}",
                java.util.Map.of(
                        "{id}", result.placedBlock().type(),
                        "{world}", world.getName(),
                        "{x}", String.valueOf(xyz[0]),
                        "{y}", String.valueOf(xyz[1]),
                        "{z}", String.valueOf(xyz[2])
                )
        ));
        return true;
    }

    // -------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------

    private boolean handleRemove(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 6 || args.length > 7) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.block.remove.usage",
                    "Usage: /mmoblock block remove <blockId> x y z [world]"
            ));
            return true;
        }
        final String blockId = args[2];
        final double[] xyz = CommandArgs.parseXYZ(sender, args, 3, 4, 5);
        if (xyz == null) return true;

        final World world = CommandArgs.resolveWorld(sender, args.length >= 7 ? args[6] : null, ctx.configService());
        if (world == null) return true;

        final boolean removed = ctx.runtimeService().remove(blockId, world, xyz[0], xyz[1], xyz[2]);
        if (!removed) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.remove.not_found",
                    "Block not found at that location."
            ));
            return true;
        }
        sender.sendMessage(ctx.configService().messageComponent(
                "commands.remove.success",
                "Removed {id} at {world} {x} {y} {z}",
                java.util.Map.of(
                        "{id}", blockId,
                        "{world}", world.getName(),
                        "{x}", String.valueOf(xyz[0]),
                        "{y}", String.valueOf(xyz[1]),
                        "{z}", String.valueOf(xyz[2])
                )
        ));
        return true;
    }

    // -------------------------------------------------------------
    // Get
    // -------------------------------------------------------------

    private boolean handleGet(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Player only command."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ctx.configService().messageComponent(
                    "commands.block.get.usage",
                    "Usage: /mmoblock block get <blockId|remover>"
            ));
            return true;
        }
        final String token = args[2];
        if (GET_REMOVER_TOKEN.equalsIgnoreCase(token)) {
            final ItemStack item = ctx.customItemUtil().createBlockRemover();
            player.getInventory().addItem(item);
            return true;
        }
        final var definition = ctx.configService().findBlock(token);
        if (definition == null) {
            sender.sendMessage(Component.text("Block not found: " + token));
            return true;
        }
        final ItemStack item = ctx.customItemUtil().createBlockItem(definition);
        if (item == null) {
            sender.sendMessage(Component.text("Block item is not configured for: " + token));
            return true;
        }
        player.getInventory().addItem(item);
        return true;
    }

    // -------------------------------------------------------------
    // List
    // -------------------------------------------------------------

    private boolean handleList(@NotNull final CommandSender sender, @NotNull final String[] args) {
        final List<PlacedBlockModel> blocks = new ArrayList<>(ctx.runtimeService().placedBlocks());
        if (blocks.isEmpty()) {
            sender.sendMessage(Component.text("No blocks placed."));
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
        final int fromIndex = pagination[0];
        final int toIndex = pagination[1];
        final int currentPage = pagination[2];
        final int totalPages = pagination[3];

        final List<PlacedBlockModel> pageBlocks = blocks.subList(fromIndex, toIndex);
        sender.sendMessage(TextColor.toComponent("&eBlock List:"));

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

    // -------------------------------------------------------------
    // Tab completion
    // -------------------------------------------------------------

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        // args[0] = "block", args[1] = action, args[2+] = parameters
        if (args.length == 2) {
            return CommandArgs.filter(ACTIONS, args[1]);
        }

        final String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("get")) {
            if (args.length == 3) {
                final List<String> blockIds = new ArrayList<>(ctx.runtimeService().blockIds());
                blockIds.add(GET_REMOVER_TOKEN);
                return CommandArgs.filter(blockIds, args[2]);
            }
        }

        if (action.equals("place") || action.equals("remove")) {
            if (args.length == 3) {
                return CommandArgs.filter(new ArrayList<>(ctx.runtimeService().blockIds()), args[2]);
            }
            if (args.length >= 4 && args.length <= 6) {
                return CommandArgs.suggestCoordinate(sender, args.length - 3);
            }
            if (args.length == 7) {
                return CommandArgs.filter(ctx.configService().knownWorlds(), args[6]);
            }
            if (action.equals("place") && args.length == 8) {
                return CommandArgs.filter(CommandArgs.FACINGS, args[7]);
            }
        }

        if (action.equals("list") && args.length == 3) {
            return List.of(); // Page number — no completions needed
        }

        return List.of();
    }

    // -------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------

    private int compareBlock(final PlacedBlockModel a, final PlacedBlockModel b) {
        final int world = a.world().compareToIgnoreCase(b.world());
        if (world != 0) return world;
        final int x = Double.compare(a.x(), b.x());
        if (x != 0) return x;
        final int y = Double.compare(a.y(), b.y());
        if (y != 0) return y;
        return Double.compare(a.z(), b.z());
    }

}
