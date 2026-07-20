package me.chyxelmc.mmoblock.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.nmsloader.NmsAdapter;

public final class BdEngineService {

    private static final String BDENGINE_FOLDER = "models" + File.separator + "bdengine";
    private static final String BDENGINE_EXTENSION = ".bdengine";
    private static final int DEFAULT_ANIMATION_TICKS_PER_KEYFRAME = 2;
    private static final Gson GSON = new Gson();

    private final MMOBlock plugin;
    private final NmsAdapter nmsAdapter;
    private final Map<String, BdEngineModel> modelCache = new HashMap<>();
    private final Map<UUID, PacketModelState> activeModels = new HashMap<>();
    private final Map<UUID, List<BdEngineCollisionEntry>> activeCollisions = new HashMap<>();
    private boolean warnedUnsupportedAdapter;

    public BdEngineService(final MMOBlock plugin, final NmsAdapter nmsAdapter) {
        this.plugin = plugin;
        this.nmsAdapter = nmsAdapter;
    }

    public void showModel(
            final PlacedBlockKey blockKey,
            final BlockDefinition definition,
            final World world,
            final double x,
            final double y,
            final double z
    ) {
        if (blockKey == null || definition == null || !definition.bdengineEnabled()) {
            return;
        }
        if (!this.nmsAdapter.supportsPacketBdEngineModels()) {
            warnUnsupportedAdapter();
            return;
        }
        clearModel(blockKey.uniqueId(), world);

        final BdEngineModel model = loadModel(definition.bdengineModel());
        if (model == null || model.parts().isEmpty()) {
            this.plugin.getLogger().warning("BDEngine model '" + definition.bdengineModel() + "' has no supported display parts.");
            return;
        }

        final Location base = new Location(world, Math.floor(x), y, Math.floor(z));
        final double size = Math.max(0.01D, definition.bdengineSize());
        final List<NmsAdapter.BdEngineDisplayPart> displayParts = new ArrayList<>();
        final List<BdEngineRenderedPart> renderedParts = new ArrayList<>();

        for (final BdEnginePart part : model.parts()) {
            final Material material = part.materialName() == null ? null : Material.matchMaterial(normalizeMaterialName(part.materialName()), false);
            if ((part.type() == NmsAdapter.BdEngineDisplayType.BLOCK || part.type() == NmsAdapter.BdEngineDisplayType.ITEM) && material == null) {
                continue;
            }
            if (part.type() == NmsAdapter.BdEngineDisplayType.BLOCK && !material.isBlock()) {
                continue;
            }
            final BdEngineRenderedPart renderedPart = new BdEngineRenderedPart(
                    part.type(),
                    material,
                    part.text(),
                    new Matrix4f(part.transforms()),
                    part.skyLight(),
                    part.blockLight()
            );
            renderedParts.add(renderedPart);
            displayParts.add(toDisplayPart(renderedPart, size, model.bounds()));
        }

        if (!displayParts.isEmpty()) {
            final PacketModelState state = new PacketModelState(
                    base,
                    List.copyOf(displayParts),
                    List.copyOf(displayParts),
                    List.copyOf(renderedParts),
                    size,
                    model.bounds(),
                    model.animations(),
                    0L
            );
            this.activeModels.put(blockKey.uniqueId(), state);
            for (final Player player : world.getNearbyPlayers(base, 128.0D)) {
                this.nmsAdapter.upsertPacketBdEngineModel(player, blockKey.uniqueId(), base, displayParts);
            }
        }
        applyCollision(blockKey.uniqueId(), definition, world, x, y, z);
    }

    private void warnUnsupportedAdapter() {
        if (this.warnedUnsupportedAdapter) {
            return;
        }
        this.warnedUnsupportedAdapter = true;
        this.plugin.getLogger().warning("BDEngine packet models are not implemented for NMS adapter " + this.nmsAdapter.targetMinecraftVersion() + ".");
    }

    public void clearModel(final UUID blockUniqueId, final World world) {
        if (blockUniqueId == null) {
            return;
        }
        final PacketModelState state = this.activeModels.remove(blockUniqueId);
        if (state != null) {
            final World targetWorld = world != null ? world : state.baseLocation().getWorld();
            if (targetWorld != null) {
                for (final Player player : targetWorld.getPlayers()) {
                    if (player.getWorld().equals(targetWorld)) {
                        this.nmsAdapter.removePacketBdEngineModel(player, blockUniqueId);
                    }
                }
            }
        }
        clearCollision(blockUniqueId, world);
    }

