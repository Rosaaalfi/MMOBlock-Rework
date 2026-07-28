package me.chyxelmc.mmoblock.runtime.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel;
import me.chyxelmc.mmoblock.model.BlockDefinitionModel.ConditionDefinition;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.DISPLAY_ACTIVE;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.I18N_SEPARATOR;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.PH_MAX_PROGRESS;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.PH_RESPAWN_TIME;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.STATE_ACTIVE;
import me.chyxelmc.mmoblock.utils.ConditionEvaluator;
import me.chyxelmc.mmoblock.utils.HologramAnimationUtil;
import me.chyxelmc.mmoblock.utils.TextColor;

final class HologramPlaceholderResolver {

    private static final Pattern PROGRESS_SPECIFIC = Pattern.compile("%mmoblock_progress_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");
    private static final Pattern MAX_SPECIFIC = Pattern.compile("%mmoblock_max_progress_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");
    private static final Pattern RESPAWN_SPECIFIC = Pattern.compile("%mmoblock_respawn_time_([0-9a-fA-F\\-]+)(?:_([^_%]+)_(-?\\d+)_(-?\\d+)_(-?\\d+))?%");

    private final MMOBlock plugin;

    HologramPlaceholderResolver(final MMOBlock plugin) {
        this.plugin = plugin;
    }

    List<NmsAdapter.HologramLine> resolveViewerPlaceholders(
            final Player viewer,
            final List<NmsAdapter.HologramLine> source,
            final HologramPlaceholderValues placeholderValues,
            final Location baseLocation,
            final UUID hologramUniqueId,
            final BlockDefinitionModel definition,
            final long animationStep
    ) {
        if (source.isEmpty()) {
            return source;
        }
        boolean changed = false;
        final List<NmsAdapter.HologramLine> resolved = new ArrayList<>(source.size());
        final boolean viewerLookingAt = playerIsLookingAt(viewer, baseLocation);
        final long step = animationStep >= 0 ? animationStep : HologramRuntimeService.currentAnimationStep();

        for (final NmsAdapter.HologramLine line : source) {
            if (line.type() != NmsAdapter.HologramLineType.TEXT || line.text() == null || line.text().isEmpty()) {
                resolved.add(line);
                continue;
            }
            String text = line.text();

            text = replaceEntitySpecificPlaceholder(text, PROGRESS_SPECIFIC, hologramUniqueId, baseLocation, viewerLookingAt,
                    String.valueOf(placeholderValues.progress()));
            text = replaceEntitySpecificPlaceholder(text, MAX_SPECIFIC, hologramUniqueId, baseLocation, viewerLookingAt,
                    String.valueOf(placeholderValues.maxProgress()));
            final String respawnReplacement = STATE_ACTIVE.equals(placeholderValues.stateName())
                    ? DISPLAY_ACTIVE
                    : String.valueOf(placeholderValues.respawnTimeSeconds());
            text = replaceEntitySpecificPlaceholder(text, RESPAWN_SPECIFIC, hologramUniqueId, baseLocation, viewerLookingAt,
                    respawnReplacement);

            text = replaceCommonPlaceholders(text, placeholderValues, viewerLookingAt);
            text = replaceI18nPlaceholders(viewer, placeholderValues, text, step);
            text = replaceConditionPlaceholders(viewer, definition, text, step);
            text = replaceI18nPlaceholders(viewer, placeholderValues, text, step);
            text = replaceCommonPlaceholders(text, placeholderValues, viewerLookingAt);

            final String replaced = this.plugin.applyHologramPlaceholderApi(
                    viewer,
                    text,
                    placeholderValues.progress(),
                    placeholderValues.maxProgress(),
                    placeholderValues.respawnTimeSeconds()
            );
            if (!replaced.equals(line.text())) {
                changed = true;
            }
            resolved.add(NmsAdapter.HologramLine.text(replaced, line.offsetY()));
        }
        return changed ? resolved : source;
    }

    private static String replaceCommonPlaceholders(
            String text,
            final HologramPlaceholderValues placeholderValues,
            final boolean viewerLookingAt
    ) {
        if (viewerLookingAt) {
            text = text.replace("%mmoblock_progress%", String.valueOf(placeholderValues.progress()));
            text = text.replace(PH_MAX_PROGRESS, String.valueOf(placeholderValues.maxProgress()));
            return text.replace(PH_RESPAWN_TIME, STATE_ACTIVE.equals(placeholderValues.stateName())
                    ? DISPLAY_ACTIVE
                    : String.valueOf(placeholderValues.respawnTimeSeconds()));
        }
        text = text.replace(PH_MAX_PROGRESS, String.valueOf(placeholderValues.maxProgress()));
        if (STATE_ACTIVE.equals(placeholderValues.stateName())) {
            text = text.replace(PH_RESPAWN_TIME, DISPLAY_ACTIVE);
        }
        return text;
    }

