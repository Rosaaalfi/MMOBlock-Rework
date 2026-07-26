package me.chyxelmc.mmoblock.runtime.block;
import me.chyxelmc.mmoblock.runtime.block.RandomLocationContext;

import me.chyxelmc.mmoblock.utils.MMOBlockLogger;

import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.block.BlockStateRegistry;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.util.BoundingBox;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BlockRandomLocationResolver {

    private static final double NODE_RANDOM_MIN_BLOCK_DISTANCE = 1.5D;
    private static final int RANDOM_LOCATION_MAX_ATTEMPTS = 48;
    private static final Set<Material> VEGETATION_MATERIALS = buildVegetationMaterials();

    private final BlockStateRegistry stateRegistry;

    public BlockRandomLocationResolver(final BlockStateRegistry stateRegistry) {
        this.stateRegistry = stateRegistry;
    }

    public Location resolveRandomContextLocation(
            final World world,
            final RandomLocationContext context,
            final UUID excludingBlockId
    ) {
        if (world == null || context == null) {
            return null;
        }

        final int originBlockX = (int) Math.floor(context.originX());
        final int originBlockY = (int) Math.floor(context.originY());
        final int originBlockZ = (int) Math.floor(context.originZ());
        if (!context.enabled() || context.radius() <= 0.0D) {
            // When random location is disabled, try the origin first.
            // If an excludingBlockId is provided (previous block already at origin),
            // spread subsequent blocks around the origin.
            if (excludingBlockId == null) {
                return findSafeBlockLocation(world, originBlockX, originBlockY, originBlockZ, null, context.closest());
            }
            // Try positions with increasing distance from the origin.
            // Skip {0,0} since a block already occupies the exact origin.
            final int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, -1}, {1, -1}, {-1, 1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
                {1, 2}, {1, -2}, {-1, 2}, {-1, -2}
            };
            for (final int[] offset : offsets) {
                final Location loc = findSafeBlockLocation(
                        world,
                        originBlockX + offset[0],
                        originBlockY,
                        originBlockZ + offset[1],
                        excludingBlockId,
                        context.closest()
                );
                if (loc != null) {
                    return loc;
                }
            }
            return null;
        }

        final double radius = Math.max(0.0D, context.radius());
        final double centerDistance = Math.max(0.0D, context.centerDistance());
        for (int attempt = 0; attempt < RANDOM_LOCATION_MAX_ATTEMPTS; attempt++) {
            final double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            final double minDistance = Math.min(radius, centerDistance);
            final double distance = minDistance + (Math.sqrt(ThreadLocalRandom.current().nextDouble()) * Math.max(0.0D, radius - minDistance));
            final int targetBlockX = originBlockX + (int) Math.round(Math.cos(angle) * distance);
            final int targetBlockZ = originBlockZ + (int) Math.round(Math.sin(angle) * distance);
            if (horizontalDistanceSquared(targetBlockX, targetBlockZ, originBlockX, originBlockZ) < centerDistance * centerDistance) {
                continue;
            }

            final Location safe = findSafeBlockLocation(world, targetBlockX, originBlockY, targetBlockZ, excludingBlockId, context.closest());
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    public Location findSafeBlockLocation(
            final World world,
            final int blockX,
            final int baseY,
            final int blockZ,
            final UUID excludingBlockId,
            final boolean requireClosestHorizontalBlock
    ) {
        if (!isOwnedByCurrentRegion(new Location(world, blockX, baseY, blockZ))) {
            return null;
        }
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight();
        final int startY = Math.max(minY, baseY);
        final int topY = maxY - 2;

        // First pass: try normal safe positions (solid ground below)
        Location result = scanNormalUpward(world, blockX, blockZ, minY, startY, topY, excludingBlockId, requireClosestHorizontalBlock);
        if (result != null) return result;

        // Second pass: try water/lava positions (replace water/lava with the block).
        result = scanLiquidDownward(world, blockX, blockZ, minY, startY, excludingBlockId, requireClosestHorizontalBlock);
        if (result != null) return result;

        // Third pass + Fourth pass: retry WITHOUT closest check (fallback for flat terrain)
        if (requireClosestHorizontalBlock) {
            result = scanNormalUpward(world, blockX, blockZ, minY, startY, topY, excludingBlockId, false);
            if (result != null) return result;
            result = scanLiquidDownward(world, blockX, blockZ, minY, startY, excludingBlockId, false);
            if (result != null) return result;
        }

        return null;
    }

    /** Scan upward for normal positions (feet & head passable, solid ground below). */
    private Location scanNormalUpward(
            final World world, final int blockX, final int blockZ,
            final int minY, final int startY, final int topY,
            final UUID excludingBlockId, final boolean requireClosest
    ) {
        for (int y = startY; y <= topY; y++) {
            final Block feet = world.getBlockAt(blockX, y, blockZ);
            final Block head = world.getBlockAt(blockX, y + 1, blockZ);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            final int groundedY = resolveGroundedSpawnY(world, blockX, y, blockZ, minY);
            if (groundedY < 0) continue;
            if (isHemmedIn(world, blockX, groundedY, blockZ)) continue;
            if (requireClosest && !hasHorizontalClosestBlock(world, blockX, groundedY, blockZ)) continue;
            if (isTooCloseToPlacedBlock(world.getName(), blockX, groundedY, blockZ, excludingBlockId)) continue;
            if (!hasBlockingEntityAt(world, blockX, groundedY, blockZ)) {
                return new Location(world, blockX, groundedY, blockZ);
            }
        }
        return null;
    }

    /** Scan downward for water/lava positions (feet is liquid, solid ground below). */
    private Location scanLiquidDownward(
            final World world, final int blockX, final int blockZ,
            final int minY, final int startY,
            final UUID excludingBlockId, final boolean requireClosest
    ) {
        for (int y = startY; y >= minY; y--) {
            final Block feet = world.getBlockAt(blockX, y, blockZ);
            final Block head = world.getBlockAt(blockX, y + 1, blockZ);
            if (!isLiquid(feet) || !head.isPassable()) continue;
            if (y - 1 >= minY) {
                final Block support = world.getBlockAt(blockX, y - 1, blockZ);
                if (support.getType().isAir() || isLiquid(support) || VEGETATION_MATERIALS.contains(support.getType()) || support.getType() == Material.SNOW) {
                    continue;
                }
            } else {
                continue;
            }
            if (isHemmedIn(world, blockX, y, blockZ)) continue;
            if (requireClosest && !hasHorizontalClosestBlock(world, blockX, y, blockZ)) continue;
            if (isTooCloseToPlacedBlock(world.getName(), blockX, y, blockZ, excludingBlockId)) continue;
            if (!hasBlockingEntityAt(world, blockX, y, blockZ)) {
                return new Location(world, blockX, y, blockZ);
            }
        }
        return null;
    }

    public String resolveRandomFacing(final World world, final int blockX, final int spawnY, final int blockZ) {
        final boolean northBlocked = isSolidAt(world, blockX, spawnY, blockZ - 1)
                || isSolidAt(world, blockX, spawnY + 1, blockZ - 1);
        final boolean southBlocked = isSolidAt(world, blockX, spawnY, blockZ + 1)
                || isSolidAt(world, blockX, spawnY + 1, blockZ + 1);
        final boolean eastBlocked = isSolidAt(world, blockX + 1, spawnY, blockZ)
                || isSolidAt(world, blockX + 1, spawnY + 1, blockZ);
        final boolean westBlocked = isSolidAt(world, blockX - 1, spawnY, blockZ)
                || isSolidAt(world, blockX - 1, spawnY + 1, blockZ);

        final java.util.List<String> candidates = new java.util.ArrayList<>();

        if (northBlocked) candidates.add("south");
        if (southBlocked) candidates.add("north");
        if (eastBlocked) candidates.add("west");
        if (westBlocked) candidates.add("east");

        if (candidates.isEmpty()) {
            candidates.add("north");
            candidates.add("south");
            candidates.add("east");
            candidates.add("west");
            candidates.add("up");
            candidates.add("down");
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private int resolveGroundedSpawnY(final World world, final int blockX, final int initialY, final int blockZ, final int minY) {
        if (isGroundSupport(world, blockX, initialY, blockZ)) {
            return initialY;
        }

        final int maxDownAttempts = 3;
        for (int down = 1; down <= maxDownAttempts; down++) {
            final int candidateY = initialY - down;
            if (candidateY < minY) {
                return -1;
            }

            final Block feet = world.getBlockAt(blockX, candidateY, blockZ);
            final Block head = world.getBlockAt(blockX, candidateY + 1, blockZ);
            if (!feet.isPassable() || !head.isPassable()) {
                continue;
            }
            if (isGroundSupport(world, blockX, candidateY, blockZ)) {
                return candidateY;
            }
        }
        return -1;
    }

    private boolean isHemmedIn(final World world, final int blockX, final int spawnY, final int blockZ) {
        final int[][] offsets = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

        int blockedDirections = 0;
        for (final int[] offset : offsets) {
            final Block feetAdj = world.getBlockAt(blockX + offset[0], spawnY + offset[1], blockZ + offset[2]);
            final Block headAdj = world.getBlockAt(blockX + offset[0], spawnY + 1 + offset[1], blockZ + offset[2]);
            final boolean feetBlocked = !feetAdj.getType().isAir() && !feetAdj.isPassable();
            final boolean headBlocked = !headAdj.getType().isAir() && !headAdj.isPassable();
            if (feetBlocked || headBlocked) {
                blockedDirections++;
            }
        }
        return blockedDirections >= 3;
    }

    private static boolean isOwnedByCurrentRegion(final Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        try {
            final java.lang.reflect.Method method = Bukkit.class.getMethod("isOwnedByCurrentRegion", Location.class);
            return Boolean.TRUE.equals(method.invoke(null, location));
        } catch (final NoSuchMethodException ignored) {
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private double horizontalDistanceSquared(final int x, final int z, final int otherX, final int otherZ) {
        final double dx = (x + 0.5D) - (otherX + 0.5D);
        final double dz = (z + 0.5D) - (otherZ + 0.5D);
        return (dx * dx) + (dz * dz);
    }

    private boolean hasHorizontalClosestBlock(final World world, final int blockX, final int spawnY, final int blockZ) {
        final int[][] offsets = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        for (final int[] offset : offsets) {
            if (isSolidAt(world, blockX + offset[0], spawnY, blockZ + offset[1])
                    || isSolidAt(world, blockX + offset[0], spawnY + 1, blockZ + offset[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooCloseToPlacedBlock(
            final String worldName,
            final int blockX,
            final int blockY,
            final int blockZ,
            final UUID excludingBlockId
    ) {
        final double minDistanceSquared = NODE_RANDOM_MIN_BLOCK_DISTANCE * NODE_RANDOM_MIN_BLOCK_DISTANCE;
        final double centerX = blockX + 0.5D;
        final double centerZ = blockZ + 0.5D;
        for (final PlacedBlockModel placedBlock : this.stateRegistry.blocks()) {
            if (excludingBlockId != null && excludingBlockId.equals(placedBlock.uniqueId())) {
                continue;
            }
            if (!worldName.equals(placedBlock.world())) {
                continue;
            }
            if (Math.abs(placedBlock.y() - blockY) > 2.0D) {
                continue;
            }
            final double otherX = Math.floor(placedBlock.x()) + 0.5D;
            final double otherZ = Math.floor(placedBlock.z()) + 0.5D;
            final double dx = centerX - otherX;
            final double dz = centerZ - otherZ;
            if ((dx * dx) + (dz * dz) < minDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBlockingEntityAt(final World world, final int blockX, final int blockY, final int blockZ) {
        final BoundingBox spawnBox = new BoundingBox(
                blockX,
                blockY,
                blockZ,
                blockX + 1.0D,
                blockY + 2.0D,
                blockZ + 1.0D
        );
        final Location center = new Location(world, blockX + 0.5D, blockY + 1.0D, blockZ + 0.5D);
        for (final Entity entity : world.getNearbyEntities(center, 1.0D, 1.5D, 1.0D)) {
            if (!entity.isValid() || entity.isDead()) {
                continue;
            }
            if (entity instanceof Interaction) {
                continue;
            }
            // Skip players — a player placing a node is expected to stand at the
            // spawn location, and the entity will move away naturally.
            if (entity instanceof org.bukkit.entity.Player) {
                continue;
            }
            try {
                if (entity.getBoundingBox().overlaps(spawnBox)) {
                    return true;
                }
            } catch (final Exception ignored) {
                return true;
            }
        }
        return false;
    }

    private boolean isSolidAt(final World world, final int x, final int y, final int z) {
        final Block block = world.getBlockAt(x, y, z);
        return !block.getType().isAir() && !block.isPassable();
    }

    private boolean isGroundSupport(final World world, final int blockX, final int spawnY, final int blockZ) {
        if (spawnY - 1 < world.getMinHeight()) {
            return false;
        }
        final Block support = world.getBlockAt(blockX, spawnY - 1, blockZ);
        if (support.getType() == Material.SNOW) {
            return false;
        }
        if (support.getType().isAir()) {
            return false;
        }
        if (isLiquid(support)) {
            return false;
        }
        return !VEGETATION_MATERIALS.contains(support.getType());
    }

    /**
     * Checks if the given block is a liquid block (water or lava).
     * Uses ordinal comparison and fallback name-based checks for cross-version compatibility.
     */
    private static boolean isLiquid(final Block block) {
        final Material type = block.getType();
        if (type == Material.WATER || type == Material.LAVA) {
            return true;
        }
        // Cross-version fallback for renamed/legacy materials
        final String name = type.name();
        return name.equals("WATER") || name.equals("LAVA")
                || name.equals("LEGACY_WATER") || name.equals("LEGACY_LAVA")
                || name.equals("FLOWING_WATER") || name.equals("FLOWING_LAVA");
    }

    private static Set<Material> buildVegetationMaterials() {
        final Set<Material> set = EnumSet.noneOf(Material.class);
        final String[] names = new String[]{
                "GRASS",
                "SHORT_GRASS",
                "TALL_GRASS",
                "FERN",
                "LARGE_FERN",
                "DEAD_BUSH",
                "SEAGRASS",
                "TALL_SEAGRASS",
                "VINE",
                "HANGING_ROOTS",
                "AZALEA",
                "FLOWERING_AZALEA",
                "MOSS_CARPET",
                "SUGAR_CANE",
                "BAMBOO",
                "SWEET_BERRY_BUSH",
                "SHORT_DRY_GRASS",
                "TALL_DRY_GRASS"
        };
        for (final String name : names) {
            try {
                final Material material = Material.matchMaterial(name);
                if (material != null) {
                    set.add(material);
                }
            } catch (final Exception ignored) {
                // ignore missing materials
            }
        }
        return java.util.Collections.unmodifiableSet(set);
    }
}
