package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.config.ConfigSectionParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ConfigSectionParserRegistry}.
 */
public final class ConfigSectionParserRegistryImpl implements ConfigSectionParserRegistry {

    private final Map<String, ConfigSectionParser> parsers = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull final String type, @NotNull final ConfigSectionParser parser) {
        final ConfigSectionParser previous = this.parsers.putIfAbsent(type, parser);
        if (previous != null) {
            throw new IllegalArgumentException(
                "Config section parser '" + type + "' is already registered."
            );
        }
    }

    @Override
    public void unregister(@NotNull final String type) {
        this.parsers.remove(type);
    }

    @Override
    @Nullable
    public ConfigSectionParser getParser(@NotNull final String type) {
        return this.parsers.get(type);
    }

    @Override
    public boolean isRegistered(@NotNull final String type) {
        return this.parsers.containsKey(type);
    }

    @Override
    @NotNull
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(this.parsers.keySet());
    }
}
