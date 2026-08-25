package com.melluh.servertours.api.event;

import com.melluh.servertours.api.TouringPlayer;
import lombok.Getter;
import org.bukkit.entity.Player;

@Getter
public abstract class RoutePlayerEvent extends RouteEvent {
    private final TouringPlayer touringPlayer;

    protected RoutePlayerEvent(TouringPlayer touringPlayer) {
        super(touringPlayer.getRoute());
        this.touringPlayer = touringPlayer;
    }

    public Player getBukkitPlayer() {
        return this.touringPlayer.getPlayer();
    }
}