    public void syncForPlayer(final Player player, final UUID blockUniqueId) {
        if (player == null || blockUniqueId == null || !this.nmsAdapter.supportsPacketBdEngineModels()) {
            return;
        }
        final PacketModelState state = this.activeModels.get(blockUniqueId);
        if (state == null || state.baseLocation().getWorld() == null || !state.baseLocation().getWorld().equals(player.getWorld())) {
            return;
        }
        if (state.baseLocation().distanceSquared(player.getLocation()) > 128.0D * 128.0D) {
            this.nmsAdapter.removePacketBdEngineModel(player, blockUniqueId);
            return;
        }
        this.nmsAdapter.upsertPacketBdEngineModel(player, blockUniqueId, state.baseLocation(), state.parts());
        final List<BdEngineCollisionEntry> collisions = this.activeCollisions.get(blockUniqueId);
        if (collisions != null) {
            for (final BdEngineCollisionEntry entry : collisions) {
                if (player.getWorld().getName().equals(entry.worldName())) {
                    this.nmsAdapter.showFakeBlock(player, player.getWorld(), new Location(player.getWorld(), entry.x(), entry.y(), entry.z()), entry.material());
                }
            }
        }
    }

    public void playAnimation(final UUID blockUniqueId, final String animationName) {
        playAnimation(blockUniqueId, animationName, 0.0D, "once");
    }

    public void playAnimation(final UUID blockUniqueId, final String animationName, final double timelineLengthSeconds, final String mode) {
        final PacketModelState state = this.activeModels.get(blockUniqueId);
        if (state == null || animationName == null || animationName.isBlank()) {
            return;
        }
        final String normalized = animationName.toLowerCase(Locale.ROOT);
        final BdEngineAnimation animation = resolveAnimation(state.animations(), normalized);
        if (animation == null || animation.frames().isEmpty()) {
            this.plugin.getLogger().warning("BDEngine animation '" + animationName + "' was not found. Available animations: " + state.animations().keySet());
            return;
        }
        final long sequence = state.animationSequence() + 1L;
        final PacketModelState runningState = state.withAnimationSequence(sequence);
        this.activeModels.put(blockUniqueId, runningState);
        final List<BdEngineAnimationFrame> sampledFrames = sampleAnimationFrames(animation.frames(), timelineLengthSeconds);
        for (final BdEngineAnimationFrame frame : sampledFrames) {
            final long delay = Math.max(0L, Math.round(frame.time()));
            this.plugin.scheduler().runAtLocationLater(
                    runningState.baseLocation(),
                    () -> sendParts(blockUniqueId, state.baseLocation(), transformParts(
                            runningState.baseParts(),
                            runningState.renderedParts(),
                            animation.bindTransform(),
                            frame,
                            runningState.size(),
                            runningState.bounds()
                    ), sequence),
                    delay
            );
        }
        final long loopDelay = sampledFrames.isEmpty()
                ? 0L
                : Math.max(1L, Math.round(sampledFrames.get(sampledFrames.size() - 1).time()) + 1L);
        if ("loop".equalsIgnoreCase(mode) && loopDelay > 0L) {
            this.plugin.scheduler().runAtLocationLater(
                    runningState.baseLocation(),
                    () -> replayLoop(blockUniqueId, animationName, timelineLengthSeconds, mode, sequence),
                    loopDelay
            );
        } else if (loopDelay > 0L) {
            this.plugin.scheduler().runAtLocationLater(
                    runningState.baseLocation(),
                    () -> sendParts(blockUniqueId, runningState.baseLocation(), runningState.baseParts(), sequence),
                    loopDelay
            );
        }
    }

    private void replayLoop(
            final UUID blockUniqueId,
            final String animationName,
            final double timelineLengthSeconds,
            final String mode,
            final long animationSequence
    ) {
        final PacketModelState state = this.activeModels.get(blockUniqueId);
        if (state == null || state.animationSequence() != animationSequence) {
            return;
        }
        playAnimation(blockUniqueId, animationName, timelineLengthSeconds, mode);
    }

