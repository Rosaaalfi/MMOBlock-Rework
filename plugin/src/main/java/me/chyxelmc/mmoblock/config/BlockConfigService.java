package me.chyxelmc.mmoblock.config;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinition;
import me.chyxelmc.mmoblock.model.ConditionDefinition;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.DropEntry;
import me.chyxelmc.mmoblock.model.ToolAction;
import me.chyxelmc.mmoblock.utils.TextColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BlockConfigService {

    private final MMOBlock plugin;
    private final Map<String, BlockDefinition> blockDefinitions = new HashMap<>();
    private final Map<String, List<DropEntry>> drops = new HashMap<>();
    private final Map<String, List<ToolAction>> tools = new HashMap<>();
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private ValidationReport lastBlockReport = ValidationReport.empty();
    private ValidationReport lastToolReport = ValidationReport.empty();
    private ValidationReport lastDropReport = ValidationReport.empty();
    private long interactionThrottleMs;

    public BlockConfigService(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    public void reloadAll() {
        this.plugin.reloadConfig();

        this.interactionThrottleMs = this.plugin.getConfig()
                .getLong("interactionThrottleMs", 1000L);

        reloadBlocks();
        reloadDrops();
        reloadTools();
        reloadLanguages();
    }

    public int reloadBlocks() {
        final ValidationReport report = ValidationReport.empty();
        this.blockDefinitions.clear();
        ensureResourceFolder("blocks");
        final File folder = new File(this.plugin.getDataFolder(), "blocks");
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        int loaded = 0;
        for (final File file : files) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (final String key : yaml.getKeys(false)) {
                final ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                final ConfigurationSection hitbox = section.getConfigurationSection("hitbox");
                final double width = hitbox != null ? hitbox.getDouble("width", 1.0D) : 1.0D;
                final double height = hitbox != null ? hitbox.getDouble("height", 1.0D) : 1.0D;
                final long respawn = section.getLong("respawnTime", 60L);
                final ConfigurationSection randomLocation = section.getConfigurationSection("randomLocation");
                final boolean randomLocationEnabled = randomLocation != null && randomLocation.getBoolean("enabled", false);
                final double randomLocationRadius = randomLocationEnabled ? Math.max(0.0D, randomLocation.getDouble("radius", 0.0D)) : 0.0D;
                boolean useRealBlockModel = section.getBoolean("modelType.block.enabled", false);
                final String configuredModelBlock = section.getString("modelType.block.block");
                if (!useRealBlockModel && configuredModelBlock != null && !configuredModelBlock.isBlank()) {
                    // Compatibility fallback for older files that only set modelType.block.block.
                    useRealBlockModel = true;
                }

                final Material realBlockMaterial = parseMaterial(configuredModelBlock == null ? "minecraft:stone" : configuredModelBlock);
                if (useRealBlockModel && realBlockMaterial == null) {
                    report.error("Block '" + key + "' has invalid modelType.block.block material.");
                }
                final Sound soundOnClick = parseSound(section.getString("sound.onClick"), "sound.onClick", key, report);
                final Sound soundOnDead = parseSound(section.getString("sound.onDead"), "sound.onDead", key, report);
                final Sound soundOnRespawn = parseSound(section.getString("sound.onRespawn"), "sound.onRespawn", key, report);
                final boolean particleBreak = section.getBoolean("particleBreak", false);
                final boolean breakAnimation = section.getBoolean("breakAnimation", false);
                final ConfigurationSection displaySection = section.getConfigurationSection("display");
                final double displayHeight = displaySection != null
                        ? displaySection.getDouble("displayHeight", section.getDouble("displayHeight", 1.6D))
                        : section.getDouble("displayHeight", 1.6D);
                final List<String> allowedTools = section.getStringList("allowedTools").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .toList();
                if (allowedTools.isEmpty()) {
                    report.warn("Block '" + key + "' has no allowedTools configured.");
                }
                final List<DisplayLine> displayLines = parseDisplayLines(section, key, report);
                final List<ConditionDefinition> conditions = parseConditions(section, key, report);
                this.blockDefinitions.put(
                        key.toLowerCase(Locale.ROOT),
                        new BlockDefinition(
                                key,
                                width,
                                height,
                                respawn,
                                randomLocationEnabled,
                                randomLocationRadius,
                                useRealBlockModel,
                                realBlockMaterial,
                                soundOnClick,
                                soundOnDead,
                                soundOnRespawn,
                                particleBreak,
                                breakAnimation,
                                displayHeight,
                                allowedTools,
                                displayLines,
                                conditions
                        )
                );
                loaded++;
            }
        }
        this.lastBlockReport = report;
        return loaded;
    }

    public int reloadDrops() {
        final ValidationReport report = ValidationReport.empty();
        this.drops.clear();
        ensureResourceFolder("drops");
        final File folder = new File(this.plugin.getDataFolder(), "drops");
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (final File file : files) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (final String key : yaml.getKeys(false)) {
                final List<Map<?, ?>> values = yaml.getMapList(key);
                final List<DropEntry> parsed = new ArrayList<>();
                for (final Map<?, ?> raw : values) {
                    final DropEntry entry = parseDropEntry(raw, key, report);
                    if (entry != null) {
                        parsed.add(entry);
                    }
                }
                this.drops.put(key.toLowerCase(Locale.ROOT), parsed);
            }
        }
        this.lastDropReport = report;
        return this.drops.size();
    }

    public int reloadTools() {
        final ValidationReport report = ValidationReport.empty();
        this.tools.clear();
        ensureResourceFolder("tools");
        final File folder = new File(this.plugin.getDataFolder(), "tools");
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (final File file : files) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (final String key : yaml.getKeys(false)) {
                final List<Map<?, ?>> values = yaml.getMapList(key);
                final List<ToolAction> parsed = new ArrayList<>();
                for (final Map<?, ?> raw : values) {
                    parsed.addAll(parseToolActions(raw, key, report));
                }
                if (parsed.isEmpty()) {
                    report.warn("Tool group '" + key + "' has no valid tool actions.");
                }
                this.tools.put(key.toLowerCase(Locale.ROOT), parsed);
            }
        }
        this.lastToolReport = report;
        return this.tools.size();
    }

    public int reloadLanguages() {
        this.languages.clear();
        return loadFolder("lang", this.languages);
    }

    public BlockDefinition findBlock(final String id) {
        if (id == null) {
            return null;
        }
        return this.blockDefinitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> blockIds() {
        return Collections.unmodifiableSet(this.blockDefinitions.keySet());
    }

    public ToolAction resolveToolAction(final BlockDefinition blockDefinition, final Material material, final String clickType) {
        for (final String toolId : blockDefinition.allowedTools()) {
            final List<ToolAction> actions = this.tools.get(toolId.toLowerCase(Locale.ROOT));
            if (actions == null) {
                continue;
            }
            for (final ToolAction action : actions) {
                if (action.material() != material) {
                    continue;
                }
                if ("both_click".equals(action.clickType()) || clickType.equals(action.clickType())) {
                    return action;
                }
            }
        }
        return null;
    }

    public List<DropEntry> findDrops(final String dropId) {
        return this.drops.getOrDefault(dropId.toLowerCase(Locale.ROOT), List.of());
    }

    public ValidationReport lastBlockReport() {
        return this.lastBlockReport;
    }

    public ValidationReport lastToolReport() {
        return this.lastToolReport;
    }

    public ValidationReport lastDropReport() {
        return this.lastDropReport;
    }

    public String message(final String path, final String fallback) {
        return TextColorUtil.toLegacySection(messageComponent(path, fallback));
    }

    public Component messageComponent(final String path, final String fallback) {
        final String lang = this.plugin.getConfig().getString("lang", "en-us").toLowerCase(Locale.ROOT);
        final YamlConfiguration selected = this.languages.get(lang + ".yml");
        if (selected == null) {
            return colorizeComponent(fallback);
        }
        return colorizeComponent(selected.getString(path, fallback));
    }

    public String message(final String path, final String fallback, final Map<String, String> placeholders) {
        return TextColorUtil.toLegacySection(messageComponent(path, fallback, placeholders));
    }

    public Component messageComponent(final String path, final String fallback, final Map<String, String> placeholders) {
        String value = rawMessage(path, fallback);
        for (final Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace(entry.getKey(), entry.getValue());
        }
        return colorizeComponent(value);
    }

    public Collection<String> knownWorlds() {
        return new ArrayList<>(this.plugin.getServer().getWorlds().stream().map(world -> world.getName()).toList());
    }

    private int loadFolder(final String folderName, final Map<String, YamlConfiguration> target) {
        ensureResourceFolder(folderName);
        final File folder = new File(this.plugin.getDataFolder(), folderName);
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }

        for (final File file : files) {
            target.put(file.getName(), loadYamlFile(file, "lang".equals(folderName)));
        }
        return target.size();
    }

    private YamlConfiguration loadYamlFile(final File file, final boolean allowTabRepair) {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (final IOException | InvalidConfigurationException exception) {
            if (allowTabRepair) {
                final YamlConfiguration repaired = tryRepairTabIndentedYaml(file, exception);
                if (repaired != null) {
                    return repaired;
                }
            }
            // logging removed: failed to load YAML file
            return yaml;
        }
    }

    private YamlConfiguration tryRepairTabIndentedYaml(final File file, final Exception originalException) {
        try {
            final String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!raw.contains("\t")) {
                return null;
            }

            final String fixed = raw.replace("\t", "  ");
            Files.writeString(file.toPath(), fixed, StandardCharsets.UTF_8);

            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            // logging removed: fixed tab indentation in file and reloaded successfully
            return yaml;
        } catch (final IOException | InvalidConfigurationException repairException) {
            // logging removed: failed to auto-repair YAML file
            return null;
        }
    }

    private List<ToolAction> parseToolActions(final Map<?, ?> raw, final String groupId, final ValidationReport report) {
        final Object materialRaw = raw.get("material");
        if (!(materialRaw instanceof String materialName)) {
            report.error("Tool group '" + groupId + "' contains entry without material.");
            return List.of();
        }

        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            report.error("Tool group '" + groupId + "' contains invalid material: " + materialName);
            return List.of();
        }

        final List<String> allowedDrops = normalizeStringList(raw.get("allowedDrops"));
        final List<ToolAction> actions = new ArrayList<>();
        parseToolAction(raw, groupId, "both_click", material, allowedDrops, actions, report);
        parseToolAction(raw, groupId, "left_click", material, allowedDrops, actions, report);
        parseToolAction(raw, groupId, "right_click", material, allowedDrops, actions, report);
        return actions;
    }

    private void parseToolAction(
            final Map<?, ?> raw,
            final String groupId,
            final String clickType,
            final Material material,
            final List<String> allowedDrops,
            final List<ToolAction> actions,
            final ValidationReport report
    ) {
        final Object sectionObject = raw.get(clickType);
        if (!(sectionObject instanceof Map<?, ?> section)) {
            return;
        }

        final int clickNeeded = parseInteger(section.get("clickNeeded"), 1);
        final int decreaseDurability = parseInteger(section.get("decreaseDurability"), 0);
        if (clickNeeded <= 0) {
            report.error("Tool group '" + groupId + "' has invalid clickNeeded <= 0 for " + clickType);
            return;
        }
        actions.add(new ToolAction(material, clickNeeded, decreaseDurability, allowedDrops, clickType));
    }

    private DropEntry parseDropEntry(final Map<?, ?> raw, final String dropId, final ValidationReport report) {
        final double chance = parseDouble(raw.get("chances"), 1.0D);
        final Object dropTypeRaw = raw.containsKey("drop_type") ? raw.get("drop_type") : "inventory";
        final String dropType = String.valueOf(dropTypeRaw);

        if (raw.containsKey("material")) {
            final Material material = Material.matchMaterial(String.valueOf(raw.get("material")));
            if (material == null) {
                report.error("Drop group '" + dropId + "' contains invalid material: " + raw.get("material"));
                return null;
            }
            final int[] range = parseRange(raw.get("total"), 1, 1);
            return new DropEntry(DropEntry.DropType.MATERIAL, material, range[0], range[1], null, chance, dropType);
        }
        if (raw.containsKey("experience")) {
            final int[] range = parseRange(raw.get("experience"), 1, 1);
            return new DropEntry(DropEntry.DropType.EXPERIENCE, null, range[0], range[1], null, chance, dropType);
        }
        if (raw.containsKey("command")) {
            return new DropEntry(DropEntry.DropType.COMMAND, null, 1, 1, String.valueOf(raw.get("command")), chance, dropType);
        }
        report.warn("Drop group '" + dropId + "' contains unsupported drop entry: " + raw);
        return null;
    }

    private List<DisplayLine> parseDisplayLines(
            final ConfigurationSection section,
            final String blockId,
            final ValidationReport report
    ) {
        final List<Map<?, ?>> nestedLines = section.getMapList("display.lines");
        if (!nestedLines.isEmpty()) {
            return parseDisplayLineMaps(nestedLines);
        }

        final List<Map<?, ?>> legacyLines = section.getMapList("display");
        if (!legacyLines.isEmpty()) {
            return parseDisplayLineMaps(legacyLines);
        }

        final ConfigurationSection displaySection = section.getConfigurationSection("display");
        final List<?> rawLines = displaySection != null ? displaySection.getList("lines", List.of()) : List.of();
        if (rawLines.isEmpty()) {
            report.warn("Block '" + blockId + "' has no display lines.");
            return List.of();
        }

        final List<DisplayLine> parsed = new ArrayList<>();
        for (final Object lineEntry : rawLines) {
            if (!(lineEntry instanceof Map<?, ?>) && !(lineEntry instanceof ConfigurationSection)) {
                continue;
            }
            final Object contents = resolveValue(lineEntry, "contents");
            final int number = parseInteger(resolveValue(lineEntry, "line"), parsed.size() + 1);
            final String text = valueAsString(contents, lineEntry, "text");
            final String click = valueAsString(contents, lineEntry, "click");
            final String dead = valueAsString(contents, lineEntry, "dead");
            final String item = valueAsString(contents, lineEntry, "item");
            final String block = valueAsString(contents, lineEntry, "block");
            parsed.add(new DisplayLine(number, text, click, dead, item, block));
        }
        return parsed;
    }

    private List<DisplayLine> parseDisplayLineMaps(final List<Map<?, ?>> lines) {
        final List<DisplayLine> parsed = new ArrayList<>();
        for (final Map<?, ?> line : lines) {
            final Object contents = line.get("contents");
            final int number = parseInteger(line.get("line"), parsed.size() + 1);
            final String text = valueAsString(contents, line, "text");
            final String click = valueAsString(contents, line, "click");
            final String dead = valueAsString(contents, line, "dead");
            final String item = valueAsString(contents, line, "item");
            final String block = valueAsString(contents, line, "block");
            parsed.add(new DisplayLine(number, text, click, dead, item, block));
        }
        return parsed;
    }

    private List<ConditionDefinition> parseConditions(
            final ConfigurationSection section,
            final String blockId,
            final ValidationReport report
    ) {
        final List<Map<?, ?>> rawList = section.getMapList("conditions");
        if (rawList.isEmpty()) {
            return List.of();
        }
        final List<ConditionDefinition> parsed = new ArrayList<>();
        for (final Map<?, ?> raw : rawList) {
            final int id = parseInteger(raw.get("condition"), -1);
            if (id <= 0) {
                report.warn("Block '" + blockId + "' has condition with invalid id.");
                continue;
            }
            final Object typeRaw = raw.get("type");
            final String type = String.valueOf(typeRaw == null ? "placeholder" : typeRaw)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            final String value = raw.get("value") != null ? String.valueOf(raw.get("value")) : null;
            final String operator = raw.get("operator") != null ? String.valueOf(raw.get("operator")) : "==";
            final String compareTo = raw.get("compareTo") != null ? String.valueOf(raw.get("compareTo")) : "";

            String requireText = null;
            String notMetText = null;
            final Object placeholderTextRaw = raw.get("placeholderText");
            if (placeholderTextRaw instanceof Map<?, ?> placeholderMap) {
                final Object requireRaw = placeholderMap.get("require");
                final Object notMetRaw = placeholderMap.get("notMet");
                requireText = requireRaw != null ? String.valueOf(requireRaw) : null;
                notMetText = notMetRaw != null ? String.valueOf(notMetRaw) : null;
            } else if (placeholderTextRaw instanceof ConfigurationSection placeholderSection) {
                requireText = placeholderSection.getString("require");
                notMetText = placeholderSection.getString("notMet");
            }
            final String sendTitle = raw.get("sendTitle") != null ? String.valueOf(raw.get("sendTitle")) : null;
            final String sendSubtitle = raw.get("sendSubtitle") != null ? String.valueOf(raw.get("sendSubtitle")) : null;

            parsed.add(new ConditionDefinition(
                    id,
                    type,
                    value,
                    operator,
                    compareTo,
                    requireText,
                    notMetText,
                    sendTitle,
                    sendSubtitle
            ));
        }
        return parsed;
    }

    private String valueAsString(final Object primary, final Object fallback, final String key) {
        final Object rawPrimary = resolveValue(primary, key);
        final Object raw = rawPrimary != null ? rawPrimary : resolveValue(fallback, key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean bool) {
            // click/dead false should be treated as "not configured", so renderer can fallback to text.
            return bool ? "true" : null;
        }
        final String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private Object resolveValue(final Object source, final String key) {
        if (source instanceof Map<?, ?> map) {
            return map.get(key);
        }
        if (source instanceof ConfigurationSection configurationSection) {
            return configurationSection.get(key);
        }
        return null;
    }

    private List<String> normalizeStringList(final Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).map(item -> item.toLowerCase(Locale.ROOT)).toList();
    }

    private int[] parseRange(final Object raw, final int defaultMin, final int defaultMax) {
        if (raw == null) {
            return new int[]{defaultMin, defaultMax};
        }

        String value = String.valueOf(raw).replace("[", "").replace("]", "").trim();
        if (value.contains(",")) {
            value = value.substring(0, value.indexOf(','));
        }
        final String[] split = value.split("-");
        if (split.length == 2) {
            final int min = parseInteger(split[0], defaultMin);
            final int max = parseInteger(split[1], defaultMax);
            return new int[]{Math.min(min, max), Math.max(min, max)};
        }

        final int single = parseInteger(value, defaultMin);
        return new int[]{single, single};
    }

    private int parseInteger(final Object raw, final int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private double parseDouble(final Object raw, final double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (final NumberFormatException exception) {
            return fallback;
        }
    }

    private Material parseMaterial(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String normalized = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        return Material.matchMaterial(normalized, false);
    }

    private Sound parseSound(
            final String raw,
            final String path,
            final String blockId,
            final ValidationReport report
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final Sound resolved = Registry.SOUNDS.get(NamespacedKey.minecraft(raw.trim().toLowerCase(Locale.ROOT)));
        if (resolved == null) {
            report.warn("Block '" + blockId + "' has invalid " + path + ": " + raw);
        }
        return resolved;
    }

    private String rawMessage(final String path, final String fallback) {
        final String lang = this.plugin.getConfig().getString("lang", "en-us").toLowerCase(Locale.ROOT);
        final YamlConfiguration selected = this.languages.get(lang + ".yml");
        if (selected == null) {
            return fallback;
        }
        return selected.getString(path, fallback);
    }

    private Component colorizeComponent(final String value) {
        return TextColorUtil.toComponent(value);
    }

    private void ensureResourceFolder(final String folderName) {
        final File folder = new File(this.plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        if ("blocks".equals(folderName)) {
            saveResourceIfMissing("blocks/exampleEntity.yml");
        }
        if ("drops".equals(folderName)) {
            saveResourceIfMissing("drops/exampleDrops.yml");
        }
        if ("tools".equals(folderName)) {
            saveResourceIfMissing("tools/exampleTools.yml");
        }
        if ("lang".equals(folderName)) {
            saveResourceIfMissing("lang/en-us.yml");
            saveResourceIfMissing("lang/id-id.yml");
            saveResourceIfMissing("lang/ja-jp.yml");
        }
    }

    private void saveResourceIfMissing(final String resourcePath) {
        final File outFile = new File(this.plugin.getDataFolder(), resourcePath);
        if (!outFile.exists()) {
            this.plugin.saveResource(resourcePath, false);
        }
    }

    public long interactionThrottleMs() {
        return this.interactionThrottleMs;
    }

    public static final class ValidationReport {

        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public static ValidationReport empty() {
            return new ValidationReport();
        }

        public void warn(final String message) {
            this.warnings.add(message);
        }

        public void error(final String message) {
            this.errors.add(message);
        }

        public List<String> warnings() {
            return List.copyOf(this.warnings);
        }

        public List<String> errors() {
            return List.copyOf(this.errors);
        }

        public int warningCount() {
            return this.warnings.size();
        }

        public int errorCount() {
            return this.errors.size();
        }
    }
}
