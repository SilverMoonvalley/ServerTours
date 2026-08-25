package com.melluh.servertours.api;

import org.bukkit.entity.Player;

public class ServerToursAPI {
    private static ServerToursPlugin implementation;

    private ServerToursAPI() {
    }

    public static RouteManager getRouteManager() {
        return getImplementation().getRouteManager();
    }

    public static PlaybackManager getPlaybackManager() {
        return getImplementation().getPlaybackManager();
    }

    public static ServerToursPlugin getImplementation() {
        if (ServerToursAPI.implementation == null) {
            throw new IllegalStateException("Implementation not registered yet");
        }
        return ServerToursAPI.implementation;
    }

    public static void setImplementation(ServerToursPlugin implementation) {
        if (ServerToursAPI.implementation != null) {
            throw new IllegalStateException("Implementation already registered");
        }
        ServerToursAPI.implementation = implementation;
    }

    public interface ServerToursPlugin {
        RouteManager getRouteManager();

        PlaybackManager getPlaybackManager();

        String getTranslation(String p0);

        boolean isBedrockPlayer(Player p0);
    }
}
