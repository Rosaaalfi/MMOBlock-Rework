package me.chyxelmc.mmoblock.command.sub;

import me.chyxelmc.mmoblock.command.CommandArgs;
import me.chyxelmc.mmoblock.command.CommandContext;
import me.chyxelmc.mmoblock.command.SubCommand;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.PlaceResult;
import me.chyxelmc.mmoblock.utils.InternalPlaceholderResolver;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DebugCommand implements SubCommand {

    private static final List<String> DEBUG_ACTIONS = List.of(
            "placeholder", "extractDefaultAssets",
            "mass-spawn", "mass-remove", "stress-info",
            "chunk-stress", "db-stress"
    );

    private final CommandContext ctx;

    public DebugCommand(final CommandContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 2) {
            showUsage(sender);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "placeholder" -> handlePlaceholder(sender, args);
            case "extractdefaultassets" -> handleExtractDefaultAssets(sender);
            case "mass-spawn" -> handleMassSpawn(sender, args);
            case "mass-remove" -> handleMassRemove(sender, args);
            case "stress-info" -> handleStressInfo(sender);
            case "chunk-stress" -> handleChunkStress(sender, args);
            case "db-stress" -> handleDbStress(sender, args);
            default -> { showUsage(sender); yield true; }
        };
    }

    private void showUsage(final CommandSender sender) {
        sender.sendMessage(TextColor.toComponent("&7"));
        this.ctx.sendMessage(sender, "commands.debug.usage", "&eDebug Commands:");
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug placeholder parse <player> <placeholder>"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug extractDefaultAssets"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug mass-spawn <blockId> <count> [radius] &8- Spawn N blocks in a grid"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug mass-remove [blockId] &8- Remove all (or filtered) blocks"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug stress-info &8- Show server stats (TPS, memory, block/node counts)"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug chunk-stress [radius] &8- Teleport across chunks in a spiral"));
        sender.sendMessage(TextColor.toComponent("&7/mmoblock debug db-stress <count> &8- Place & remove N blocks to test DB throughput"));
    }

    private boolean handleExtractDefaultAssets(@NotNull final CommandSender sender) {
        this.ctx.sendMessage(sender, "commands.debug.extracting", "&7Extracting default assets...");
        this.ctx.configService().extractAllDefaultAssets();
        this.ctx.sendMessage(sender, "commands.debug.extracted",
                "&aDefault assets extracted successfully. Run &7/mmoblock reload&a to apply.");
        return true;
    }

    private boolean handlePlaceholder(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 4 || !"parse".equalsIgnoreCase(args[2])) {
            this.ctx.sendMessage(sender, "commands.debug.placeholder_usage",
                    "Usage: /mmoblock debug placeholder parse <player> <placeholder>");
            return true;
        }

        final Player target = CommandArgs.resolvePlayer(sender, args[3]);
        if (target == null) {
            if ("@s".equalsIgnoreCase(args[3]) || "me".equalsIgnoreCase(args[3])) {
                this.ctx.sendMessage(sender, "commands.debug.console_player_required",
                        "&cConsole must specify a player name.");
            } else {
                this.ctx.sendMessage(sender, "commands.debug.player_not_found",
                        "&cPlayer not found: {player}", Map.of("{player}", args[3]));
            }
            return true;
        }

        final StringBuilder placeholderBuilder = new StringBuilder();
        for (int i = 4; i < args.length; i++) {
            if (placeholderBuilder.length() > 0) placeholderBuilder.append(" ");
            placeholderBuilder.append(args[i]);
        }
        final String placeholderText = placeholderBuilder.toString();
        if (placeholderText.isEmpty()) {
            this.ctx.sendMessage(sender, "commands.debug.placeholder_usage",
                    "Usage: /mmoblock debug placeholder parse me {mmocore_level}");
            return true;
        }

        final String internalResolved = InternalPlaceholderResolver.resolve(target, placeholderText);
        if (!internalResolved.equals(placeholderText)) {
            sender.sendMessage(Component.text(internalResolved));
        }
        return true;
    }

    private boolean handleMassSpawn(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            this.ctx.sendMessage(sender, "commands.player_only", "&cThis command can only be run by a player.");
            return true;
        }
        if (args.length < 4 || args.length > 5) {
            this.ctx.sendMessage(sender, "commands.debug.mass_spawn_usage",
                    "&cUsage: /mmoblock debug mass-spawn <blockId> <count> [radius]");
            return true;
        }

        final String blockId = args[2];
        final int count;
        try {
            count = Integer.parseInt(args[3]);
            if (count < 1 || count > 10000) {
                this.ctx.sendMessage(sender, "commands.debug.count_range",
                        "&cCount must be between 1 and 10000.");
                return true;
            }
        } catch (final NumberFormatException e) {
            this.ctx.sendMessage(sender, "commands.debug.invalid_count",
                    "&cInvalid count: {count}", Map.of("{count}", args[3]));
            return true;
        }

        final int radius = args.length >= 5 ? Math.max(1, Integer.parseInt(args[4])) : 5;
        final Location loc = player.getLocation();
        final World world = loc.getWorld();
        if (world == null) {
            this.ctx.sendMessage(sender, "commands.debug.no_world", "&cCould not determine your world.");
            return true;
        }

        final double centerX = loc.getX(), centerY = loc.getY(), centerZ = loc.getZ();
        final String facing = CommandArgs.resolveFacing(sender, null, this.ctx);
        if (facing == null) return true;

        final int side = (int) Math.ceil(Math.sqrt(count));
        final double spacing = side <= 1 ? 0.0D : (2.0D * radius) / (side - 1);
        final int batchSize = 20;
        int success = 0, failed = 0;

        for (int i = 0; i < count; i++) {
            final int row = i / side, col = i % side;
            final PlaceResult result = this.ctx.runtimeService().place(blockId, world,
                    centerX - radius + col * spacing, centerY, centerZ - radius + row * spacing, facing);
            if (result.success()) success++; else failed++;
            if (i > 0 && i % batchSize == 0) Thread.yield();
        }

        sender.sendMessage(TextColor.toComponent(
                "&aMass-spawn complete. &7Placed: &a" + success + " &7Failed: &c" + failed));
        return true;
    }

    private boolean handleMassRemove(@NotNull final CommandSender sender, @NotNull final String[] args) {
        String filter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        if ("all".equals(filter)) filter = null;

        int removed = 0, skipped = 0;
        for (final PlacedBlockModel block : this.ctx.runtimeService().placedBlocks()) {
            if (filter != null && !block.type().equalsIgnoreCase(filter)) { skipped++; continue; }
            if (this.ctx.runtimeService().removeById(block.uniqueId())) removed++;
        }

        if (filter != null) {
            sender.sendMessage(TextColor.toComponent(
                    "&aMass-remove complete. &7Filter: &e" + filter
                    + " &7Removed: &a" + removed + " &7Skipped: &e" + skipped));
        } else {
            sender.sendMessage(TextColor.toComponent("&aMass-remove complete. &7Removed: &a" + removed));
        }
        return true;
    }

    private boolean handleStressInfo(@NotNull final CommandSender sender) {
        final Runtime rt = Runtime.getRuntime();
        final long totalMem = rt.totalMemory() / (1024L * 1024L);
        final long freeMem = rt.freeMemory() / (1024L * 1024L);
        final long usedMem = totalMem - freeMem;
        final long maxMem = rt.maxMemory() / (1024L * 1024L);

        final double tps;
        {
            double resolved;
            try {
                final double[] tpsArr = Bukkit.getTPS();
                resolved = tpsArr == null || tpsArr.length == 0 ? 20.0D : tpsArr[0];
            } catch (final Throwable ignored) { resolved = 20.0D; }
            tps = resolved;
        }

        final String tickRanges;
        {
            String resolved;
            try {
                final double[] tpsArr = Bukkit.getTPS();
                if (tpsArr != null && tpsArr.length >= 3) {
                    resolved = String.format("&71m: &e%.2f &7| 5m: &e%.2f &7| 15m: &e%.2f",
                            tpsArr[0], tpsArr[1], tpsArr[2]);
                } else { resolved = "&7N/A"; }
            } catch (final Throwable ignored) { resolved = "&7N/A"; }
            tickRanges = resolved;
        }

        final int blockCount = this.ctx.runtimeService().placedBlocks().size();
        final int nodeCount = this.ctx.nodeRuntimeService() != null
                ? this.ctx.nodeRuntimeService().placedNodes().size() : 0;
        final int blockDefCount = this.ctx.runtimeService().blockIds().size();

        this.ctx.sendMessage(sender, "commands.debug.stress_info.header", "&e&l=== MMOBlock Stress Info ===");
        this.ctx.sendMessage(sender, "commands.debug.stress_info.tps", "&7TPS: &e{tps}",
                Map.of("{tps}", String.format("%.2f", tps)));
        sender.sendMessage(TextColor.toComponent(tickRanges));
        this.ctx.sendMessage(sender, "commands.debug.stress_info.memory",
                "&7Memory: &e{used}&7/&e{max} &7MB (&e{free} &7MB free)",
                Map.of("{used}", String.valueOf(usedMem), "{max}", String.valueOf(maxMem),
                       "{free}", String.valueOf(freeMem)));
        this.ctx.sendMessage(sender, "commands.debug.stress_info.blocks",
                "&7Placed Blocks: &e{count}", Map.of("{count}", String.valueOf(blockCount)));
        this.ctx.sendMessage(sender, "commands.debug.stress_info.nodes",
                "&7Placed Nodes: &e{count}", Map.of("{count}", String.valueOf(nodeCount)));
        this.ctx.sendMessage(sender, "commands.debug.stress_info.types",
                "&7Registered Block Types: &e{count}", Map.of("{count}", String.valueOf(blockDefCount)));
        sender.sendMessage(TextColor.toComponent(
                "&7Block IDs: &f" + String.join("&7, &f", this.ctx.runtimeService().blockIds())));
        return true;
    }

    private boolean handleChunkStress(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            this.ctx.sendMessage(sender, "commands.player_only", "&cThis command can only be run by a player.");
            return true;
        }

        final int radius = args.length >= 3 ? Math.max(1, Integer.parseInt(args[2])) : 8;
        final int totalChunks = (2 * radius + 1) * (2 * radius + 1);
        final Location baseLocation = player.getLocation().clone();
        final World world = baseLocation.getWorld();
        if (world == null) {
            this.ctx.sendMessage(sender, "commands.debug.no_world", "&cCould not determine your world.");
            return true;
        }

        this.ctx.sendMessage(sender, "commands.debug.chunk_stress.start",
                "&eStarting chunk stress test... &7{count} chunks in a &e{radius}&7 chunk radius.",
                Map.of("{count}", String.valueOf(totalChunks), "{radius}", String.valueOf(radius)));
        this.ctx.sendMessage(sender, "commands.debug.chunk_stress.spiral",
                "&7You will be teleported in a spiral pattern.");

        final int[][] offsets = generateSpiralOffsets(radius);
        final int[] index = {0};
        final int[] taskIdHolder = {0};

        taskIdHolder[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.ctx.plugin(), () -> {
            if (index[0] >= offsets.length) {
                Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
                this.ctx.sendMessage(sender, "commands.debug.chunk_stress.complete",
                        "&aChunk stress test complete! &7Visited &e{count} &7chunks.",
                        Map.of("{count}", String.valueOf(totalChunks)));
                return;
            }
            final int dx = offsets[index[0]][0], dz = offsets[index[0]][1];
            final int bx = ((int) Math.floor(baseLocation.getX()) >> 4) * 16 + 8 + dx * 16;
            final int bz = ((int) Math.floor(baseLocation.getZ()) >> 4) * 16 + 8 + dz * 16;
            player.teleportAsync(new Location(world, bx + 0.5D, baseLocation.getBlockY(), bz + 0.5D,
                    player.getLocation().getYaw(), player.getLocation().getPitch()));
            index[0]++;
        }, 1L, 2L);

        Bukkit.getScheduler().runTaskLater(this.ctx.plugin(), () -> {
            Bukkit.getScheduler().cancelTask(taskIdHolder[0]);
            this.ctx.sendMessage(sender, "commands.debug.chunk_stress.timeout", "&eChunk stress test timed out.");
        }, 600L);
        return true;
    }

    private boolean handleDbStress(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            this.ctx.sendMessage(sender, "commands.player_only", "&cThis command can only be run by a player.");
            return true;
        }
        if (args.length < 3) {
            this.ctx.sendMessage(sender, "commands.debug.db_stress_usage", "&cUsage: /mmoblock debug db-stress <count>");
            return true;
        }

        final int count;
        try {
            count = Integer.parseInt(args[2]);
            if (count < 1 || count > 1000) {
                this.ctx.sendMessage(sender, "commands.debug.count_range_1000",
                        "&cCount must be between 1 and 1000.");
                return true;
            }
        } catch (final NumberFormatException e) {
            this.ctx.sendMessage(sender, "commands.debug.invalid_count",
                    "&cInvalid count: {count}", Map.of("{count}", args[2]));
            return true;
        }

        final List<String> blockIds = this.ctx.runtimeService().blockIds();
        if (blockIds.isEmpty()) {
            this.ctx.sendMessage(sender, "commands.debug.no_blocks", "&cNo block types configured. Place some blocks first.");
            return true;
        }

        final Location loc = player.getLocation();
        final World world = loc.getWorld();
        if (world == null) {
            this.ctx.sendMessage(sender, "commands.debug.no_world", "&cCould not determine your world.");
            return true;
        }

        final double centerX = loc.getX(), centerY = loc.getY(), centerZ = loc.getZ();
        final String facing = CommandArgs.resolveFacing(sender, null, this.ctx);
        if (facing == null) return true;

        // Phase 1: Place
        sender.sendMessage(TextColor.toComponent("&ePhase 1: Placing &f" + count + " &eblocks..."));
        final long placeStart = System.currentTimeMillis();
        final List<PlacedBlockModel> placed = new ArrayList<>();
        int success = 0;

        for (int i = 0; i < count; i++) {
            final String bid = blockIds.get(i % blockIds.size());
            final PlaceResult result = this.ctx.runtimeService().place(bid, world,
                    centerX + (i % 10) * 3, centerY, centerZ + (i / 10) * 3, facing);
            if (result.success()) { placed.add(result.placedBlock()); success++; }
            if (i > 0 && i % 20 == 0) Thread.yield();
        }
        final long placeEnd = System.currentTimeMillis();
        final long placeTime = placeEnd - placeStart;

        sender.sendMessage(TextColor.toComponent("&aPlaced &e" + success + " &ablocks in &e" + placeTime + "ms"
                + " &7(&e" + String.format("%.1f", (double) placeTime / Math.max(1, success)) + " &7ms/block)"));

        // Phase 2: Remove
        sender.sendMessage(TextColor.toComponent("&ePhase 2: Removing &f" + placed.size() + " &eblocks..."));
        final long removeStart = System.currentTimeMillis();
        int removed = 0;
        for (int i = 0; i < placed.size(); i++) {
            if (this.ctx.runtimeService().removeById(placed.get(i).uniqueId())) removed++;
            if (i > 0 && i % 20 == 0) Thread.yield();
        }
        final long removeEnd = System.currentTimeMillis();
        final long removeTime = removeEnd - removeStart;

        sender.sendMessage(TextColor.toComponent("&aRemoved &e" + removed + " &ablocks in &e" + removeTime + "ms"
                + " &7(&e" + String.format("%.1f", (double) removeTime / Math.max(1, removed)) + " &7ms/block)"));

        sender.sendMessage(TextColor.toComponent("&e&l=== DB Stress Test Results ==="));
        sender.sendMessage(TextColor.toComponent("&7Write: &e" + placeTime + "ms &7(&e"
                + String.format("%.1f", (double) placeTime / Math.max(1, success)) + " ms/block)"));
        sender.sendMessage(TextColor.toComponent("&7Delete: &e" + removeTime + "ms &7(&e"
                + String.format("%.1f", (double) removeTime / Math.max(1, removed)) + " ms/block)"));
        sender.sendMessage(TextColor.toComponent("&7Total: &e" + (placeTime + removeTime) + "ms &7for &e"
                + placed.size() + " &7blocks"));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length == 2) return CommandArgs.filter(DEBUG_ACTIONS, args[1]);
        if (args.length == 3 && "placeholder".equalsIgnoreCase(args[1])) return CommandArgs.filter(List.of("parse"), args[2]);
        if (args.length == 4 && "placeholder".equalsIgnoreCase(args[1]) && "parse".equalsIgnoreCase(args[2])) {
            final List<String> targets = new ArrayList<>(List.of("@s", "me"));
            targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return CommandArgs.filter(targets, args[3]);
        }
        if (args.length == 3 && "mass-spawn".equalsIgnoreCase(args[1])) return CommandArgs.filter(new ArrayList<>(this.ctx.runtimeService().blockIds()), args[2]);
        if (args.length == 3 && "mass-remove".equalsIgnoreCase(args[1])) {
            final List<String> c = new ArrayList<>(this.ctx.runtimeService().blockIds());
            c.add(0, "all"); return CommandArgs.filter(c, args[2]);
        }
        if (args.length == 4 && "mass-spawn".equalsIgnoreCase(args[1])) return CommandArgs.filter(List.of("50", "100", "500", "1000"), args[3]);
        if (args.length == 5 && "mass-spawn".equalsIgnoreCase(args[1])) return CommandArgs.filter(List.of("5", "10", "15", "20"), args[4]);
        if (args.length == 3 && "chunk-stress".equalsIgnoreCase(args[1])) return CommandArgs.filter(List.of("4", "8", "12", "16"), args[2]);
        if (args.length == 3 && "db-stress".equalsIgnoreCase(args[1])) return CommandArgs.filter(List.of("50", "100", "200", "500"), args[2]);
        return List.of();
    }

    private static int[][] generateSpiralOffsets(final int radius) {
        final int size = (2 * radius + 1) * (2 * radius + 1);
        final int[][] result = new int[size][2];
        int idx = 1;
        for (int layer = 1; layer <= radius; layer++) {
            for (int dx = -layer + 1; dx <= layer; dx++) { result[idx][0] = dx; result[idx][1] = -layer; idx++; }
            for (int dz = -layer + 1; dz <= layer; dz++) { result[idx][0] = layer; result[idx][1] = dz; idx++; }
            for (int dx = layer - 1; dx >= -layer; dx--) { result[idx][0] = dx; result[idx][1] = layer; idx++; }
            for (int dz = layer - 1; dz >= -layer; dz--) { result[idx][0] = -layer; result[idx][1] = dz; idx++; }
        }
        return result;
    }
}
