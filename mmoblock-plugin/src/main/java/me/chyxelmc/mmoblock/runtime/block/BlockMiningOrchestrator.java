package me.chyxelmc.mmoblock.runtime.block;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.config.BlockConfigLoader;
import me.chyxelmc.mmoblock.ecs.system.PersistenceSystem;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ConditionDefinition;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ToolAction;
import me.chyxelmc.mmoblock.model.PlacedBlockModel;
import me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService;
import me.chyxelmc.mmoblock.runtime.interaction.BlockInteractionOrchestrator;
import me.chyxelmc.mmoblock.runtime.visual.BlockModelApplier;
import me.chyxelmc.mmoblock.runtime.visual.BlockVisualSyncService;
import me.chyxelmc.mmoblock.utils.ConditionEvaluator;
import me.chyxelmc.mmoblock.utils.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public final class BlockMiningOrchestrator {

    private final MMOBlock plugin;
    private final BlockConfigLoader blockConfigService;
    private final me.chyxelmc.mmoblock.i18n.TranslationService translationService;
    private final PersistenceSystem persistenceSystem;
    private final MiningProgressTracker miningSystem;
    private final DropService dropSystem;
    private final BlockLifecycleState lifecycleSystem;
    private final BlockVisualSyncService visualSyncSystem;
    private final HologramRuntimeService hologramRuntimeService;
    private final BlockModelApplier modelApplier;
    private final BlockInteractionOrchestrator interactionOrchestrator;
    private final BlockEventDispatcher eventDispatcher;
    private final Predicate<UUID> transientBlockPredicate;
    private final Predicate<UUID> suppressDeadHologramPredicate;
    private final RespawnScheduler respawnScheduler;
    private final Particle breakParticle;

    public BlockMiningOrchestrator(
            final MMOBlock plugin,
            final BlockConfigLoader blockConfigService,
            final me.chyxelmc.mmoblock.i18n.TranslationService translationService,
            final PersistenceSystem persistenceSystem,
            final MiningProgressTracker miningSystem,
            final DropService dropSystem,
            final BlockLifecycleState lifecycleSystem,
            final BlockVisualSyncService visualSyncSystem,
            final HologramRuntimeService hologramRuntimeService,
            final BlockModelApplier modelApplier,
            final BlockInteractionOrchestrator interactionOrchestrator,
            final BlockEventDispatcher eventDispatcher,
            final Predicate<UUID> transientBlockPredicate,
            final Predicate<UUID> suppressDeadHologramPredicate,
            final RespawnScheduler respawnScheduler,
            final Particle breakParticle
    ) {
        this.plugin = plugin;
        this.blockConfigService = blockConfigService;
        this.translationService = translationService;
        this.persistenceSystem = persistenceSystem;
        this.miningSystem = miningSystem;
        this.dropSystem = dropSystem;
        this.lifecycleSystem = lifecycleSystem;
        this.visualSyncSystem = visualSyncSystem;
        this.hologramRuntimeService = hologramRuntimeService;
        this.modelApplier = modelApplier;
        this.interactionOrchestrator = interactionOrchestrator;
        this.eventDispatcher = eventDispatcher;
        this.transientBlockPredicate = transientBlockPredicate;
        this.suppressDeadHologramPredicate = suppressDeadHologramPredicate;
        this.respawnScheduler = respawnScheduler;
        this.breakParticle = breakParticle;
    }

    public Component processMiningClick(final PlacedBlockModel block, final Player player, final String clickType) {
        if (!this.lifecycleSystem.isActive(block)) {
            return translate(player, "blocks.not_active", "&c[MMOBlock] Block is not active.");
        }

        final BlockDefinitionModel definition = this.blockConfigService.findBlock(block.type());
        if (definition == null) {
            return translate(player, "blocks.config_missing", "&c[MMOBlock] Block config missing.");
        }

        if (!checkConditions(definition, player)) {
            return Component.empty();
        }

        final ItemStack item = player.getInventory().getItemInMainHand();
        final ToolAction action = this.blockConfigService.resolveToolAction(definition, item, clickType);
        if (action == null) {
            final Component toolNotAllowed = translate(player, "blocks.tool_not_allowed", "&cTool is not allowed for this block.");
            final Component subtitle = translate(player, "blocks.tool_not_allowed_subtitle", "&eGunakan alat yang sesuai");
            player.showTitle(Title.title(toolNotAllowed, subtitle));
            return Component.empty();
        }

        if (isThrottled(block.uniqueId(), player.getUniqueId())) {
            final Component tooFastTitle = translate(player, "blocks.too_fast", "&eHey slow down a bit.");
            final Component tooFastSubtitle = translate(player, "blocks.too_fast_subtitle", "&eHarap tunggu beberapa saat");
            player.showTitle(Title.title(tooFastTitle, tooFastSubtitle));
            return Component.empty();
        }

        playConfiguredSound(player.getWorld(), block, definition.soundOnClick());
        this.modelApplier.playBdEngineAnimation(
                block,
                definition.bdengineOnClickAnimation(),
                definition.bdengineOnClickTimelineLength(),
                definition.bdengineOnClickAnimationMode()
        );
        this.modelApplier.playModelEngineAnimation(
                block,
                definition,
                definition.modelEngineOnClickName(),
                definition.modelEngineOnClickLerpIn(),
                definition.modelEngineOnClickLerpOut(),
                definition.modelEngineOnClickSpeed()
        );
        this.modelApplier.playBetterModelAnimation(block, definition, definition.betterModelOnClickName());

        applyDurability(item, action.decreaseDurability());
        final int progress = this.miningSystem.incrementProgress(block.uniqueId(), player.getUniqueId(), System.currentTimeMillis());
        if (definition.breakAnimation() && !definition.bdengineEnabled()) {
            this.visualSyncSystem.sendBreakAnimation(block, action, progress, false);
        }
        if (definition.particleBreak()) {
            spawnBreakParticles(block, definition);
        }
        this.eventDispatcher.callMineProgress(player, block, definition, clickType, progress, action.clickNeeded());

        if (progress < action.clickNeeded()) {
            showProgressHologram(block, definition, progress, action.clickNeeded());
            return Component.empty();
        }

        this.miningSystem.clearProgress(block.uniqueId(), player.getUniqueId());
        handleBlockBreak(block, definition, action, player);
        return Component.empty();
    }

    public void playConfiguredSound(final World world, final PlacedBlockModel block, final Sound sound) {
        if (world == null || sound == null) {
            return;
        }
        try {
            world.playSound(blockCenterLocation(block, world), sound, 1.0F, 1.0F);
        } catch (final Exception ignored) {
            // expected - cross-version sound fallback
        }
    }

    private void showProgressHologram(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final int progress,
            final int needed
    ) {
        final String progressBar = renderProgressBar(progress, needed);
        this.hologramRuntimeService.showProgress(block, definition, progressBar, progress, needed);
    }

    private boolean checkConditions(final BlockDefinitionModel definition, final Player player) {
        if (definition.conditions() == null || definition.conditions().isEmpty()) {
            return true;
        }
        for (final ConditionDefinition condition : definition.conditions()) {
            if (condition == null) {
                continue;
            }
            final String type = condition.type() == null ? "" : condition.type().toLowerCase(java.util.Locale.ROOT);
            if (!"placeholder".equals(type) && !"placholder".equals(type)) {
                continue;
            }
            if (!ConditionEvaluator.isMet(this.plugin, player, condition)) {
                sendConditionTitle(player, condition);
                return false;
            }
        }
        return true;
    }

    private static final Pattern I18N_PATTERN = Pattern.compile("\\{i18n:([^}]+)\\}");

    private String resolveI18nPlaceholders(final String text, final Player player) {
        if (text == null || !text.contains("{i18n:")) {
            return text;
        }
        final Matcher matcher = I18N_PATTERN.matcher(text);
        final StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            final String content = matcher.group(1);
            final String[] parts = content.split("\\|\\|\\|", 2);
            final String key = parts[0].trim();
            final String defaultText = parts.length > 1 ? parts[1].trim() : "";
            final String translated = this.translationService.translate(player, key, defaultText);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(translated));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void sendConditionTitle(final Player player, final ConditionDefinition condition) {
        if (player == null || condition == null) {
            return;
        }
        String titleRaw = ConditionEvaluator.resolvePlaceholder(this.plugin, player, condition.sendTitle());
        String subtitleRaw = ConditionEvaluator.resolvePlaceholder(this.plugin, player, condition.sendSubtitle());
        titleRaw = resolveI18nPlaceholders(titleRaw, player);
        subtitleRaw = resolveI18nPlaceholders(subtitleRaw, player);
        final Component title = (titleRaw == null || titleRaw.isBlank()) ? Component.empty() : TextColor.toComponent(titleRaw);
        final Component subtitle = (subtitleRaw == null || subtitleRaw.isBlank()) ? Component.empty() : TextColor.toComponent(subtitleRaw);
        if (title.equals(Component.empty()) && subtitle.equals(Component.empty())) {
            return;
        }
        player.showTitle(Title.title(title, subtitle));
    }

    private void handleBlockBreak(
            final PlacedBlockModel block,
            final BlockDefinitionModel definition,
            final ToolAction action,
            final Player player
    ) {
        this.eventDispatcher.callMineComplete(player, block, definition, action.clickType(), action.clickNeeded());
        this.miningSystem.clearAllProgress(block.uniqueId());
        this.dropSystem.executeDrops(block, action, player);
        if (definition.breakAnimation() && !definition.bdengineEnabled()) {
            this.visualSyncSystem.sendBreakAnimation(block, action, action.clickNeeded(), true);
        }
        playConfiguredSound(player.getWorld(), block, definition.soundOnDead());
        this.modelApplier.playModelEngineAnimation(
                block,
                definition,
                definition.modelEngineOnSpawnName(),
                definition.modelEngineOnSpawnLerpIn(),
                definition.modelEngineOnSpawnLerpOut(),
                definition.modelEngineOnSpawnSpeed()
        );
        this.modelApplier.playBetterModelAnimation(block, definition, definition.betterModelOnSpawnName());
        spawnBreakParticles(block, definition);

        this.lifecycleSystem.markRespawning(block);
        if (!this.transientBlockPredicate.test(block.uniqueId())) {
            this.persistenceSystem.persistBlockAsync(block);
        }

        final World world = this.plugin.getServer().getWorld(block.world());
        if (world != null) {
            // Apply dead block model if configured, otherwise clear
            if (this.visualSyncSystem.hasDeadBlockModel(definition)) {
                this.visualSyncSystem.applyDeadBlockModel(block, definition, world);
            } else {
                this.visualSyncSystem.clearRealBlockModel(block, definition, world);
            }
            this.modelApplier.clearSchematicModel(block, world);
            this.modelApplier.clearBdEngineModel(block, world);
            this.modelApplier.clearModelEngineModel(block, world);
            this.modelApplier.clearModelEngineCollision(block, world);
            this.modelApplier.clearBetterModelModel(block, world);
            this.modelApplier.clearBetterModelCollision(block, world);
        }
        this.interactionOrchestrator.despawn(block);
        if (world != null && definition.schematicsEnabled() && definition.schematicsDeadFile() != null && !definition.schematicsDeadFile().isBlank()) {
            this.modelApplier.applySchematicModel(block, definition, world, true);
        }
        if (this.suppressDeadHologramPredicate.test(block.uniqueId())) {
            this.hologramRuntimeService.remove(block);
        } else {
            this.hologramRuntimeService.showDead(block, definition, definition.respawnTimeSeconds());
        }
        final long respawnAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds());
        if (!this.transientBlockPredicate.test(block.uniqueId())) {
            this.persistenceSystem.upsertRespawnAsync(block.uniqueId(), respawnAt);
        }

        if (world != null) {
            this.respawnScheduler.schedule(block, world, TimeUnit.SECONDS.toMillis(definition.respawnTimeSeconds()));
        }
    }

    private boolean isThrottled(final UUID blockId, final UUID playerId) {
        final long now = System.currentTimeMillis();
        final long minDelay = this.blockConfigService.interactionThrottleMs();
        return this.miningSystem.isThrottled(blockId, playerId, now, minDelay);
    }

    private void applyDurability(final ItemStack item, final int decreaseDurability) {
        if (decreaseDurability <= 0) {
            return;
        }

        final Enchantment unbreaking = Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("unbreaking"));
        final int unbreakingLevel = unbreaking != null ? item.getEnchantmentLevel(unbreaking) : 0;
        if (unbreakingLevel > 0 && ThreadLocalRandom.current().nextInt(unbreakingLevel + 1) != 0) {
            return;
        }

        // Wrap integration durability calls in try-catch(Throwable) so that even
        // if a NoClassDefFoundError or other LinkageError occurs during JVM class
        // resolution, the error is caught and we fall through to the next option
        // instead of crashing the event handler.
        try {
            if (me.chyxelmc.mmoblock.api.integration.MMOItemsIntegration.applyCustomDurability(item, decreaseDurability)) {
                return;
            }
        } catch (final Throwable ignored) {
            // Integration class not available or incompatible
        }

        try {
            if (me.chyxelmc.mmoblock.api.integration.CraftEngineIntegration.applyCustomDurability(item, decreaseDurability)) {
                return;
            }
        } catch (final Throwable ignored) {
            // Integration class not available or incompatible
        }

        try {
            if (me.chyxelmc.mmoblock.api.integration.ItemsAdderIntegration.applyCustomDurability(item, decreaseDurability)) {
                return;
            }
        } catch (final Throwable ignored) {
            // Integration class not available or incompatible
        }

        if (item.getType().getMaxDurability() <= 0) {
            return;
        }

        final ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }

        final int nextDamage = damageable.getDamage() + decreaseDurability;
        if (nextDamage >= item.getType().getMaxDurability()) {
            item.setAmount(Math.max(0, item.getAmount() - 1));
            return;
        }

        damageable.setDamage(nextDamage);
        item.setItemMeta(meta);
    }

    private String renderProgressBar(final int progress, final int needed) {
        final int length = Math.max(1, this.plugin.getConfig().getInt("progressbar.barlength", 16));
        final String done = this.plugin.getConfig().getString("progressbar.progressing", "&a|");
        final String left = this.plugin.getConfig().getString("progressbar.noprogress", "&7|");
        final int completed = Math.min(length, (int) Math.round((progress / (double) needed) * length));
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < completed; i++) {
            out.append(done);
        }
        for (int i = completed; i < length; i++) {
            out.append(left);
        }
        return TextColor.ampersandToMiniMessage(out.toString());
    }

    private void spawnBreakParticles(final PlacedBlockModel block, final BlockDefinitionModel definition) {
        final World world = this.plugin.getServer().getWorld(block.world());
        if (world == null) {
            return;
        }

        Material material = definition.particleMaterial();
        if (material == null) {
            material = Material.STONE;
        }

        final Location loc = blockCenterLocation(block, world);
        Object data;
        try {
            data = material.createBlockData();
        } catch (final Exception ex) {
            data = Material.STONE.createBlockData();
        }
        try {
            world.spawnParticle(this.breakParticle, loc, 12, 0.35, 0.35, 0.35, 0.02, data);
        } catch (final IllegalArgumentException ex) {
            try {
                world.spawnParticle(this.breakParticle, loc, 12, 0.35, 0.35, 0.35, 0.02);
            } catch (final Exception ignored) {
                // expected - cross-version particle fallback
            }
        }
    }

    private Location blockCenterLocation(final PlacedBlockModel block, final World world) {
        return new Location(world, block.x() + 0.5D, block.y() + 0.5D, block.z() + 0.5D);
    }

    private Component translate(final Player player, final String key, final String defaultMessage) {
        return translate(player, key, defaultMessage, java.util.Map.of());
    }

    private Component translate(final Player player, final String key, final String defaultMessage,
                                final java.util.Map<String, String> placeholders) {
        if (this.translationService != null) {
            return this.translationService.translateComponent(player, key, defaultMessage, placeholders);
        }
        return this.blockConfigService.messageComponent(key, defaultMessage, placeholders);
    }

    @FunctionalInterface
    public interface RespawnScheduler {
        void schedule(PlacedBlockModel block, World world, long delayMillis);
    }
}
