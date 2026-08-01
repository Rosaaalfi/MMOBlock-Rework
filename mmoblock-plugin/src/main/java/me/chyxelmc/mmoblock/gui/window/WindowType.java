package me.chyxelmc.mmoblock.gui.window;

/** Player-openable container screens supported by the GUI engine. */
public enum WindowType {
    CHEST(null, -1),
    HOPPER("HOPPER", 5),
    DISPENSER("DISPENSER", 9),
    DROPPER("DROPPER", 9),
    FURNACE("FURNACE", 3),
    BLAST_FURNACE("BLAST_FURNACE", 3),
    SMOKER("SMOKER", 3),
    BREWING("BREWING", 5),
    WORKBENCH("WORKBENCH", 10),
    ENCHANTING("ENCHANTING", 2),
    ANVIL("ANVIL", 3),
    SMITHING("SMITHING", -1),
    BEACON("BEACON", 1),
    LOOM("LOOM", 4),
    CARTOGRAPHY("CARTOGRAPHY", 3),
    GRINDSTONE("GRINDSTONE", 3),
    STONECUTTER("STONECUTTER", 2),
    CRAFTER("CRAFTER", 9);

    private final String bukkitName;
    private final int expectedSize;

    WindowType(final String bukkitName, final int expectedSize) {
        this.bukkitName = bukkitName;
        this.expectedSize = expectedSize;
    }

    public int expectedSize() { return this.expectedSize; }
    public String bukkitName() { return this.bukkitName; }
}
