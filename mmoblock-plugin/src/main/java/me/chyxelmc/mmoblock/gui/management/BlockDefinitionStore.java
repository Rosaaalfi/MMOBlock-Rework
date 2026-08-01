package me.chyxelmc.mmoblock.gui.management;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Persists the small, GUI-editable portion of block definition YAML. */
final class BlockDefinitionStore {
    private final MMOBlock plugin;
    private final BlockConfigLoader loader;

    BlockDefinitionStore(final MMOBlock plugin, final BlockConfigLoader loader) {
        this.plugin = plugin;
        this.loader = loader;
    }

    String normalizedId(final String name) {
        final String separated = name.trim().replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        final String[] words = separated.split("[^A-Za-z0-9]+");
        final StringBuilder id = new StringBuilder();
        for (final String word : words) {
            if (word.isEmpty()) continue;
            final String lower = word.toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                id.append(lower);
            } else {
                id.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
            }
        }
        return id.toString();
    }

    boolean exists(final String name) {
        final String id = normalizedId(name);
        final File file = new File(new File(this.plugin.getDataFolder(), "blocks"), id + ".yml");
        return id.isEmpty() || this.loader.findBlock(id) != null || file.exists();
    }

    void create(final String name) throws IOException {
        final String id = normalizedId(name);
        final File file = new File(new File(this.plugin.getDataFolder(), "blocks"), id + ".yml");
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(id + ".item.name", name.trim());
        yaml.set(id + ".item.material", Material.IRON_ORE.name().toLowerCase(Locale.ROOT));
        yaml.set(id + ".respawnTime", 60L);
        yaml.set(id + ".modelType.block.enabled", true);
        yaml.set(id + ".modelType.block.type", "vanilla");
        yaml.set(id + ".modelType.block.material", "minecraft:iron_ore");
        save(yaml, file);
    }

    void updateName(final String id, final String name) throws IOException {
        final LocatedDefinition located = locate(id);
        located.yaml().set(located.key() + ".name", null);
        located.yaml().set(located.key() + ".item.name", name.trim());
        save(located.yaml(), located.file());
    }

    void updateMaterial(final String id, final Material material) throws IOException {
        final LocatedDefinition located = locate(id);
        final String value = material.name().toLowerCase(Locale.ROOT);
        located.yaml().set(located.key() + ".item.material", value);
        located.yaml().set(located.key() + ".modelType.block.enabled", true);
        located.yaml().set(located.key() + ".modelType.block.type", "vanilla");
        located.yaml().set(located.key() + ".modelType.block.material", "minecraft:" + value);
        save(located.yaml(), located.file());
    }

    void updateRespawnTime(final String id, final long seconds) throws IOException {
        final LocatedDefinition located = locate(id);
        located.yaml().set(located.key() + ".respawnTime", seconds);
        save(located.yaml(), located.file());
    }

    void reload() {
        this.loader.reloadBlocks();
    }

    Object value(final String id, final String path) throws IOException {
        final LocatedDefinition located = locate(id);
        return located.yaml().get(located.key() + "." + path);
    }

    String string(final String id, final String path, final String fallback) {
        try {
            final Object value = value(id, path);
            return value == null ? fallback : String.valueOf(value);
        } catch (final IOException ignored) {
            return fallback;
        }
    }

    boolean bool(final String id, final String path) {
        return Boolean.parseBoolean(string(id, path, "false"));
    }

    List<String> strings(final String id, final String path) {
        try {
            final LocatedDefinition located = locate(id);
            return located.yaml().getStringList(located.key() + "." + path);
        } catch (final IOException ignored) {
            return List.of();
        }
    }

    List<Map<?, ?>> maps(final String id, final String path) {
        try {
            final LocatedDefinition located = locate(id);
            return located.yaml().getMapList(located.key() + "." + path);
        } catch (final IOException ignored) {
            return List.of();
        }
    }

    void set(final String id, final String path, final Object value) throws IOException {
        final LocatedDefinition located = locate(id);
        located.yaml().set(located.key() + "." + path, value);
        save(located.yaml(), located.file());
        reload();
    }

    boolean schematicExists(final String path) {
        if (path == null || path.isBlank()) return false;
        final File folder = new File(new File(this.plugin.getDataFolder(), "models"), "schematics");
        return new File(folder, path).isFile() || new File(folder, path + ".schem").isFile()
                || new File(folder, path + ".schematic").isFile();
    }

    private LocatedDefinition locate(final String id) throws IOException {
        final File folder = new File(this.plugin.getDataFolder(), "blocks");
        final File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (final File file : files) {
                final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                for (final String key : yaml.getKeys(false)) {
                    final ConfigurationSection section = yaml.getConfigurationSection(key);
                    if (section != null && key.equalsIgnoreCase(id)) return new LocatedDefinition(file, yaml, key);
                }
            }
        }
        throw new IOException("Block definition not found: " + id);
    }

    private void save(final YamlConfiguration yaml, final File file) throws IOException {
        final File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create " + parent);
        yaml.save(file);
    }

    private record LocatedDefinition(File file, YamlConfiguration yaml, String key) { }
}
