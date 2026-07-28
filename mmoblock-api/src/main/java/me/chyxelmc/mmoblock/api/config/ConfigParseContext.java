package me.chyxelmc.mmoblock.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Context passed to a {@link ConfigSectionParser} during YAML loading.
 *
 * <p>Provides the configuration section data along with metadata about
 * which file and block definition the section belongs to.</p>
 *
 * @param fileName     the YAML file name (e.g., {@code "exampleBlock.yml"})
 * @param blockId      the block/node/drop/tool ID that owns this section
 * @param configType   the type of configuration being loaded (e.g., {@code "blocks"}, {@code "drops"}, {@code "tools"}, {@code "nodes"})
 * @param section      the YAML configuration section for this parser
 * @param rawValues    the raw parsed values from the YAML section (Map view)
 */
public record ConfigParseContext(
    @NotNull String fileName,
    @NotNull String blockId,
    @NotNull String configType,
    @NotNull ConfigurationSection section,
    @NotNull Map<String, Object> rawValues
) {
}
