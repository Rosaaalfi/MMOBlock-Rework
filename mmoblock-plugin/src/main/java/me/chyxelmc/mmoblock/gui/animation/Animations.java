package me.chyxelmc.mmoblock.gui.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Animations {
    public static GuiAnimation sequential() { return (width, height) -> range(width * height); }
    public static GuiAnimation reversed() { return (width, height) -> { final List<Integer> slots = range(width * height); Collections.reverse(slots); return slots; }; }
    public static GuiAnimation random() { return (width, height) -> { final List<Integer> slots = range(width * height); Collections.shuffle(slots); return slots; }; }
    public static GuiAnimation rowByRow() { return sequential(); }
    public static GuiAnimation columnByColumn() { return (width, height) -> { final List<Integer> slots = new ArrayList<>(); for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) slots.add(y * width + x); return slots; }; }
    public static GuiAnimation horizontalSnake() { return (width, height) -> { final List<Integer> slots = new ArrayList<>(); for (int y = 0; y < height; y++) for (int step = 0; step < width; step++) slots.add(y * width + (y % 2 == 0 ? step : width - step - 1)); return slots; }; }
    public static GuiAnimation verticalSnake() { return (width, height) -> { final List<Integer> slots = new ArrayList<>(); for (int x = 0; x < width; x++) for (int step = 0; step < height; step++) slots.add((x % 2 == 0 ? step : height - step - 1) * width + x); return slots; }; }
    private static List<Integer> range(final int size) { final List<Integer> slots = new ArrayList<>(size); for (int slot = 0; slot < size; slot++) slots.add(slot); return slots; }
    private Animations() { }
}
