package me.chyxelmc.mmoblock.runtime.hologram;

import me.chyxelmc.mmoblock.MMOBlock;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel;
import me.chyxelmc.mmoblock.domain.BlockDefinitionModel.ConditionDefinition;
import me.chyxelmc.mmoblock.nms.NmsAdapter;
import me.chyxelmc.mmoblock.utils.ConditionEvaluator;
import me.chyxelmc.mmoblock.utils.HologramAnimationUtil;
import me.chyxelmc.mmoblock.utils.TextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.DISPLAY_ACTIVE;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.PH_MAX_PROGRESS;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.PH_RESPAWN_TIME;
import static me.chyxelmc.mmoblock.runtime.hologram.HologramRuntimeService.STATE_ACTIVE;

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
            text = replaceConditionPlaceholders(viewer, definition, text, step);

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