    private String replaceConditionPlaceholders(
            final Player viewer,
            final BlockDefinitionModel definition,
            final String input,
            final long animationStep
    ) {
        if (definition == null || definition.conditions() == null || definition.conditions().isEmpty()) {
            return input;
        }
        final Matcher matcher = HologramRuntimeService.CONDITION_PATTERN.matcher(input);
        final StringBuffer sb = new StringBuffer();
        boolean any = false;
        while (matcher.find()) {
            any = true;
            final int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (final NumberFormatException exception) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            final ConditionDefinition condition = findCondition(definition.conditions(), id);
            if (condition == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            final boolean met = ConditionEvaluator.isMet(this.plugin, viewer, condition);
            String replacement = ConditionEvaluator.resolvePlaceholderText(this.plugin, viewer, condition, met);
            if (replacement == null) {
                replacement = "";
            }
            final String animated = HologramAnimationUtil.resolveAnimations(replacement, animationStep);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(TextColor.toLegacySection(animated)));
        }
        if (!any) {
            return input;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static ConditionDefinition findCondition(final List<ConditionDefinition> conditions, final int id) {
        for (final ConditionDefinition condition : conditions) {
            if (condition != null && condition.id() == id) {
                return condition;
            }
        }
        return null;
    }

    private static String replaceEntitySpecificPlaceholder(
            final String input,
            final Pattern pattern,
            final UUID hologramUniqueId,
            final Location baseLocation,
            final boolean viewerLookingAt,
            final String replacementWhenMatched
    ) {
        final Matcher matcher = pattern.matcher(input);
        final StringBuffer sb = new StringBuffer();
        boolean any = false;
        while (matcher.find()) {
            any = true;
            if (matchesEntity(matcher, hologramUniqueId, baseLocation) && viewerLookingAt) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacementWhenMatched));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        if (!any) {
            return input;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean matchesEntity(final Matcher matcher, final UUID hologramUniqueId, final Location baseLocation) {
        try {
            final String id = matcher.group(1);
            final String world = matcher.group(2);
            final String sx = matcher.group(3);
            final String sy = matcher.group(4);
            final String sz = matcher.group(5);
            if (id == null || !id.equalsIgnoreCase(hologramUniqueId.toString())) {
                return false;
            }
            if (world == null || sx == null || sy == null || sz == null) {
                return true;
            }
            final String baseWorld = baseLocation.getWorld() == null ? "" : baseLocation.getWorld().getName();
            return world.equals(baseWorld)
                    && baseLocation.getBlockX() == Integer.parseInt(sx)
                    && baseLocation.getBlockY() == Integer.parseInt(sy)
                    && baseLocation.getBlockZ() == Integer.parseInt(sz);
        } catch (final Exception ignored) {
            return false;
        }
    }

    /**
     * Resolve {@code {i18n:key|||default}} placeholders per-player using the TranslationService.
     * <p>
     * Format: {@code {i18n:translation_key|||fallback_text}}
     * The {@code |||} separates the translation key from the fallback/default text.
     * </p>
     * <p>
     * This method also passes through all known hologram placeholder values
     * ({@code {progress}}, {@code {needed}}, {@code {progress_bar}},
     * {@code {respawn}}, {@code {respawn_time}}) so that i18n keys used in
     * holograms (like {@code blocks.mining_progress}) have their placeholders
     * correctly resolved. Without this, tokens such as {@code {progress_bar}}
     * remain as literal text in the translated output.
     * </p>
     * <p>
     * Because {@link HologramPacketLineFactory#toPacketLines} already runs
     * {@link HologramAnimationUtil#resolveAnimations} and
     * {@link TextColor#toLegacySection} <em>before</em> this method, any
     * MiniMessage colour tags ({@code <color:red>}), animation tags
     * ({@code <anim:wave>}), or legacy {@code &} codes in the translated
     * output would otherwise pass through as literal text. This method
     * therefore applies both passes to the translated string so that the
     * Minecraft client renders them correctly.
     * </p>
     * <p>
     * Uses brace-counting instead of the {@link HologramRuntimeService#I18N_PATTERN}
     * regex to correctly handle nested {@code {progress}} / {@code {needed}} /
     * {@code {progress_bar}} placeholders inside the default text.
     * </p>
     */
    private String replaceI18nPlaceholders(
            final Player viewer,
            final HologramPlaceholderValues values,
            final String input,
            final long animationStep
    ) {
        if (input == null || !input.contains(HologramRuntimeService.I18N_PREFIX)) {
            return input;
        }

        // Build a placeholder map from the hologram context so that i18n keys
        // (e.g. blocks.mining_progress) can use {progress}, {needed}, {progress_bar}, {respawn}.
        final java.util.Map<String, String> hologramPlaceholders = new java.util.HashMap<>();
        hologramPlaceholders.put("%mmoblock_progress%", String.valueOf(values.progress()));
        hologramPlaceholders.put(PH_MAX_PROGRESS, String.valueOf(values.maxProgress()));
        hologramPlaceholders.put("{progress}", String.valueOf(values.progress()));
        hologramPlaceholders.put("{needed}", String.valueOf(values.maxProgress()));
        if (values.progressBar() != null) {
            hologramPlaceholders.put("{progress_bar}", values.progressBar());
        }
        final String respawnStr = STATE_ACTIVE.equals(values.stateName())
                ? DISPLAY_ACTIVE
                : String.valueOf(values.respawnTimeSeconds());
        hologramPlaceholders.put(PH_RESPAWN_TIME, respawnStr);
        hologramPlaceholders.put("{respawn}", respawnStr);
        hologramPlaceholders.put("{respawn_time}", respawnStr);

        // Use brace-counting to correctly handle nested {placeholders} inside
        // the default text — the I18N_PATTERN regex with [^}]+ would stop at
        // the first } from e.g. {progress}.
        final StringBuilder sb = new StringBuilder(input.length() + 64);
        int cursor = 0;
        boolean any = false;
        while (true) {
            final int start = input.indexOf(HologramRuntimeService.I18N_PREFIX, cursor);
            if (start < 0) {
                sb.append(input, cursor, input.length());
                break;
            }
            any = true;
            sb.append(input, cursor, start);

            final int end = findI18nEnd(input, start);
            if (end < 0) {
                // No matching close brace — treat rest as literal
                sb.append(input, start, input.length());
                break;
            }

            // Extract raw content between {i18n: and its matching }
            final String raw = input.substring(start + HologramRuntimeService.I18N_PREFIX.length(), end);
            final int sep = raw.indexOf(I18N_SEPARATOR);
            final String key = (sep >= 0) ? raw.substring(0, sep) : raw;
            final String defaultText = (sep >= 0) ? raw.substring(sep + I18N_SEPARATOR.length()) : "";

            String translated = this.plugin.translationService().translate(viewer, key, defaultText, hologramPlaceholders);
            // Process animation, colour, and legacy & codes in the translated
            // output because toPacketLines already ran these passes before this
            // method runs. Without this, tags from translations appear as literal text.
            translated = HologramAnimationUtil.resolveAnimations(translated, animationStep);
            translated = TextColor.toLegacySection(translated);
            sb.append(translated);

            cursor = end + 1;
        }
        if (!any) {
            return input;
        }
        return sb.toString();
    }

    private static int findI18nEnd(final String input, final int start) {
        final int contentStart = start + HologramRuntimeService.I18N_PREFIX.length();
        final int separator = input.indexOf(I18N_SEPARATOR, contentStart);
        final int firstEnd = input.indexOf('}', contentStart);
        if (separator < 0) {
            return firstEnd;
        }
        final int defaultEnd = input.indexOf('}', separator + I18N_SEPARATOR.length());
        if (defaultEnd < 0) {
            return -1;
        }
        if (defaultEnd + 1 < input.length() && input.charAt(defaultEnd + 1) == '}') {
            return defaultEnd + 1;
        }
        return defaultEnd;
    }

    private static boolean playerIsLookingAt(final Player player, final Location target) {
        if (player == null || target == null) {
            return false;
        }
        try {
            final Vector eye = player.getEyeLocation().toVector();
            final Vector dir = player.getEyeLocation().getDirection().normalize();
            final Vector toTarget = target.toVector().subtract(eye);
            final double projection = toTarget.dot(dir);
            if (projection < 0 || projection > 20.0D) {
                return false;
            }
            final Vector closest = eye.clone().add(dir.multiply(projection));
            return closest.distanceSquared(target.toVector()) <= 2.25D;
        } catch (final Exception ignored) {
            return false;
        }
    }
}
