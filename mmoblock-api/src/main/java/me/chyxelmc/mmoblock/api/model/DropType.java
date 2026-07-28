package me.chyxelmc.mmoblock.api.model;

/**
 * The type of a drop entry.
 *
 * <p>Use {@link #MATERIAL} for item/block drops, {@link #EXPERIENCE} for
 * experience point drops, {@link #COMMAND} for console command execution,
 * and {@link #CUSTOM} for third-party addon plugin drop handlers.</p>
 *
 * <p>For {@link #CUSTOM} drops, the {@link DropEntry#customHandlerId()}
 * specifies which registered {@link me.chyxelmc.mmoblock.api.drop.DropHandler}
 * to invoke, and {@link DropEntry#customData()} provides optional configuration.</p>
 */
public enum DropType {
    MATERIAL,
    EXPERIENCE,
    COMMAND,
    CUSTOM
}
