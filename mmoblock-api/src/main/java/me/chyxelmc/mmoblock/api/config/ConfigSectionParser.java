package me.chyxelmc.mmoblock.api.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

/**
 * Parses custom configuration sections within MMOBlock YAML files.
 *
 * <p>Third-party addon plugins can register config section parsers to add support
 * for custom blocks, sections, or features within MMOBlock's configuration files
 * (blocks/*.yml, drops/*.yml, tools/*.yml, nodes/*.yml) without modifying
 * MMOBlock's source code.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * MMOBlockApi.get().getConfigSectionParserRegistry()
 *     .register("myplugin", new BackpackConfigParser());
 * }</pre>
 *
 * <h3>Config Example (in blocks/exampleBlock.yml)</h3>
 * <pre>{@code
 * my_backpack_block:
 *   name: "&aBackpack Block"
 *   myplugin:
 *     backpack_type: "mining"
 *     slots: 27
 *     open_sound: "block.chest.open"
 * }</pre>
 *
 * <p>The parser receives the {@code myplugin} section and can read the
 * {@code backpack_type}, {@code slots}, and other custom values.</p>
 *
 * <p>Section parsers are invoked per-section during MMOBlock config reload.
 * The parser type identifier should match the section key in YAML.</p>
 */
@FunctionalInterface
public interface ConfigSectionParser {

    /**
     * Parse a custom configuration section.
     * This is called when the config loader encounters a section with a key
     * matching this parser's registered identifier.
     *
     * @param context the parse context with file info, section, and collector
     */
    void parse(@NotNull ConfigParseContext context);
}
