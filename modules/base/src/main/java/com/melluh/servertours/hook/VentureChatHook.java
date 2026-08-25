package com.melluh.servertours.hook;

import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VentureChatHook implements HookHandler.Hook {
    private final Set<UUID> bungeeChatEnabled;

    public VentureChatHook() {
        this.bungeeChatEnabled = new HashSet<>();
    }

    public void disableBungeeChat(UUID uuid) {
        MineverseChatPlayer onlineMineverseChatPlayer = MineverseChatAPI.getOnlineMineverseChatPlayer(uuid);
        if (onlineMineverseChatPlayer == null || !onlineMineverseChatPlayer.getBungeeToggle()) {
            return;
        }
        onlineMineverseChatPlayer.setBungeeToggle(false);
        this.bungeeChatEnabled.add(uuid);
    }

    public void restoreBungeeChat(UUID uuid) {
        if (!this.bungeeChatEnabled.remove(uuid)) {
            return;
        }
        MineverseChatPlayer onlineMineverseChatPlayer = MineverseChatAPI.getOnlineMineverseChatPlayer(uuid);
        if (onlineMineverseChatPlayer == null) {
            return;
        }
        onlineMineverseChatPlayer.setBungeeToggle(true);
    }
}
