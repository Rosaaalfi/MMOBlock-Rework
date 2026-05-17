package me.chyxelmc.mmoblock.runtime;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;
import me.chyxelmc.mmoblock.nmsloader.SchematicData;
import me.chyxelmc.mmoblock.nmsloader.SchematicData.SchematicBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class SchematicService {

    private static final String[] SCHEMATIC_EXTENSIONS = {".schematic", ".schem", ".schematics"};
    private static final String SCHEMATICS_FOLDER = "models" + File.separator + "schematics";
    private static final String[] HORIZONTAL_FACINGS = {"north", "west", "south", "east"};
    private static final String[] ALL_FACINGS = {"north", "west", "south", "east", "up", "down"};

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Map<String, SchematicData> schematicCache = new HashMap<>();
    private final Map<String, List<SchematicBlockEntry>> activeSchematicBlocks = new HashMap<>();

    public SchematicService(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
    }

    public void showSchematic(final String blockUniqueId, final BlockDefinition definition, final World world, final double x, final double y, final double z, final boolean dead) {
        if (definition == null || !definition.schematicsEnabled()) return;

        final String schemFile = dead ? definition.schematicsDeadFile() : definition.schematicsNormalFile();
        if (schemFile == null || schemFile.isBlank()) return;

        final SchematicData data = loadSchematic(schemFile);
        if (data == null) return;

        final String resolvedFacing = resolveFacing(definition.schematicsPlaceFacing());
        final List<String> adjustPos = dead ? definition.schematicsAdjustPosDead() : definition.schematicsAdjustPosNormal();
        final double[] offset = parseAdjustPos(adjustPos);

        final List<SchematicBlock> transformed = transformBlocks(data, resolvedFacing, offset);
        final List<SchematicBlockEntry> entries = new ArrayList<>();

        for (final SchematicBlock block : transformed) {
            final int worldX = (int) Math.floor(x) + block.x();
            final int worldY = (int) Math.floor(y) + block.y();
            final int worldZ = (int) Math.floor(z) + block.z();

            final Material material = Material.matchMaterial(block.materialName());
            if (material == null || !material.isBlock()) continue;

            final Location loc = new Location(world, worldX, worldY, worldZ);
            this.nmsAdapter.showFakeBlock(world, loc, material);

            FakeBlockRegistry.add(world.getName(), worldX, worldY, worldZ, material.name());
            entries.add(new SchematicBlockEntry(worldX, worldY, worldZ, material.name()));
        }

        this.activeSchematicBlocks.put(blockUniqueId, entries);
    }

    public void clearSchematic(final String blockUniqueId, final World world) {
        final List<SchematicBlockEntry> entries = this.activeSchematicBlocks.remove(blockUniqueId);
        if (entries == null) return;

        for (final SchematicBlockEntry entry : entries) {
            final Location loc = new Location(world, entry.x(), entry.y(), entry.z());
            FakeBlockRegistry.remove(world.getName(), entry.x(), entry.y(), entry.z());
            this.nmsAdapter.clearFakeBlock(world, loc);
        }
    }

    public void clearSchematicForPlayer(final String blockUniqueId, final Player player) {
        final List<SchematicBlockEntry> entries = this.activeSchematicBlocks.get(blockUniqueId);
        if (entries == null) return;
        final World world = player.getWorld();
        for (final SchematicBlockEntry entry : entries) {
            final Location loc = new Location(world, entry.x(), entry.y(), entry.z());
            this.nmsAdapter.clearFakeBlock(player, world, loc);
        }
    }

    public void showSchematicForPlayer(final String blockUniqueId, final Player player, final BlockDefinition definition, final World world, final double x, final double y, final double z, final boolean dead) {
        final String schemFile = dead ? definition.schematicsDeadFile() : definition.schematicsNormalFile();
        if (schemFile == null || schemFile.isBlank()) return;

        final SchematicData data = loadSchematic(schemFile);
        if (data == null) return;

        final String resolvedFacing = resolveFacing(definition.schematicsPlaceFacing());
        final List<String> adjustPos = dead ? definition.schematicsAdjustPosDead() : definition.schematicsAdjustPosNormal();
        final double[] offset = parseAdjustPos(adjustPos);

        final List<SchematicBlock> transformed = transformBlocks(data, resolvedFacing, offset);

        for (final SchematicBlock block : transformed) {
            final int worldX = (int) Math.floor(x) + block.x();
            final int worldY = (int) Math.floor(y) + block.y();
            final int worldZ = (int) Math.floor(z) + block.z();

            final Material material = Material.matchMaterial(block.materialName());
            if (material == null || !material.isBlock()) continue;

            final Location loc = new Location(world, worldX, worldY, worldZ);
            this.nmsAdapter.showFakeBlock(player, world, loc, material);
        }
    }

    public void clearAll() {
        for (final Map.Entry<String, List<SchematicBlockEntry>> entry : this.activeSchematicBlocks.entrySet()) {
            final String worldName = entry.getValue().isEmpty() ? null : entry.getValue().get(0).worldName();
            if (worldName == null) continue;
            final World world = this.plugin.getServer().getWorld(worldName);
            if (world == null) continue;
            clearSchematic(entry.getKey(), world);
        }
        this.activeSchematicBlocks.clear();
        this.schematicCache.clear();
    }

    private SchematicData loadSchematic(final String schematicName) {
        if (schematicName == null || schematicName.isBlank()) return null;

        final String cacheKey = schematicName.toLowerCase(java.util.Locale.ROOT);
        SchematicData cached = this.schematicCache.get(cacheKey);
        if (cached != null) return cached;

        final File schematicsDir = new File(this.plugin.getDataFolder(), SCHEMATICS_FOLDER);
        if (!schematicsDir.exists()) return null;

        // Try each extension
        for (final String ext : SCHEMATIC_EXTENSIONS) {
            final File file = new File(schematicsDir, schematicName + ext);
            if (file.exists() && file.isFile()) {
                cached = this.nmsAdapter.loadSchematic(file.getAbsolutePath());
                if (cached != null) {
                    this.schematicCache.put(cacheKey, cached);
                    return cached;
                }
            }
        }

        // Try with extension already in name
        final File file = new File(schematicsDir, schematicName);
        if (file.exists() && file.isFile()) {
            cached = this.nmsAdapter.loadSchematic(file.getAbsolutePath());
            if (cached != null) {
                this.schematicCache.put(cacheKey, cached);
                return cached;
            }
        }

        return null;
    }

    private static String resolveFacing(final String placeFacing) {
        if (placeFacing == null || placeFacing.isBlank()) return "north";
        final String lower = placeFacing.toLowerCase(java.util.Locale.ROOT);
        return switch (lower) {
            case "randomup" -> HORIZONTAL_FACINGS[ThreadLocalRandom.current().nextInt(HORIZONTAL_FACINGS.length)];
            case "randomdown" -> HORIZONTAL_FACINGS[ThreadLocalRandom.current().nextInt(HORIZONTAL_FACINGS.length)];
            case "random" -> ALL_FACINGS[ThreadLocalRandom.current().nextInt(ALL_FACINGS.length)];
            default -> lower;
        };
    }

    private static double[] parseAdjustPos(final List<String> adjustPos) {
        if (adjustPos == null || adjustPos.isEmpty()) return new double[]{0, 0, 0};
        final String first = adjustPos.get(0);
        if (first == null || first.isBlank()) return new double[]{0, 0, 0};
        final String[] parts = first.split(",");
        if (parts.length < 3) return new double[]{0, 0, 0};
        try {
            return new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())
            };
        } catch (final NumberFormatException e) {
            return new double[]{0, 0, 0};
        }
    }

    private static List<SchematicBlock> transformBlocks(final SchematicData data, final String facing, final double[] offset) {
        final int w = data.width();
        final int h = data.height();
        final int l = data.length();
        final int ox = (int) Math.round(offset[0]);
        final int oy = (int) Math.round(offset[1]);
        final int oz = (int) Math.round(offset[2]);

        final List<SchematicBlock> result = new ArrayList<>(data.blocks().size());
        for (final SchematicBlock block : data.blocks()) {
            int bx = block.x();
            int by = block.y();
            int bz = block.z();

            // Apply rotation based on facing
            switch (facing) {
                case "north" -> {
                    // default orientation, no rotation
                }
                case "south" -> {
                    // 180 degrees around Y
                    final int tmpX = bx;
                    bx = (w - 1) - bx;
                    bz = (l - 1) - bz;
                }
                case "east" -> {
                    // 90 degrees clockwise around Y: (x, z) -> (-z, x)
                    final int tmpX = bx;
                    bx = (l - 1) - bz;
                    bz = tmpX;
                }
                case "west" -> {
                    // 90 degrees counterclockwise around Y: (x, z) -> (z, -x)
                    final int tmpX = bx;
                    bx = bz;
                    bz = (w - 1) - tmpX;
                }
                case "up" -> {
                    // Default (upright), same as north
                }
                case "down" -> {
                    // Flipped vertically
                    by = (h - 1) - by;
                }
            }

            result.add(new SchematicBlock(bx + ox, by + oy, bz + oz, block.materialName()));
        }
        return result;
    }

    private record SchematicBlockEntry(int x, int y, int z, String materialName) {
        private String worldName() {
            return null; // world name is tracked outside
        }
    }
}
