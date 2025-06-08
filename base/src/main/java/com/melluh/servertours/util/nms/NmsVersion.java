package com.melluh.servertours.util.nms;

import com.melluh.servertours.nms.NmsHandler;
import lombok.Getter;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public enum NmsVersion {
    v1_21_4("1.21.4", com.melluh.servertours.nms.v1_21_4.NmsHandler.class),
    v1_21_5("1.21.5", com.melluh.servertours.nms.v1_21_5.NmsHandler.class);

    private static NmsVersion current;

    static {
        NmsVersion.current = null;
    }

    private final String name;
    private final Class<? extends NmsHandler> handlerClass;

    NmsVersion(String name2, Class<? extends NmsHandler> handlerClass) {
        this.name = name2;
        this.handlerClass = handlerClass;
    }

    public static NmsVersion getCurrent() {
        if (NmsVersion.current != null) {
            return NmsVersion.current;
        }
        return NmsVersion.current = Arrays.stream(values()).filter(nmsVersion -> {
            Object o = Bukkit.getBukkitVersion().split("-")[0];
            return nmsVersion.getName().equals(o);
        }).findFirst().orElse(null);
    }

    public static String formatSupported() {
        return Arrays.stream(values()).map(NmsVersion::getName).collect(Collectors.joining(", "));

    }
}
