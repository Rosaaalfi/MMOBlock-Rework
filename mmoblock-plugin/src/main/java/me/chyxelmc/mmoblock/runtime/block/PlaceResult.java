package me.chyxelmc.mmoblock.runtime.block;

import me.chyxelmc.mmoblock.model.PlacedBlockModel;

public record PlaceResult(boolean success, String message, PlacedBlockModel placedBlock) {

    public static PlaceResult success(final PlacedBlockModel placedBlock) {
        return new PlaceResult(true, "", placedBlock);
    }

    public static PlaceResult error(final String message) {
        return new PlaceResult(false, message, null);
    }
}
