package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.config.ConfigSectionParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Registry for custom configuration section parsers.
 *
 * <p>Third-party addon plugins can register config section parsers to allow
 * custom sections in MMOBlock's YAML configuration files.</p>
 */
public interface ConfigSectionParserRegistry {

    /**
     * Register a custom config section parser.
     *
     * @param type   the section key identifier (must match the YAML section key,
     *               e.g., {@code "myplugin"})
     * @param parser the parser implementation
     * @throws IllegalArgumentException if the type is already registered
     */
    void register(@NotNull String type, @NotNull ConfigSectionParser parser);

    /**
     * Unregister a previously registered config section parser.
     *
     * @param type the section type to unregister
     */
    void unregister(@NotNull String type);

    /**
     * Get a registered config section parser by its type.
     *
     * @param type the section type
     * @return the parser, or null if not registered
     */
    @Nullable
    ConfigSectionParser getParser(@NotNull String type);

    /**
     * Check if a config section parser type is registered.
     *
     * @param type the section type
     * @return true if registered
     */
    boolean isRegistered(@NotNull String type);

    /**
     * Get all registered config section parser types.
     *
     * @return an unmodifiable set of parser types
     */
    @NotNull
    Set<String> getRegisteredTypes();
}
