package me.chyxelmc.mmoblock.api;

import me.chyxelmc.mmoblock.api.registry.*;
import me.chyxelmc.mmoblock.api.service.BlockService;
import me.chyxelmc.mmoblock.api.service.NodeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main API entry point for MMOBlock.
 *
 * <p>Third-party addon plugins should obtain the API instance via {@link #get()}
 * and use it to access services and extension registries.</p>
 *
 * <h3>Basic Usage</h3>
 * <pre>{@code
 * MMOBlockApi api = MMOBlockApi.get();
 * if (api != null) {
 *     // Access block service
 *     api.getBlockService().placeBlock("my_block", world, x, y, z, "north");
 *
 *     // Register custom drop handler
 *     api.getDropHandlerRegistry().register("myplugin:custom_drop", context -> {
 *         // handle custom drop
 *     });
 *
 *     // Register custom item resolver
 *     api.getItemResolverRegistry().register(new MyPluginItemResolver());
 * }
 * }</pre>
 */
public interface MMOBlockApi {

    /**
     * Get the block service for managing placed blocks.
     */
    @NotNull
    BlockService getBlockService();

    /**
     * Get the node service for managing placed nodes.
     * May return null if the node system is disabled.
     */
    @Nullable
    NodeService getNodeService();

    /**
     * Get the registry for custom drop handlers.
     * Third-party plugins can register handlers for {@code CUSTOM} drop types here.
     */
    @NotNull
    DropHandlerRegistry getDropHandlerRegistry();

    /**
     * Get the registry for custom condition evaluators.
     * Third-party plugins can register custom condition types here.
     */
    @NotNull
    ConditionEvaluatorRegistry getConditionEvaluatorRegistry();

    /**
     * Get the registry for custom model renderers.
     * Third-party plugins can register custom block model types here.
     */
    @NotNull
    ModelRendererRegistry getModelRendererRegistry();

    /**
     * Get the registry for custom item resolvers.
     * Third-party plugins can register custom item/plugin resolvers here.
     */
    @NotNull
    ItemResolverRegistry getItemResolverRegistry();

    /**
     * Get the registry for custom config section parsers.
     * Third-party plugins can register parsers for custom YAML sections here.
     */
    @NotNull
    ConfigSectionParserRegistry getConfigSectionParserRegistry();

    /**
     * Get the global MMOBlockApi instance.
     *
     * @return the API instance, or null if MMOBlock is not enabled
     */
    @Nullable
    static MMOBlockApi get() {
        return ApiProvider.getApi();
    }
}
