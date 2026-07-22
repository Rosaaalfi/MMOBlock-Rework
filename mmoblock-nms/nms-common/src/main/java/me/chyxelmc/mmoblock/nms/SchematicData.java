package me.chyxelmc.mmoblock.nms;

import java.util.List;

public record SchematicData(
    int width,
    int height,
    int length,
    List<SchematicBlock> blocks
) {

    public record SchematicBlock(int x, int y, int z, String materialName) {
    }
}
