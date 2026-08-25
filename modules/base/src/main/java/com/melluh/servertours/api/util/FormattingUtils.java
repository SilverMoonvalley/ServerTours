package com.melluh.servertours.api.util;

import java.util.Arrays;
import java.util.stream.Collectors;

public class FormattingUtils {
    private FormattingUtils() {
    }

    public static String capitalize(String s) {
        return Arrays.stream(s.split(" ")).map(s2 -> s2.substring(0, 1).toUpperCase() + s2.substring(1)).collect(Collectors.joining(" "));
    }

    public static String formatEnumName(String s) {
        return capitalize(s.toLowerCase().replace("_", " "));
    }
}