    private void sendParts(final UUID blockUniqueId, final Location baseLocation, final List<NmsAdapter.BdEngineDisplayPart> parts, final long animationSequence) {
        final PacketModelState state = this.activeModels.get(blockUniqueId);
        if (state == null || state.animationSequence() != animationSequence) {
            return;
        }
        final World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }
        this.activeModels.put(blockUniqueId, state.withParts(parts));
        for (final Player player : world.getNearbyPlayers(baseLocation, 128.0D)) {
            this.nmsAdapter.upsertPacketBdEngineModel(player, blockUniqueId, baseLocation, parts);
        }
    }

    private List<NmsAdapter.BdEngineDisplayPart> transformParts(
            final List<NmsAdapter.BdEngineDisplayPart> baseParts,
            final List<BdEngineRenderedPart> parts,
            final Matrix4f bindTransform,
            final BdEngineAnimationFrame frame,
            final double size,
            final BdEngineBounds bounds
    ) {
        final Matrix4f frameTransform = frame.transform();
        if (sameTransform(frameTransform, bindTransform)) {
            return List.copyOf(baseParts);
        }
        final Matrix4f inverseBind = new Matrix4f(bindTransform).invert();
        final Matrix4f delta = new Matrix4f(frameTransform).mul(inverseBind);
        final List<NmsAdapter.BdEngineDisplayPart> transformed = new ArrayList<>(parts.size());
        for (final BdEngineRenderedPart part : parts) {
            final Matrix4f matrix = new Matrix4f(delta).mul(part.transforms());
            transformed.add(toDisplayPart(part.withTransforms(matrix), size, bounds));
        }
        return transformed;
    }

    private boolean sameTransform(final Matrix4f first, final Matrix4f second) {
        final float epsilon = 0.0001F;
        return Math.abs(first.m00() - second.m00()) <= epsilon
                && Math.abs(first.m01() - second.m01()) <= epsilon
                && Math.abs(first.m02() - second.m02()) <= epsilon
                && Math.abs(first.m03() - second.m03()) <= epsilon
                && Math.abs(first.m10() - second.m10()) <= epsilon
                && Math.abs(first.m11() - second.m11()) <= epsilon
                && Math.abs(first.m12() - second.m12()) <= epsilon
                && Math.abs(first.m13() - second.m13()) <= epsilon
                && Math.abs(first.m20() - second.m20()) <= epsilon
                && Math.abs(first.m21() - second.m21()) <= epsilon
                && Math.abs(first.m22() - second.m22()) <= epsilon
                && Math.abs(first.m23() - second.m23()) <= epsilon
                && Math.abs(first.m30() - second.m30()) <= epsilon
                && Math.abs(first.m31() - second.m31()) <= epsilon
                && Math.abs(first.m32() - second.m32()) <= epsilon
                && Math.abs(first.m33() - second.m33()) <= epsilon;
    }

    private NmsAdapter.BdEngineDisplayPart toDisplayPart(
            final BdEngineRenderedPart part,
            final double size,
            final BdEngineBounds bounds
    ) {
        return new NmsAdapter.BdEngineDisplayPart(
                part.type(),
                part.material(),
                part.text(),
                matrixValues(toDisplayMatrix(part.transforms(), size, bounds)),
                part.skyLight(),
                part.blockLight()
        );
    }

    public void clearAll() {
        for (final Map.Entry<UUID, PacketModelState> entry : new ArrayList<>(this.activeModels.entrySet())) {
            final Location base = entry.getValue().baseLocation();
            final World world = base.getWorld();
            if (world != null) {
                for (final Player player : world.getNearbyPlayers(base, 160.0D)) {
                    this.nmsAdapter.removePacketBdEngineModel(player, entry.getKey());
                }
            }
        }
        for (final Map.Entry<UUID, List<BdEngineCollisionEntry>> entry : new ArrayList<>(this.activeCollisions.entrySet())) {
            final World world = this.plugin.getServer().getWorld(entry.getValue().isEmpty() ? "" : entry.getValue().get(0).worldName());
            clearCollision(entry.getKey(), world);
        }
        this.activeModels.clear();
        this.activeCollisions.clear();
        this.modelCache.clear();
    }

    private void applyCollision(
            final UUID blockUniqueId,
            final BlockDefinition definition,
            final World world,
            final double x,
            final double y,
            final double z
    ) {
        final List<String> positions = definition.bdengineCollisionPositions();
        if (positions == null || positions.isEmpty()) {
            return;
        }

        final List<BdEngineCollisionEntry> entries = new ArrayList<>();
        for (final String rawPosition : positions) {
            final int[] offset = parseBlockOffset(rawPosition);
            if (offset == null) {
                continue;
            }
            final int worldX = (int) Math.floor(x) + offset[0];
            final int worldY = (int) Math.floor(y) + offset[1];
            final int worldZ = (int) Math.floor(z) + offset[2];
            final Location location = new Location(world, worldX, worldY, worldZ);
            this.nmsAdapter.showFakeBlock(world, location, Material.BARRIER);
            entries.add(new BdEngineCollisionEntry(world.getName(), worldX, worldY, worldZ, Material.BARRIER));
        }
        if (!entries.isEmpty()) {
            this.activeCollisions.put(blockUniqueId, entries);
        }
    }

    private void clearCollision(final UUID blockUniqueId, final World world) {
        final List<BdEngineCollisionEntry> entries = this.activeCollisions.remove(blockUniqueId);
        if (entries == null) {
            return;
        }
        for (final BdEngineCollisionEntry entry : entries) {
            final World entryWorld = world != null && world.getName().equals(entry.worldName())
                    ? world
                    : this.plugin.getServer().getWorld(entry.worldName());
            if (entryWorld != null) {
                this.nmsAdapter.clearFakeBlock(entryWorld, new Location(entryWorld, entry.x(), entry.y(), entry.z()));
            }
        }
    }

    private BdEngineModel loadModel(final String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        final String cacheKey = modelName.toLowerCase(Locale.ROOT);
        final BdEngineModel cached = this.modelCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        final File modelsDir = new File(this.plugin.getDataFolder(), BDENGINE_FOLDER);
        final File file = resolveModelFile(modelsDir, modelName);
        if (file == null) {
            this.plugin.getLogger().warning("BDEngine model not found: " + modelName);
            return null;
        }

        try {
            final String sceneJson = readSceneJson(file);
            final BdEngineModel model = parseModel(sceneJson);
            this.modelCache.put(cacheKey, model);
            return model;
        } catch (final IOException | RuntimeException exception) {
            this.plugin.getLogger().warning("Failed to load BDEngine model " + modelName + ": " + exception.getMessage());
            return null;
        }
    }

    private File resolveModelFile(final File modelsDir, final String modelName) {
        if (!modelsDir.exists()) {
            return null;
        }
        final File exact = new File(modelsDir, modelName);
        if (exact.isFile()) {
            return exact;
        }
        final File withExtension = new File(modelsDir, modelName + BDENGINE_EXTENSION);
        return withExtension.isFile() ? withExtension : null;
    }

    private String readSceneJson(final File file) throws IOException {
        final byte[] rawBytes = Files.readAllBytes(file.toPath());
        final byte[] decompressed = gunzip(rawBytes);
        if (startsWith(decompressed, "PRJ2")) {
            return readPrj2Scene(decompressed);
        }
        return new String(decompressed, StandardCharsets.UTF_8);
    }

    private byte[] gunzip(final byte[] rawBytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(rawBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private String readPrj2Scene(final byte[] data) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            final byte[] magic = input.readNBytes(4);
            if (!"PRJ2".equals(new String(magic, StandardCharsets.US_ASCII))) {
                throw new IOException("Invalid BDEngine project header");
            }
            input.skipNBytes(5);
            while (true) {
                final int nameLength = readUnsignedShortLittleEndian(input);
                final String name = new String(input.readNBytes(nameLength), StandardCharsets.UTF_8);
                final int contentLength = readIntLittleEndian(input);
                final byte[] content = input.readNBytes(contentLength);
                if (content.length != contentLength) {
                    throw new EOFException("Unexpected end of BDEngine project");
                }
                if ("scene.json".equals(name)) {
                    return new String(content, StandardCharsets.UTF_8);
                }
            }
        }
    }

    private BdEngineModel parseModel(final String sceneJson) {
        final JsonElement root = GSON.fromJson(sceneJson, JsonElement.class);
        final List<BdEnginePart> parts = new ArrayList<>();
        final Map<String, BdEngineAnimation> animations = new LinkedHashMap<>();
        final Map<Integer, String> animationNames = collectAnimationNames(root);
        collectParts(root, new Matrix4f(), parts, animations, animationNames);
        aliasAnimations(animations);
        return new BdEngineModel(parts, calculateBounds(parts), Map.copyOf(animations));
    }

    private Map<Integer, String> collectAnimationNames(final JsonElement element) {
        final Map<Integer, String> names = new HashMap<>();
        collectAnimationNames(element, names);
        return names;
    }

    private void collectAnimationNames(final JsonElement element, final Map<Integer, String> names) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (final JsonElement child : element.getAsJsonArray()) {
                collectAnimationNames(child, names);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        final JsonObject object = element.getAsJsonObject();
        final JsonArray listAnim = object.getAsJsonArray("listAnim");
        if (listAnim != null) {
            for (final JsonElement item : listAnim) {
                if (!item.isJsonObject()) {
                    continue;
                }
                final JsonObject animation = item.getAsJsonObject();
                if (!animation.has("id") || !animation.has("name")) {
                    continue;
                }
                final String name = animation.get("name").getAsString();
                if (name != null && !name.isBlank()) {
                    names.put(animation.get("id").getAsInt(), name.toLowerCase(Locale.ROOT));
                }
            }
        }
        final JsonArray children = object.getAsJsonArray("children");
        if (children != null) {
            for (final JsonElement child : children) {
                collectAnimationNames(child, names);
            }
        }
    }

    private void collectParts(
            final JsonElement element,
            final Matrix4f parentTransform,
            final List<BdEnginePart> parts,
            final Map<String, BdEngineAnimation> animations,
            final Map<Integer, String> animationNames
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (final JsonElement child : element.getAsJsonArray()) {
                collectParts(child, parentTransform, parts, animations, animationNames);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        final JsonObject object = element.getAsJsonObject();
        final Matrix4f localTransform = readTransform(object.get("transforms"));
        final Matrix4f transform = new Matrix4f(parentTransform).mul(localTransform);
        collectAnimations(object, transform, animations, animationNames);

        final NmsAdapter.BdEngineDisplayType type = readPartType(object);
        if (type != null) {
            final String materialName = readMaterialName(object, type);
            final String text = readText(object);
            final int skyLight = readBrightness(object, "sky", 15);
            final int blockLight = readBrightness(object, "block", 15);
            parts.add(new BdEnginePart(type, materialName, text, transform, skyLight, blockLight));
        }

        final JsonArray children = object.getAsJsonArray("children");
        if (children == null) {
            return;
        }
        for (final JsonElement child : children) {
            collectParts(child, transform, parts, animations, animationNames);
        }
    }

    private void collectAnimations(
            final JsonObject object,
            final Matrix4f bindTransform,
            final Map<String, BdEngineAnimation> animations,
            final Map<Integer, String> animationNames
    ) {
        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            final String key = entry.getKey();
            if (!key.startsWith("animation") || !entry.getValue().isJsonArray()) {
                continue;
            }
            final List<BdEngineAnimationFrame> frames = parseAnimationFrames(entry.getValue().getAsJsonArray());
            if (!frames.isEmpty()) {
                final BdEngineAnimation animation = new BdEngineAnimation(new Matrix4f(bindTransform), frames);
                final String normalizedKey = key.toLowerCase(Locale.ROOT);
                animations.putIfAbsent(normalizedKey, animation);
                final String configuredName = animationNames.get(animationIdFromKey(normalizedKey));
                if (configuredName != null && !configuredName.isBlank()) {
                    animations.putIfAbsent(configuredName, animation);
                }
            }
        }
    }

    private int animationIdFromKey(final String key) {
        if ("animation".equals(key)) {
            return 1;
        }
        if (key != null && key.startsWith("animation_")) {
            try {
                return Integer.parseInt(key.substring("animation_".length()));
            } catch (final NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private List<BdEngineAnimationFrame> parseAnimationFrames(final JsonArray array) {
        final List<BdEngineAnimationFrame> rawFrames = new ArrayList<>();
        for (final JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            final JsonObject object = element.getAsJsonObject();
            final double time = object.has("time") ? object.get("time").getAsDouble() : rawFrames.size();
            final BdEngineAnimationPose pose = keyframePose(object);
            rawFrames.add(new BdEngineAnimationFrame(time, pose));
        }
        rawFrames.sort(java.util.Comparator.comparingDouble(BdEngineAnimationFrame::time));
        return rawFrames;
    }

    private List<BdEngineAnimationFrame> sampleAnimationFrames(final List<BdEngineAnimationFrame> rawFrames, final double timelineLengthSeconds) {
        if (rawFrames.size() < 2) {
            return rawFrames;
        }
        final double firstTime = rawFrames.get(0).time();
        final double lastTime = rawFrames.get(rawFrames.size() - 1).time();
        final double rawDuration = Math.max(0.0001D, lastTime - firstTime);
        final long configuredTotalTicks = timelineLengthSeconds > 0.0D
                ? Math.max(1L, Math.round(timelineLengthSeconds * 20.0D))
                : Math.max(1L, Math.round(rawDuration * DEFAULT_ANIMATION_TICKS_PER_KEYFRAME));
        final List<BdEngineAnimationFrame> frames = new ArrayList<>();
        for (int i = 0; i < rawFrames.size() - 1; i++) {
            final BdEngineAnimationFrame current = rawFrames.get(i);
            final BdEngineAnimationFrame next = rawFrames.get(i + 1);
            final long startTick = Math.round(((current.time() - firstTime) / rawDuration) * configuredTotalTicks);
            final long endTick = Math.max(startTick + 1L, Math.round(((next.time() - firstTime) / rawDuration) * configuredTotalTicks));
            final long gap = endTick - startTick;
            if (frames.isEmpty() || Math.round(frames.get(frames.size() - 1).time()) != startTick) {
                frames.add(new BdEngineAnimationFrame(startTick, current.pose()));
            }
            final BdEngineAnimationPose currentPose = current.pose();
            final BdEngineAnimationPose nextPose = next.pose();
            for (long tick = startTick + 1L; tick <= endTick; tick++) {
                final float progress = smoothStep((float) (tick - startTick) / (float) gap);
                final BdEngineAnimationPose pose = currentPose.lerp(nextPose, progress);
                frames.add(new BdEngineAnimationFrame(tick, pose));
            }
        }
        return frames;
    }

    private float smoothStep(final float value) {
        final float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return clamped * clamped * (3.0F - (2.0F * clamped));
    }

    private BdEngineBounds calculateBounds(final List<BdEnginePart> parts) {
        if (parts.isEmpty()) {
            return new BdEngineBounds(0.0F, 0.0F, 0.0F);
        }
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (final BdEnginePart part : parts) {
            final Matrix4f matrix = part.transforms();
            for (int corner = 0; corner < 8; corner++) {
                final float x = (corner & 1) == 0 ? 0.0F : 1.0F;
                final float y = (corner & 2) == 0 ? 0.0F : 1.0F;
                final float z = (corner & 4) == 0 ? 0.0F : 1.0F;
                final float transformedX = matrix.m30() + (matrix.m00() * x) + (matrix.m10() * y) + (matrix.m20() * z);
                final float transformedY = matrix.m31() + (matrix.m01() * x) + (matrix.m11() * y) + (matrix.m21() * z);
                final float transformedZ = matrix.m32() + (matrix.m02() * x) + (matrix.m12() * y) + (matrix.m22() * z);
                minX = Math.min(minX, transformedX);
                minY = Math.min(minY, transformedY);
                minZ = Math.min(minZ, transformedZ);
                maxX = Math.max(maxX, transformedX);
                maxY = Math.max(maxY, transformedY);
                maxZ = Math.max(maxZ, transformedZ);
            }
        }
        return new BdEngineBounds((minX + maxX) * 0.5F, minY, (minZ + maxZ) * 0.5F);
    }

    private BdEngineAnimationPose keyframePose(final JsonObject object) {
        final JsonObject position = object.getAsJsonObject("position");
        final JsonObject rotation = object.getAsJsonObject("rotation");
        final JsonObject scale = object.getAsJsonObject("scale");
        return new BdEngineAnimationPose(
                vectorValue(position, "x", 0.0F),
                vectorValue(position, "y", 0.0F),
                vectorValue(position, "z", 0.0F),
                vectorValue(rotation, "x", 0.0F),
                vectorValue(rotation, "y", 0.0F),
                vectorValue(rotation, "z", 0.0F),
                vectorValue(scale, "x", 1.0F),
                vectorValue(scale, "y", 1.0F),
                vectorValue(scale, "z", 1.0F)
        );
    }

    private float vectorValue(final JsonObject object, final String key, final float fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsFloat();
    }

    private void aliasAnimations(final Map<String, BdEngineAnimation> animations) {
        final BdEngineAnimation first = animations.get("animation");
        final BdEngineAnimation second = animations.get("animation_2");
        if (first != null) {
            animations.putIfAbsent("spawn", first);
        }
        if (second != null) {
            animations.putIfAbsent("click", second);
            animations.putIfAbsent("hit", second);
            animations.putIfAbsent("mine", second);
        }
    }

    private BdEngineAnimation resolveAnimation(final Map<String, BdEngineAnimation> animations, final String animationName) {
        if (animations == null || animations.isEmpty()) {
            return null;
        }
        final BdEngineAnimation direct = animations.get(animationName);
        if (direct != null) {
            return direct;
        }
        return switch (animationName) {
            case "spawn" -> animations.get("animation");
            case "click", "hit", "mine" -> animations.get("animation_2");
            default -> null;
        };
    }

    private NmsAdapter.BdEngineDisplayType readPartType(final JsonObject object) {
        if (object.has("isBlockDisplay") && object.get("isBlockDisplay").getAsBoolean()) {
            return NmsAdapter.BdEngineDisplayType.BLOCK;
        }
        if (object.has("isItemDisplay") && object.get("isItemDisplay").getAsBoolean()) {
            return NmsAdapter.BdEngineDisplayType.ITEM;
        }
        if (object.has("isTextDisplay") && object.get("isTextDisplay").getAsBoolean()) {
            return NmsAdapter.BdEngineDisplayType.TEXT;
        }
        return null;
    }

    private String readMaterialName(final JsonObject object, final NmsAdapter.BdEngineDisplayType type) {
        final String stateKey = type == NmsAdapter.BdEngineDisplayType.ITEM ? "defaultItemState" : "defaultBlockState";
        final JsonObject defaultState = object.getAsJsonObject(stateKey);
        if (defaultState != null && defaultState.has("name")) {
            return defaultState.get("name").getAsString();
        }
        return object.has("name") ? object.get("name").getAsString() : null;
    }

    private String readText(final JsonObject object) {
        for (final String key : List.of("text", "content", "name")) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsString();
            }
        }
        return "";
    }

    private int readBrightness(final JsonObject object, final String key, final int fallback) {
        final JsonObject brightness = object.getAsJsonObject("brightness");
        if (brightness == null || !brightness.has(key)) {
            return fallback;
        }
        return Math.max(0, Math.min(15, brightness.get(key).getAsInt()));
    }

    private Matrix4f readTransform(final JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return new Matrix4f();
        }
        final JsonArray array = element.getAsJsonArray();
        if (array.size() < 16) {
            return new Matrix4f();
        }
        final float[] values = new float[16];
        for (int i = 0; i < 16; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return rowMajorToJoml(values, 1.0D);
    }

    private Matrix4f toDisplayMatrix(final Matrix4f modelMatrix, final double size, final BdEngineBounds bounds) {
        final float scale = (float) size;
        final Matrix4f scaled = new Matrix4f(modelMatrix);

        scaled.m00(scaled.m00() * scale);
        scaled.m01(scaled.m01() * scale);
        scaled.m02(scaled.m02() * scale);
        scaled.m10(scaled.m10() * scale);
        scaled.m11(scaled.m11() * scale);
        scaled.m12(scaled.m12() * scale);
        scaled.m20(scaled.m20() * scale);
        scaled.m21(scaled.m21() * scale);
        scaled.m22(scaled.m22() * scale);
        scaled.m30(0.5F + ((scaled.m30() - 0.5F) * scale));
        scaled.m31((scaled.m31() - bounds.minY()) * scale);
        scaled.m32(0.5F + ((scaled.m32() - 0.5F) * scale));
        return scaled;
    }

    private Matrix4f rowMajorToJoml(final float[] values, final double size) {
        final Matrix4f matrix = new Matrix4f();
        final float scale = (float) size;
        matrix.m00(values[0] * scale);
        matrix.m10(values[1] * scale);
        matrix.m20(values[2] * scale);
        matrix.m30(values[3] * scale);
        matrix.m01(values[4] * scale);
        matrix.m11(values[5] * scale);
        matrix.m21(values[6] * scale);
        matrix.m31(values[7] * scale);
        matrix.m02(values[8] * scale);
        matrix.m12(values[9] * scale);
        matrix.m22(values[10] * scale);
        matrix.m32(values[11] * scale);
        matrix.m03(values[12]);
        matrix.m13(values[13]);
        matrix.m23(values[14]);
        matrix.m33(values[15]);
        return matrix;
    }

    private float[] matrixValues(final Matrix4f matrix) {
        return new float[]{
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        };
    }

    private String normalizeMaterialName(final String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        final int propertiesStart = value.indexOf('[');
        if (propertiesStart >= 0) {
            value = value.substring(0, propertiesStart);
        }
        final int namespaceIndex = value.indexOf(':');
        if (namespaceIndex >= 0) {
            value = value.substring(namespaceIndex + 1);
        }
        return value;
    }

    private int[] parseBlockOffset(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String[] parts = raw.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            return new int[]{
                    (int) Math.round(Double.parseDouble(parts[0].trim())),
                    (int) Math.round(Double.parseDouble(parts[1].trim())),
                    (int) Math.round(Double.parseDouble(parts[2].trim()))
            };
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    private static boolean startsWith(final byte[] data, final String magic) {
        final byte[] expected = magic.getBytes(StandardCharsets.US_ASCII);
        if (data.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readUnsignedShortLittleEndian(final DataInputStream input) throws IOException {
        final int b1 = input.readUnsignedByte();
        final int b2 = input.readUnsignedByte();
        return b1 | (b2 << 8);
    }

    private static int readIntLittleEndian(final DataInputStream input) throws IOException {
        final int b1 = input.readUnsignedByte();
        final int b2 = input.readUnsignedByte();
        final int b3 = input.readUnsignedByte();
        final int b4 = input.readUnsignedByte();
        return b1 | (b2 << 8) | (b3 << 16) | (b4 << 24);
    }

    public record PlacedBlockKey(UUID uniqueId) {
    }

    private record BdEngineModel(List<BdEnginePart> parts, BdEngineBounds bounds, Map<String, BdEngineAnimation> animations) {
    }

    private record BdEngineBounds(float centerX, float minY, float centerZ) {
    }

    private record BdEnginePart(NmsAdapter.BdEngineDisplayType type, String materialName, String text, Matrix4f transforms, int skyLight, int blockLight) {
    }

    private record BdEngineRenderedPart(
            NmsAdapter.BdEngineDisplayType type,
            Material material,
            String text,
            Matrix4f transforms,
            int skyLight,
            int blockLight
    ) {

        private BdEngineRenderedPart withTransforms(final Matrix4f transforms) {
            return new BdEngineRenderedPart(this.type, this.material, this.text, transforms, this.skyLight, this.blockLight);
        }
    }

    private record BdEngineAnimation(Matrix4f bindTransform, List<BdEngineAnimationFrame> frames) {
    }

    private record BdEngineAnimationPose(
            float positionX,
            float positionY,
            float positionZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {

        private BdEngineAnimationPose lerp(final BdEngineAnimationPose target, final float progress) {
            return new BdEngineAnimationPose(
                    lerp(this.positionX, target.positionX, progress),
                    lerp(this.positionY, target.positionY, progress),
                    lerp(this.positionZ, target.positionZ, progress),
                    lerp(this.rotationX, target.rotationX, progress),
                    lerp(this.rotationY, target.rotationY, progress),
                    lerp(this.rotationZ, target.rotationZ, progress),
                    lerp(this.scaleX, target.scaleX, progress),
                    lerp(this.scaleY, target.scaleY, progress),
                    lerp(this.scaleZ, target.scaleZ, progress)
            );
        }

        private Matrix4f transform() {
            return new Matrix4f()
                    .translation(this.positionX, this.positionY, this.positionZ)
                    .rotateXYZ(this.rotationX, this.rotationY, this.rotationZ)
                    .scale(this.scaleX, this.scaleY, this.scaleZ);
        }

        private static float lerp(final float start, final float end, final float progress) {
            return start + ((end - start) * progress);
        }
    }

    private record BdEngineAnimationFrame(double time, BdEngineAnimationPose pose) {

        private Matrix4f transform() {
            return this.pose.transform();
        }
    }

    private record PacketModelState(
            Location baseLocation,
            List<NmsAdapter.BdEngineDisplayPart> parts,
            List<NmsAdapter.BdEngineDisplayPart> baseParts,
            List<BdEngineRenderedPart> renderedParts,
            double size,
            BdEngineBounds bounds,
            Map<String, BdEngineAnimation> animations,
            long animationSequence
    ) {

        private PacketModelState withAnimationSequence(final long animationSequence) {
            return new PacketModelState(this.baseLocation, this.parts, this.baseParts, this.renderedParts, this.size, this.bounds, this.animations, animationSequence);
        }

        private PacketModelState withParts(final List<NmsAdapter.BdEngineDisplayPart> parts) {
            return new PacketModelState(this.baseLocation, List.copyOf(parts), this.baseParts, this.renderedParts, this.size, this.bounds, this.animations, this.animationSequence);
        }
    }

    private record BdEngineCollisionEntry(String worldName, int x, int y, int z, Material material) {
    }
}
