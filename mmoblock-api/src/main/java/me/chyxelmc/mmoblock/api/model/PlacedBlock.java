package me.chyxelmc.mmoblock.api.model;

import org.bukkit.Location;

import java.util.UUID;

public interface PlacedBlock {
    UUID uniqueId();
    String type();
    String world();
    double x();
    double y();
    double z();
    double originX();
    double originY();
    double originZ();
    String facing();
    String status();
    UUID interactionEntityId();
    Long respawnAt();
    Location toLocation();
}
