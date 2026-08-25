package com.melluh.servertours;

import com.melluh.servertours.playback.CraftTouringPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHandler extends PlaceholderExpansion {
    public @NotNull String getIdentifier() {
        return "servertours";
    }

    public @NotNull String getVersion() {
        return ServerTours.getInstance().getDescription().getVersion();
    }

    public @NotNull String getAuthor() {
        return String.join(", ", ServerTours.getInstance().getDescription().getAuthors());
    }

    public boolean persist() {
        return true;
    }

    public String onPlaceholderRequest(Player player, @NotNull String s) {
        CraftTouringPlayer touringPlayer = ServerTours.getInstance().getPlaybackManager().getTouringPlayer(player);
        if (touringPlayer == null) {
            return "";
        }
        return switch (s) {
            case "route" -> touringPlayer.getRoute().getName();
            case "point" -> String.valueOf(touringPlayer.getRoute().indexOf(touringPlayer.getCurrentPoint()) + 1);
            case "numpoints" -> String.valueOf(touringPlayer.getRoute().getNumPoints());
            case "percent" -> "" + (int) (touringPlayer.getRouteProgress() * 100.0f);
            default -> null;
        };
    }
}
