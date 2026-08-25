package com.melluh.servertours.util.nms;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.nms.NmsHandler;
import com.melluh.servertours.util.Utils;

import java.util.logging.Level;

public class NmsAdapter {
    private static NmsHandler handler;

    private NmsAdapter() {
    }

    public static boolean initialize() {
        if (!isSpigot()) {
            Utils.logLarge(Level.SEVERE, new String[]{"It looks like you're running an unsupported server software, like CraftBukkit.", "Please use a modern alternative like Paper instead.", "The plugin will now be disabled."});
            return false;
        }
        NmsVersion current = NmsVersion.getCurrent();
        if (current == null) {
            Utils.logLarge(Level.SEVERE, new String[]{"Your Minecraft version is incompatible with this version of ServerTours.", "Supported versions: " + NmsVersion.formatSupported(), "The plugin will now be disabled."});
            return false;
        }
        try {
            handler = current.getHandlerClass().getDeclaredConstructor(new Class[0]).newInstance();
            ServerTours.getInstance().getLogger().log(Level.INFO, "Initialized NMS support for " + current.getName() + " (" + current.getHandlerClass().getSimpleName() + ")");
            return true;
        } catch (ReflectiveOperationException e) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Failed to instantiate NMS handler", e);
            return false;
        }
    }

    private static boolean isSpigot() {
        try {
            Class.forName("org.bukkit.Server$Spigot");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static NmsHandler getHandler() {
        if (NmsAdapter.handler == null) {
            throw new IllegalStateException("NMS handler has not been initialized");
        }
        return NmsAdapter.handler;
    }
}
