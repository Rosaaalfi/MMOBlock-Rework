package me.chyxelmc.mmoblock.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorLogger {
    private static final String RESET = "\u001B[0m";

    private static final String BLACK = "\u001B[30m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";

    private static final String BRIGHT_BLACK = "\u001B[90m";
    private static final String BRIGHT_WHITE = "\u001B[97m";
    private static final String BRIGHT_RED = "\u001B[91m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_BLUE = "\u001B[94m";

    public static String parseColorTags(String input) {
        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("reset", RESET);
        colorMap.put("black", BLACK);
        colorMap.put("red", RED);
        colorMap.put("green", GREEN);
        colorMap.put("yellow", YELLOW);
        colorMap.put("blue", BLUE);
        colorMap.put("purple", PURPLE);
        colorMap.put("cyan", CYAN);
        colorMap.put("white", WHITE);
        colorMap.put("brightBlack", BRIGHT_BLACK);
        colorMap.put("brightWhite", BRIGHT_WHITE);
        colorMap.put("brightRed", BRIGHT_RED);
        colorMap.put("brightGreen", BRIGHT_GREEN);
        colorMap.put("brightYellow", BRIGHT_YELLOW);
        colorMap.put("brightBlue", BRIGHT_BLUE);

        Pattern pattern = Pattern.compile("<([a-zA-Z]+)>(.*?)</\\1>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();

        while(matcher.find()) {
            String tag = matcher.group(1).toLowerCase();
            String content = matcher.group(2);
            String color = colorMap.getOrDefault(tag, "");
            matcher.appendReplacement(sb, color + content);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String black(String message) {
        return BLACK + message + RESET;
    }

    public String brightBlack(String message) {
        return BRIGHT_BLACK + message + RESET;
    }

    public String white(String message) {
        return WHITE + message + RESET;
    }

    public String brightWhite(String message) {
        return BRIGHT_WHITE + message + RESET;
    }

    public String red(String message) {
        return RED + message + RESET;
    }

    public String brightRed(String message) {
        return BRIGHT_RED + message + RESET;
    }

    public String green(String message) {
        return GREEN + message + RESET;
    }

    public String brightGreen(String message) {
        return BRIGHT_GREEN + message + RESET;
    }

    public String yellow(String message) {
        return YELLOW + message + RESET;
    }

    public String brightYellow(String message) {
        return BRIGHT_YELLOW + message + RESET;
    }

    public String blue(String message) {
        return BLUE + message + RESET;
    }

    public String brightBlue(String message) {
        return BRIGHT_BLUE + message + RESET;
    }

    public String purple(String message) {
        return PURPLE + message + RESET;
    }

    public String cyan(String message) {
        return CYAN + message + RESET;
    }
}

