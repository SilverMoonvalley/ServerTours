package com.melluh.servertours.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Replacer {
    private final Map<String, String> replacements;

    public Replacer() {
        this.replacements = new HashMap<>();
    }

    public Replacer add(String s, String s2) {
        this.replacements.put(s, s2);
        return this;
    }

    public String apply(String replace) {
        for (Map.Entry<String, String> entry : this.replacements.entrySet()) {
            replace = replace.replace(entry.getKey(), entry.getValue());
        }
        return replace;
    }
}
