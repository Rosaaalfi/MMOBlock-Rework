package me.chyxelmc.mmoblock.command;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.config.NodeConfigLoader;
import me.chyxelmc.mmoblock.runtime.BlockRuntimeService;
import me.chyxelmc.mmoblock.runtime.NodeRuntimeService;
import me.chyxelmc.mmoblock.runtime.RuntimeCoordinator;
import me.chyxelmc.mmoblock.utils.CustomItemUtil;

/**
 * Immutable context object that carries all shared dependencies to every
 * {@link SubCommand} implementation.
 * <p>
 * Using a single context record eliminates the need for each subcommand
 * to carry its own constructor-injected fields and makes adding new
 * dependencies a single-line change.
 * </p>
 *
 * @param plugin                the main plugin instance
 * @param configService         block configuration loader
 * @param nodeConfigService     node configuration loader
 * @param runtimeService        block runtime service
 * @param nodeRuntimeService    node runtime service (may be null on some platforms)
 * @param runtimeCoordinator    runtime coordinator for persistence restore
 * @param customItemUtil        utility for creating custom items
 */
public record CommandContext(
        MMOBlock plugin,
        BlockConfigLoader configService,
        NodeConfigLoader nodeConfigService,
        BlockRuntimeService runtimeService,
        NodeRuntimeService nodeRuntimeService,
        RuntimeCoordinator runtimeCoordinator,
        CustomItemUtil customItemUtil
) {
}
