package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static utility methods for parsing common command arguments (coordinates,
 * worlds, facings, player selectors) and building common UI components
 * (pagination, list entries).
 * <p>
 * Extracted from {@link MMOBlockCommand} to centralize argument parsing
 * logic and make it reusable across all {@link SubCommand} implementations.
 * </p>
 */
public final class CommandArgs {

    /** Cardinal directions for facing arguments. */
    public static final List<String> FACINGS = List.of("north", "south", "east", "west");
    private static final String KEY_WORLD = "{world}";

    private CommandArgs() {
    }

    // -------------------------------------------------------------
    // Coordinate parsing
    // -------------------------------------------------------------

    /**
     * Parse a double value from a raw string argument.
     *
     * @param raw    the raw string
     * @param sender the sender (for error messages)
     * @param name   the coordinate name (x, y, z)
     * @return the parsed double, or null if parsing failed
     */
    @Nullable
    public static Double parseDouble(@NotNull final String raw, @NotNull final CommandSender sender, @NotNull final String name) {
        return parseDouble(raw, sender, name, null);
    }

    @Nullable
    public static Double parseDouble(@NotNull final String raw, @NotNull final CommandSender sender,
                                      @NotNull final String name,
                                      @Nullable final CommandContext ctx) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException exception) {
            if (ctx != null) {
                ctx.sendMessage(sender, "commands.invalid_coordinate", "Invalid {name}: {value}",
                        Map.of("{name}", name, "{value}", raw));
            } else {
                sender.sendMessage(Component.text("Invalid " + name + ": " + raw));
            }
            return null;
        }
    }

    /**
     * Parse x, y, z coordinates from successive argument positions.
     *
     * @param sender the command sender
     * @param args   the full argument array
     * @param xArg   the index of the x-coordinate argument
     * @param yArg   the index of the y-coordinate argument
     * @param zArg   the index of the z-coordinate argument
     * @return a double array [x, y, z], or null if any parse failed
     */
    @Nullable
    public static double[] parseXYZ(
            @NotNull final CommandSender sender,
            @NotNull final String[] args,
            final int xArg,
            final int yArg,
            final int zArg
    ) {
        return parseXYZ(sender, args, xArg, yArg, zArg, null);
    }

    @Nullable
    public static double[] parseXYZ(
            @NotNull final CommandSender sender,
            @NotNull final String[] args,
            final int xArg, final int yArg, final int zArg,
            @Nullable final CommandContext ctx
    ) {
        final Double x = parseDouble(args[xArg], sender, "x", ctx);
        final Double y = parseDouble(args[yArg], sender, "y", ctx);
        final Double z = parseDouble(args[zArg], sender, "z", ctx);
        if (x == null || y == null || z == null) return null;
        return new double[]{x, y, z};
    }

    // -------------------------------------------------------------
    // World resolution
    // -------------------------------------------------------------

    /**
     * Resolve a world from an optional argument or the sender's current world.
     *
     * @param sender    the command sender
     * @param worldName the world name argument (may be null)
     * @param configService  the config service for world-not-found messages
     * @return the resolved world, or null if resolution failed
     */
    @Nullable
    public static World resolveWorld(
            @NotNull final CommandSender sender,
            @Nullable final String worldName,
            @NotNull final me.chyxelmc.mmoblock.config.BlockConfigLoader configService
    ) {
        return resolveWorld(sender, worldName, (CommandContext) null);
    }

    @Nullable
    public static World resolveWorld(
            @NotNull final CommandSender sender,
            @Nullable final String worldName,
            @Nullable final CommandContext ctx
    ) {
        if (worldName != null) {
            final World world = Bukkit.getWorld(worldName);
            if (world == null) {
                if (ctx != null) {
                    ctx.sendMessage(sender, "commands.world_not_found", "World not found: {world}",
                            Map.of(KEY_WORLD, worldName));
                } else {
                    sender.sendMessage(Component.text("World not found: " + worldName));
                }
            }
            return world;
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        if (ctx != null) {
            ctx.sendMessage(sender, "commands.world_required", "&cWorld is required for console usage.");
        } else {
            sender.sendMessage(Component.text("World is required for console usage."));
        }
        return null;
    }

    // -------------------------------------------------------------
    // Facing resolution
    // -------------------------------------------------------------

    /**
     * Resolve a facing direction from an optional argument or the sender's yaw.
     *
     * @param sender the command sender
     * @param raw    the facing argument (may be null)
     * @return the resolved facing, or null if invalid
     */
    @Nullable
    public static String resolveFacing(@NotNull final CommandSender sender, @Nullable final String raw) {
        return resolveFacing(sender, raw, null);
    }

    @Nullable
    public static String resolveFacing(@NotNull final CommandSender sender, @Nullable final String raw,
                                        @Nullable final CommandContext ctx) {
        if (raw == null || raw.isBlank()) {
            if (sender instanceof Player player) {
                return yawToFacing(player.getLocation().getYaw());
            }
            return "north";
        }
        final String facing = raw.toLowerCase(Locale.ROOT);
        if (!FACINGS.contains(facing)) {
            if (ctx != null) {
                ctx.sendMessage(sender, "commands.invalid_facing",
                        "&cInvalid facing: {facing} (use north, south, east, west)",
                        Map.of("{facing}", facing));
            } else {
                sender.sendMessage(Component.text("Invalid facing: " + facing + " (use north, south, east, west)"));
            }
            return null;
        }
        return facing;
    }

    /**
     * Convert a yaw angle to a cardinal facing direction.
     */
    public static String yawToFacing(final float yaw) {
        final float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 45.0F && normalized < 135.0F) {
            return "west";
        }
        if (normalized >= 135.0F && normalized < 225.0F) {
            return "north";
        }
        if (normalized >= 225.0F && normalized < 315.0F) {
            return "east";
        }
        return "south";
    }

    // -------------------------------------------------------------
    // Player resolution
    // -------------------------------------------------------------

    /**
     * Resolve a player from a selector argument.
     *
     * @param sender    the command sender
     * @param targetArg the target argument (player name, @s, me)
     * @return the resolved player, or null if resolution failed
     */
    @Nullable
    public static Player resolvePlayer(@NotNull final CommandSender sender, @NotNull final String targetArg) {
        if ("@s".equalsIgnoreCase(targetArg) || "me".equalsIgnoreCase(targetArg)) {
            if (sender instanceof Player player) {
                return player;
            }
            return null;
        }
        return Bukkit.getPlayerExact(targetArg);
    }

    // -------------------------------------------------------------
    // Pagination UI
    // -------------------------------------------------------------

    /**
     * Build a clickable pagination component with previous/next arrows.
     *
     * @param commandPrefix the command path prefix (e.g. "/mmoblock block list ")
     * @param currentPage   the current page number
     * @param totalPages    the total number of pages
     * @return the pagination component
     */
    public static Component buildPagination(
            final String commandPrefix,
            final int currentPage,
            final int totalPages
    ) {
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

    /**
     * Build a clickable list entry with teleport and remove actions.
     */
    public static Component buildListEntry(
            @NotNull final Component base,
            @NotNull final String world,
            final double x,
            final double y,
            final double z,
            @NotNull final String removeCommand
    ) {
        final String teleportCommand = buildTeleportCommand(world, x, y, z);
        final Component teleport = TextColor.toComponent(" &8[&aTeleport&8]")
                .clickEvent(ClickEvent.runCommand(teleportCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));
        final Component remove = TextColor.toComponent(" &8[&cRemove&8]")
                .clickEvent(ClickEvent.runCommand(removeCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to remove")));
        return base.append(teleport).append(remove);
    }

    // -------------------------------------------------------------
    // Coordinate suggestion
    // -------------------------------------------------------------

    /**
     * Suggest the sender's current coordinate for a given axis index.
     *
     * @param sender    the command sender
     * @param axisIndex 1=x, 2=y, 3=z
     * @return list containing the formatted coordinate and its rounded value
     */
    @NotNull
    public static List<String> suggestCoordinate(@NotNull final CommandSender sender, final int axisIndex) {
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

    /**
     * Format a coordinate to 2 decimal places.
     */
    public static String formatCoord(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Format a coordinate as an exact string (no rounding).
     */
    public static String formatExactCoord(final double value) {
        return Double.toString(value);
    }

    /**
     * Compute a teleport command for a given location.
     */
    private static String buildTeleportCommand(final String world, final double x, final double y, final double z) {
        final String dimension = Bukkit.getWorld(world) != null ? Bukkit.getWorld(world).getKey().toString() : world;
        return "/execute in " + dimension + " run tp @s "
                + formatCoord(x + 2.0D) + " "
                + formatCoord(y + 1.0D) + " "
                + formatCoord(z);
    }

    /**
     * Format a coordinate value for display in lists (rounded to nearest integer).
     */
    public static String formatListCoord(final double value) {
        return String.valueOf(Math.round(value));
    }

    /**
     * Filter a collection of strings by a lowercase prefix match.
     */
    @NotNull
    public static List<String> filter(@NotNull final Collection<String> values, @NotNull final String input) {
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }

    /**
     * Compute pagination indices for a list of items.
     *
     * @param listSize the total number of items
     * @param page     the page number (1-based)
     * @param pageSize items per page
     * @return int array: [fromIndex, toIndex, maxPage], or null if list is empty
     */
    @Nullable
    public static int[] paginate(final int listSize, final int page, final int pageSize) {
        if (listSize <= 0) {
            return null;
        }
        final int maxPage = (int) Math.ceil((double) listSize / pageSize);
        final int clampedPage = Math.max(1, Math.min(page, maxPage));
        final int fromIndex = (clampedPage - 1) * pageSize;
        final int toIndex = Math.min(fromIndex + pageSize, listSize);
        return new int[]{fromIndex, toIndex, clampedPage, maxPage};
    }
}
