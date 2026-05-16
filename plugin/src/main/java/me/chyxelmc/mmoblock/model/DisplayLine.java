package me.chyxelmc.mmoblock.model;

public record DisplayLine(
    int line,
    String text,
    String click,
    String dead,
    String item,
    String block
) implements me.chyxelmc.mmoblock.api.model.DisplayLine {
}

