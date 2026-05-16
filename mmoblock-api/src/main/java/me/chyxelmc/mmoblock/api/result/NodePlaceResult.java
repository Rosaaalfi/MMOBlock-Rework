package me.chyxelmc.mmoblock.api.result;

import me.chyxelmc.mmoblock.api.model.PlacedNode;

public final class NodePlaceResult {

    private final boolean success;
    private final String message;
    private final PlacedNode placedNode;

    private NodePlaceResult(final boolean success, final String message, final PlacedNode placedNode) {
        this.success = success;
        this.message = message;
        this.placedNode = placedNode;
    }

    public static NodePlaceResult success(final PlacedNode placedNode) {
        return new NodePlaceResult(true, "", placedNode);
    }

    public static NodePlaceResult error(final String message) {
        return new NodePlaceResult(false, message, null);
    }

    public boolean success() {
        return this.success;
    }

    public String message() {
        return this.message;
    }

    public PlacedNode placedNode() {
        return this.placedNode;
    }
}
