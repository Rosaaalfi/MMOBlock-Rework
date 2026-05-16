package me.chyxelmc.mmoblock.config;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.DisplayLine;
import me.chyxelmc.mmoblock.model.NodeDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NodeConfigService {

    private final MMOBlock plugin;
    private final Map<String, NodeDefinition> nodeDefinitions = new HashMap<>();
    private ValidationReport lastNodeReport = ValidationReport.empty();

    public NodeConfigService(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    public int reloadNodes() {
        final ValidationReport report = ValidationReport.empty();
        this.nodeDefinitions.clear();
        ensureResourceFolder("nodes");
        final File folder = new File(this.plugin.getDataFolder(), "nodes");
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

                final ConfigurationSection itemSection = section.getConfigurationSection("item");
                final String itemName = itemSection != null ? itemSection.getString("name") : null;
                final Material itemMaterial = itemSection != null ? parseMaterial(itemSection.getString("material")) : null;
                if (itemSection != null && itemMaterial == null) {
                    report.warn("Node '" + key + "' has invalid item.material.");
                }

                final List<String> listBlocks = section.getStringList("listBlocks").stream()
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .toList();
                if (listBlocks.isEmpty()) {
                    report.warn("Node '" + key + "' has no listBlocks configured.");
                }

                final int maxBlocks = Math.max(1, section.getInt("maxBlocks", Math.max(1, listBlocks.size())));
                final ConfigurationSection randomLocation = section.getConfigurationSection("randomLocation");
                final boolean randomLocationEnabled = randomLocation != null && randomLocation.getBoolean("enabled", false);
                final double randomLocationRadius = randomLocationEnabled ? Math.max(0.0D, randomLocation.getDouble("radius", 0.0D)) : 0.0D;
                final boolean randomLocationClosest = randomLocation != null && randomLocation.getBoolean("closest", false);
                final double randomLocationCenterDistance = randomLocation != null
                        ? Math.max(0.0D, randomLocation.getDouble("center", 1.0D))
                        : 1.0D;

                final ConfigurationSection blockList = section.getConfigurationSection("blockList");
                final String blockListActive = blockList != null ? blockList.getString("active", "{block_name} Active") : "{block_name} Active";
                final String blockListDead = blockList != null ? blockList.getString("dead", "{block_name} {respawn_times}s") : "{block_name} {respawn_times}s";

                final ConfigurationSection displaySection = section.getConfigurationSection("display");
                final double displayHeight = displaySection != null
                        ? displaySection.getDouble("displayHeight", section.getDouble("displayHeight", 1.6D))
                        : section.getDouble("displayHeight", 1.6D);
                final List<DisplayLine> displayLines = parseDisplayLines(section, key, report);

                this.nodeDefinitions.put(
                        key.toLowerCase(Locale.ROOT),
                        new NodeDefinition(
                                key,
                                listBlocks,
                                maxBlocks,
                                randomLocationEnabled,
                                randomLocationRadius,
                                randomLocationClosest,
                                randomLocationCenterDistance,
                                blockListActive,
                                blockListDead,
                                displayHeight,
                                displayLines,
                                itemName,
                                itemMaterial
                        )
                );
                loaded++;
            }
        }
        this.lastNodeReport = report;
        return loaded;
    }

    public NodeDefinition findNode(final String id) {
        if (id == null) {
            return null;
        }
        return this.nodeDefinitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> nodeIds() {
        return Collections.unmodifiableSet(this.nodeDefinitions.keySet());
    }

    public ValidationReport lastNodeReport() {
        return this.lastNodeReport;
    }

    private List<DisplayLine> parseDisplayLines(
            final ConfigurationSection section,
            final String nodeId,
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
            report.warn("Node '" + nodeId + "' has no display lines.");
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

    private String valueAsString(final Object primary, final Object fallback, final String key) {
        final Object rawPrimary = resolveValue(primary, key);
        final Object raw = rawPrimary != null ? rawPrimary : resolveValue(fallback, key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean bool) {
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

    private void ensureResourceFolder(final String folderName) {
        final File folder = new File(this.plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        if ("nodes".equals(folderName)) {
            saveResourceIfMissing("nodes/exampleNodes.yml");
        }
    }

    private void saveResourceIfMissing(final String resourcePath) {
        final File outFile = new File(this.plugin.getDataFolder(), resourcePath);
        if (!outFile.exists()) {
            this.plugin.saveResource(resourcePath, false);
        }
    }

    private Material parseMaterial(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String normalized = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        return Material.matchMaterial(normalized, false);
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

