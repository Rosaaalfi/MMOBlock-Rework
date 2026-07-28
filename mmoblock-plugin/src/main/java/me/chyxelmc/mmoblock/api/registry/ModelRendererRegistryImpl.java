package me.chyxelmc.mmoblock.api.registry;

import me.chyxelmc.mmoblock.api.model.ModelRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ModelRendererRegistry}.
 */
public final class ModelRendererRegistryImpl implements ModelRendererRegistry {

    private final Map<String, ModelRenderer> renderers = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull final String type, @NotNull final ModelRenderer renderer) {
        final ModelRenderer previous = this.renderers.putIfAbsent(type, renderer);
        if (previous != null) {
            throw new IllegalArgumentException(
                "Model renderer '" + type + "' is already registered."
            );
        }
    }

    @Override
    public void unregister(@NotNull final String type) {
        this.renderers.remove(type);
    }

    @Override
    @Nullable
    public ModelRenderer getRenderer(@NotNull final String type) {
        return this.renderers.get(type);
    }

    @Override
    public boolean isRegistered(@NotNull final String type) {
        return this.renderers.containsKey(type);
    }

    @Override
    @NotNull
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(this.renderers.keySet());
    }
}
