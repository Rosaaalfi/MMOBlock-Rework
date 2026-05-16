package me.chyxelmc.mmoblock.api.result;

import me.chyxelmc.mmoblock.api.model.PlacedBlock;

public final class PlaceResult {

    private final boolean success;
    private final String message;
    private final PlacedBlock placedBlock;

    private PlaceResult(final boolean success, final String message, final PlacedBlock placedBlock) {
        this.success = success;
        this.message = message;
        this.placedBlock = placedBlock;
    }

    public static PlaceResult success(final PlacedBlock placedBlock) {
        return new PlaceResult(true, "", placedBlock);
    }

    public static PlaceResult error(final String message) {
        return new PlaceResult(false, message, null);
    }

    public boolean success() {
        return this.success;
    }

    public String message() {
        return this.message;
    }

    public PlacedBlock placedBlock() {
        return this.placedBlock;
    }
}
