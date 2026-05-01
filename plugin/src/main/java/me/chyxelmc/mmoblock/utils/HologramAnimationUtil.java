package me.chyxelmc.mmoblock.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HologramAnimationUtil {

    // DecentHolograms-compatible pattern: [<{]#?ANIM:(\w+)(:\S+)?[}>](.*?)[<{]/#?ANIM[}>]
    private static final Pattern LEGACY_ANIMATION_PATTERN = Pattern.compile(
            "(?is)[<{]#?ANIM:(\\w+)(:\\S+)?[}>](.*?)[<{]/#?ANIM[}>]"
    );
    // Also support modern tag style: <anim:name:arg1:arg2>text</anim>
    private static final Pattern MODERN_ANIMATION_PATTERN = Pattern.compile(
            "(?is)<anim:(\\w+)(:[^>]*)?>(.*?)</anim>"
    );
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9A-FK-OR]");

    private static final Map<Character, String> LEGACY_COLOR_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>")
    );

    private static final AnimationManager ANIMATION_MANAGER = new AnimationManager();

    private HologramAnimationUtil() {
    }

    public static boolean containsAnimationTag(final String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return ANIMATION_MANAGER.containsAnimations(input);
    }

    public static String resolveAnimations(final String input, final long step) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ANIMATION_MANAGER.parseTextAnimations(input, step);
    }

    public static long currentSystemStep() {
        return System.currentTimeMillis() / 50L;
    }

    private static String[] parseArgs(final String argsRaw, final boolean legacyFormat) {
        if (argsRaw == null || argsRaw.isBlank()) {
            return new String[0];
        }
        final String payload = argsRaw.startsWith(":") ? argsRaw.substring(1) : argsRaw;
        if (payload.isBlank()) {
            return new String[0];
        }
        // Accept colon, comma or semicolon as argument separators for both legacy and modern
        // formats. Users prefer semicolon in config (e.g. <anim:wave:#001f3f;#00aaff>).
        final String[] split = payload.split("[,:;]");
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].trim();
        }
        return split;
    }

    private static String animateWave(final String text, final long step, final String... args) {
        if (text.isEmpty()) {
            return text;
        }
        final List<int[]> palette = new ArrayList<>();
        if (args != null) {
            for (final String arg : args) {
                final int[] rgb = parseColorToRgb(arg);
                if (rgb != null) {
                    palette.add(rgb);
                }
            }
        }
        // Limit palette to maximum of 5 colors
        if (palette.size() > 5) {
            while (palette.size() > 5) palette.remove(palette.size() - 1);
        }

        if (palette.isEmpty()) {
            palette.add(new int[]{0xFF, 0xAA, 0x00}); // gold
            palette.add(new int[]{0xFF, 0xFF, 0x55}); // yellow
            palette.add(new int[]{0xFF, 0xFF, 0xFF}); // white
        } else if (palette.size() == 1) {
            palette.add(palette.get(0));
        }

        final String stripped = stripVisualColorTokens(text);
        if (stripped.isEmpty()) {
            return text;
        }

        // Smooth moving gradient with mirrored looping (no hard seam on wrap).
        // If only two colors: behave as before (smooth mirrored gradient wave)
        if (palette.size() == 2) {
            final double offset = (step * 0.014D) % 1.0D;
            final String rendered = renderGradientText(stripped, palette, offset);
            return preserveOuterTag(text, rendered);
        }

        // For 3+ colors: rotate between adjacent gradient pairs smoothly.
        // Build sequence of adjacent pairs: c1->c2, c2->c3, ..., cN->c1
        final List<int[]> seq = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) seq.add(palette.get(i));
        final int pairs = seq.size();

        // We want each adjacent pair (c1:c2, c2:c3, ... cN:c1) to run a full wave
        // and then smoothly transition to the next pair. To achieve this we tie the
        // pair index to completed wave cycles and blend only near the end of each
        // cycle to create a smooth morph.
        final int pairCount = seq.size();
        final double charOffsetSpeed = 0.014D; // speed of the per-character gradient movement
        final double offsetPos = (step * charOffsetSpeed);
        final double offset = offsetPos - Math.floor(offsetPos); // 0..1 per-character offset

        // Number of completed cycles (full offset loops) determines the active pair.
        final long completedCycles = (long) Math.floor(offsetPos);
        final int pairIndex = (int) (completedCycles % pairCount);

        // Blend only during the final portion of each cycle to smoothly morph to the next pair.
        final double blendPortion = 0.25D; // last 25% of cycle blends to next pair
        final double cyclePhase = offset; // 0..1 within current cycle
        final double pairLocal = cyclePhase < (1.0D - blendPortion)
                ? 0.0D
                : clamp01((cyclePhase - (1.0D - blendPortion)) / blendPortion);

        final int[] aStart = seq.get(pairIndex);
        final int[] aEnd = seq.get((pairIndex + 1) % pairCount);
        final int[] bStart = seq.get((pairIndex + 1) % pairCount);
        final int[] bEnd = seq.get((pairIndex + 2) % pairCount);

        // During most of the cycle we use the current adjacent pair. Near the end
        // we interpolate the start/end colors into the next pair to create a smooth swap.
        final int[] startRgb = mixRgb(aStart, bStart, pairLocal);
        final int[] endRgb = mixRgb(aEnd, bEnd, pairLocal);

        final List<int[]> renderPalette = new ArrayList<>();
        renderPalette.add(startRgb);
        renderPalette.add(endRgb);

        final String rendered = renderGradientText(stripped, renderPalette, offset);
        return preserveOuterTag(text, rendered);
    }

    private static String animateTypewriter(final String text, final long step, final String... args) {
        if (text.isEmpty()) {
            return text;
        }
        final String stripped = stripVisualColorTokens(text);
        final int visibleChars = getCurrentStep(step, stripped.length(), 3, 20);
        if (visibleChars <= 0) {
            return "";
        }

        // If two colors provided and valid, apply a stable gradient across the full
        // original text, but only reveal the leading visible characters. This keeps
        // gradient positions stable as characters are typed in.
        if (args != null && args.length >= 2) {
            final int[] rgbA = parseColorToRgb(args[0]);
            final int[] rgbB = parseColorToRgb(args[1]);
            if (rgbA != null && rgbB != null) {
                final int len = Math.max(1, stripped.length());
                final StringBuilder out = new StringBuilder(Math.min(stripped.length(), visibleChars) * 14);
                final int cap = Math.min(stripped.length(), visibleChars);
                for (int i = 0; i < cap; i++) {
                    final double t = (len == 1) ? 0.0D : (i / (double) (len - 1));
                    final int r = (int) Math.round(rgbA[0] + (rgbB[0] - rgbA[0]) * t);
                    final int g = (int) Math.round(rgbA[1] + (rgbB[1] - rgbA[1]) * t);
                    final int b = (int) Math.round(rgbA[2] + (rgbB[2] - rgbA[2]) * t);
                    out.append(formatHexTag(new int[]{r, g, b})).append(stripped.charAt(i));
                }
                return preserveOuterTag(text, out.toString());
            }
        }

        // Single-color or invalid colors: apply the color tag (if present) to visible substring
        final String visible = stripped.substring(0, Math.min(stripped.length(), visibleChars));
        if (args == null || args.length == 0) {
            return preserveOuterTag(text, visible);
        }
        final String colorTag = toColorTag(args[0]);
        return preserveOuterTag(text, (colorTag == null ? "" : colorTag) + visible);
    }

    private static String animateBurn(final String text, final long step, final String... args) {
        if (text.isEmpty()) {
            return text;
        }
        // Implement burn animation like DecentHolograms:
        // args[0] = primary (base) color, args[1] = burn color (applied to revealed part)
        // Preserve simple MiniMessage/legacy formatting tokens found in the original
        // so they can be re-applied around the fragments.
        String working = text;
        final StringBuilder special = new StringBuilder();
        // Extract and remove simple MiniMessage tags (e.g. <bold>, <#rrggbb>, <red>)
        final Matcher mini = MINI_TAG_PATTERN.matcher(working);
        while (mini.find()) {
            final String tok = mini.group();
            special.append(tok);
            working = working.replace(tok, "");
        }

        // Remove legacy color/format codes from the working copy and build stripped text
        final String stripped = LEGACY_COLOR_PATTERN.matcher(working).replaceAll("");
        if (stripped.isEmpty()) {
            return text;
        }

        // If not enough args provided, fallback: no animation
        if (args == null || args.length < 2) {
            return preserveOuterTag(text, stripped);
        }

        final int currentStep = getCurrentStep(step, stripped.length(), 2, 40);
        final String start = stripped.substring(0, Math.max(0, Math.min(stripped.length(), currentStep)));
        final String end = stripped.substring(Math.max(0, Math.min(stripped.length(), currentStep)));

        final String primaryTag = toColorTag(args[0]);
        final String secondaryTag = toColorTag(args[1]);
        final String result = (secondaryTag == null ? "" : secondaryTag) + special + start
                + (primaryTag == null ? "" : primaryTag) + special + end;
        return preserveOuterTag(text, result);
    }

    private static String animateColors(final String text, final long step, final String... args) {
        final List<String> colors = new ArrayList<>();
        if (args != null && args.length > 0) {
            for (final String arg : args) {
                final String tag = toColorTag(arg);
                if (tag != null) {
                    colors.add(tag);
                }
            }
        }
        // Limit to max 5 custom colors
        if (colors.size() > 5) {
            while (colors.size() > 5) colors.remove(colors.size() - 1);
        }

        if (colors.isEmpty()) {
            colors.add("<red>");
            colors.add("<gold>");
            colors.add("<yellow>");
            colors.add("<green>");
            colors.add("<aqua>");
            colors.add("<light_purple>");
        }
        final int idx = getCurrentStep(step, colors.size(), 4, 0);
        final String colored = colors.get(Math.max(0, Math.min(colors.size() - 1, idx))) + stripVisualColorTokens(text);
        return preserveOuterTag(text, colored);
    }

    /**
     * If the original input is wrapped in a single outer MiniMessage tag like <gray>...</gray>,
     * preserve that wrapper around the generated animation fragment. This is a best-effort
     * approach for common cases (simple wrappers).
     */
    private static String preserveOuterTag(final String original, final String inner) {
        if (original == null || original.isEmpty()) return inner;
        // match simple wrapper: <tag>...< /tag>
        final Matcher m = Pattern.compile("(?is)^<([a-z0-9_#-]+)>.*</\\1>$").matcher(original.trim());
        if (m.find()) {
            final String tag = m.group(1);
            return "<" + tag + ">" + inner + "</" + tag + ">";
        }
        return inner;
    }

    private static int getCurrentStep(final long step, final int maxSteps, final int speed, final int pause) {
        if (maxSteps <= 0) {
            return 0;
        }
        final long safeSpeed = Math.max(1L, speed);
        final long actualStep = step / safeSpeed;
        final int actualPause = pause <= 0 ? 0 : Math.max(0, pause / (int) safeSpeed);
        final int current = (int) (actualStep % (maxSteps + actualPause));
        return Math.min(current, maxSteps);
    }

    private static String applyGradient(final String text, final int[] from, final int[] to) {
        if (text.length() <= 1) {
            return formatHexTag(from) + text;
        }
        final StringBuilder out = new StringBuilder(text.length() * 14);
        for (int i = 0; i < text.length(); i++) {
            final double t = i / (double) (text.length() - 1);
            final int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
            final int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
            final int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
            out.append(formatHexTag(new int[]{r, g, b})).append(text.charAt(i));
        }
        return out.toString();
    }

    private static String toColorTag(final String raw) {
        if (raw == null) {
            return null;
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("<") && value.endsWith(">")) {
            return value;
        }
        if (value.startsWith("#") && value.length() == 7 && isHex(value.substring(1))) {
            return "<" + value.toLowerCase(Locale.ROOT) + ">";
        }
        if ((value.startsWith("&") || value.startsWith("§")) && value.length() == 2) {
            final char code = Character.toLowerCase(value.charAt(1));
            return LEGACY_COLOR_TAGS.get(code);
        }
        if (value.startsWith("minecraft:")) {
            return normalizeNamedTag(value.substring("minecraft:".length()));
        }
        return normalizeNamedTag(value);
    }

    private static int[] parseColorToRgb(final String raw) {
        if (raw == null) {
            return null;
        }
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("<") && value.endsWith(">") && value.length() >= 3) {
            final String inner = value.substring(1, value.length() - 1);
            if (inner.startsWith("#") && inner.length() == 7 && isHex(inner.substring(1))) {
                final int rgb = Integer.parseInt(inner.substring(1), 16);
                return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
            }
            return namedColorRgb(inner);
        }
        if (value.startsWith("<#") && value.endsWith(">") && value.length() == 9 && isHex(value.substring(2, 8))) {
            final int rgb = Integer.parseInt(value.substring(2, 8), 16);
            return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }
        if (value.startsWith("#") && value.length() == 7 && isHex(value.substring(1))) {
            final int rgb = Integer.parseInt(value.substring(1), 16);
            return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }
        if ((value.startsWith("&") || value.startsWith("§")) && value.length() == 2) {
            return switch (value.charAt(1)) {
                case '0' -> new int[]{0x00, 0x00, 0x00};
                case '1' -> new int[]{0x00, 0x00, 0xAA};
                case '2' -> new int[]{0x00, 0xAA, 0x00};
                case '3' -> new int[]{0x00, 0xAA, 0xAA};
                case '4' -> new int[]{0xAA, 0x00, 0x00};
                case '5' -> new int[]{0xAA, 0x00, 0xAA};
                case '6' -> new int[]{0xFF, 0xAA, 0x00};
                case '7' -> new int[]{0xAA, 0xAA, 0xAA};
                case '8' -> new int[]{0x55, 0x55, 0x55};
                case '9' -> new int[]{0x55, 0x55, 0xFF};
                case 'a' -> new int[]{0x55, 0xFF, 0x55};
                case 'b' -> new int[]{0x55, 0xFF, 0xFF};
                case 'c' -> new int[]{0xFF, 0x55, 0x55};
                case 'd' -> new int[]{0xFF, 0x55, 0xFF};
                case 'e' -> new int[]{0xFF, 0xFF, 0x55};
                case 'f' -> new int[]{0xFF, 0xFF, 0xFF};
                default -> null;
            };
        }
        return namedColorRgb(value);
    }

    private static String formatHexTag(final int[] rgb) {
        return String.format(Locale.ROOT, "<#%02x%02x%02x>", rgb[0], rgb[1], rgb[2]);
    }

    private static boolean isHex(final String value) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            final boolean digit = c >= '0' && c <= '9';
            final boolean lower = c >= 'a' && c <= 'f';
            final boolean upper = c >= 'A' && c <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static String stripVisualColorTokens(final String input) {
        String out = MINI_TAG_PATTERN.matcher(input).replaceAll("");
        out = LEGACY_COLOR_PATTERN.matcher(out).replaceAll("");
        return out;
    }

    private static String normalizeNamedTag(final String raw) {
        final String value = raw.toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "black", "dark_blue", "dark_green", "dark_aqua",
                 "dark_red", "dark_purple", "gold", "gray", "dark_gray",
                 "blue", "green", "aqua", "red", "light_purple",
                 "yellow", "white" -> "<" + value + ">";
            default -> null;
        };
    }

    private static int[] namedColorRgb(final String raw) {
        return switch (raw) {
            case "black" -> new int[]{0x00, 0x00, 0x00};
            case "dark_blue" -> new int[]{0x00, 0x00, 0xAA};
            case "dark_green" -> new int[]{0x00, 0xAA, 0x00};
            case "dark_aqua" -> new int[]{0x00, 0xAA, 0xAA};
            case "dark_red" -> new int[]{0xAA, 0x00, 0x00};
            case "dark_purple" -> new int[]{0xAA, 0x00, 0xAA};
            case "gold" -> new int[]{0xFF, 0xAA, 0x00};
            case "gray" -> new int[]{0xAA, 0xAA, 0xAA};
            case "dark_gray" -> new int[]{0x55, 0x55, 0x55};
            case "blue" -> new int[]{0x55, 0x55, 0xFF};
            case "green" -> new int[]{0x55, 0xFF, 0x55};
            case "aqua" -> new int[]{0x55, 0xFF, 0xFF};
            case "red" -> new int[]{0xFF, 0x55, 0x55};
            case "light_purple" -> new int[]{0xFF, 0x55, 0xFF};
            case "yellow" -> new int[]{0xFF, 0xFF, 0x55};
            case "white" -> new int[]{0xFF, 0xFF, 0xFF};
            default -> null;
        };
    }

    private static int[] interpolatePalette(final List<int[]> palette, final double pos) {
        final int n = palette.size();
        if (n == 1) {
            return palette.get(0);
        }

        // Build a mirrored palette path: c0->c1->...->cn->...->c1->c0
        // so wrap-around remains visually continuous.
        final double wrapped = pos - Math.floor(pos);
        final int forwardSegments = n - 1;
        final int totalSegments = Math.max(1, forwardSegments * 2);
        final double scaled = wrapped * totalSegments;
        final int segment = (int) Math.floor(scaled);
        final double localT = scaled - segment;

        final int[] a;
        final int[] b;
        if (segment < forwardSegments) {
            a = palette.get(segment);
            b = palette.get(segment + 1);
        } else {
            final int back = segment - forwardSegments;
            final int idxA = (n - 1) - back;
            final int idxB = Math.max(0, idxA - 1);
            a = palette.get(idxA);
            b = palette.get(idxB);
        }
        return new int[]{
                lerp(a[0], b[0], localT),
                lerp(a[1], b[1], localT),
                lerp(a[2], b[2], localT)
        };
    }

    private static int lerp(final int a, final int b, final double t) {
        return (int) Math.round(a + (b - a) * t);
    }

    private static String renderGradientText(final String text, final List<int[]> palette, final double offset) {
        if (text.length() == 1) {
            return formatHexTag(interpolatePalette(palette, offset)) + text;
        }
        final StringBuilder out = new StringBuilder(text.length() * 14);
        for (int i = 0; i < text.length(); i++) {
            final double base = i / (double) (text.length() - 1);
            final int[] rgb = interpolatePalette(palette, base + offset);
            out.append(formatHexTag(rgb)).append(text.charAt(i));
        }
        return out.toString();
    }

    private static int[] mixRgb(final int[] from, final int[] to, final double t) {
        final double clamped = clamp01(t);
        return new int[]{
                lerp(from[0], to[0], clamped),
                lerp(from[1], to[1], clamped),
                lerp(from[2], to[2], clamped)
        };
    }

    private static double clamp01(final double value) {
        if (value <= 0.0D) return 0.0D;
        if (value >= 1.0D) return 1.0D;
        return value;
    }

    private abstract static class Animation {

        private final String name;
        private final List<String> aliases;
        private final int speed;
        private final int pause;

        protected Animation(final String name, final int speed, final int pause, final String... aliases) {
            this.name = name;
            this.speed = speed;
            this.pause = pause;
            this.aliases = Arrays.asList(aliases == null ? new String[0] : aliases);
        }

        protected int getCurrentStep(final long step, final int maxSteps) {
            if (maxSteps <= 0) return 0;
            final long actualStep = step / Math.max(1, this.speed);
            final int actualPause = this.pause <= 0 ? 0 : this.pause / Math.max(1, this.speed);
            final int currentStep = (int) (actualStep % (maxSteps + actualPause));
            return Math.min(currentStep, maxSteps);
        }

        public boolean isIdentifier(final String string) {
            return this.name.equalsIgnoreCase(string) || this.aliases.contains(string.toLowerCase(Locale.ROOT));
        }

        protected String name() {
            return this.name;
        }

        protected List<String> aliases() {
            return this.aliases;
        }
    }

    private abstract static class TextAnimation extends Animation {

        protected TextAnimation(final String name, final int speed, final int pause, final String... aliases) {
            super(name, speed, pause, aliases);
        }

        public abstract String animate(String string, long step, String... args);
    }

    private static final class AnimationManager {

        private final Map<String, TextAnimation> animationMap = new HashMap<>();

        private AnimationManager() {
            registerDefaults();
        }

        private void registerDefaults() {
            registerAnimation(new TextAnimation("typewriter", 3, 20) {
                @Override
                public String animate(final String string, final long step, final String... args) {
                    return animateTypewriter(string, step, args);
                }
            });
            registerAnimation(new TextAnimation("wave", 2, 40) {
                @Override
                public String animate(final String string, final long step, final String... args) {
                    return animateWave(string, step, args);
                }
            });
            registerAnimation(new TextAnimation("burn", 2, 40) {
                @Override
                public String animate(final String string, final long step, final String... args) {
                    return animateBurn(string, step, args);
                }
            });
            registerAnimation(new TextAnimation("colors", 4, 0, "colours") {
                @Override
                public String animate(final String string, final long step, final String... args) {
                    return animateColors(string, step, args);
                }
            });
        }

        private void registerAnimation(final TextAnimation animation) {
            this.animationMap.put(animation.name(), animation);
            for (final String alias : animation.aliases()) {
                this.animationMap.put(alias.toLowerCase(Locale.ROOT), animation);
            }
        }

        private TextAnimation getAnimation(final String name) {
            return this.animationMap.get(name.toLowerCase(Locale.ROOT));
        }

        private boolean containsAnimations(final String text) {
            return LEGACY_ANIMATION_PATTERN.matcher(text).find() || MODERN_ANIMATION_PATTERN.matcher(text).find();
        }

        private String parseTextAnimations(final String text, final long step) {
            String out = applyPattern(text, step, LEGACY_ANIMATION_PATTERN, true);
            out = applyPattern(out, step, MODERN_ANIMATION_PATTERN, false);
            return out;
        }

        private String applyPattern(
                final String input,
                final long step,
                final Pattern pattern,
                final boolean legacyFormat
        ) {
            final Matcher matcher = pattern.matcher(input);
            final StringBuffer out = new StringBuffer(input.length() + 32);
            while (matcher.find()) {
                final String name = matcher.group(1) == null ? "" : matcher.group(1).toLowerCase(Locale.ROOT);
                final String argsRaw = matcher.group(2);
                final String text = matcher.group(3) == null ? "" : matcher.group(3);
                final TextAnimation animation = getAnimation(name);
                final String[] parsedArgs = parseArgs(argsRaw, legacyFormat);
                // No logging here - keep animation parsing silent in production

                final String replacement = animation == null
                        ? text
                        : animation.animate(text, step, parsedArgs);
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(out);
            return out.toString();
        }
    }
}
