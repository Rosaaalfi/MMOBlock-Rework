package me.chyxelmc.mmoblock.api.model;

/**
 * Configuration for the popup effect on dropped items or experience.
 * <p>
 * When enabled, shows a floating text popup at the drop location with the
 * configured text, supporting placeholders like {@code {exp_amount}} and
 * {@code {item_amount}}.
 * </p>
 *
 * @param enabled whether the popup effect is enabled
 * @param text    the text to display (supports color codes & placeholders)
 */
public record DropPopup(
        boolean enabled,
        String text
) {
}
