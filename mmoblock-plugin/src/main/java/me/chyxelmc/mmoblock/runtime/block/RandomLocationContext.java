package me.chyxelmc.mmoblock.runtime.block;

public record RandomLocationContext(
        double originX,
        double originY,
        double originZ,
        boolean enabled,
        double radius,
        boolean closest,
        double centerDistance
) {
}
