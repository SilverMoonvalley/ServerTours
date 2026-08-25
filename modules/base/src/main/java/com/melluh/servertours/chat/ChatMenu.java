package com.melluh.servertours.chat;

import com.melluh.servertours.ServerTours;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;

public class ChatMenu {
    private final Map<MenuItem, Supplier<Boolean>> menuItems;

    private ChatMenu() {
        this.menuItems = new LinkedHashMap<>();
    }

    private void addItem(MenuItem menuItem, Supplier<Boolean> supplier) {
        this.menuItems.put(menuItem, supplier);
    }

    public void send(Player player) {
        List<MenuItem> list = this.menuItems.entrySet().stream().filter(entry -> entry.getValue().get()).map(Map.Entry::getKey).toList();
        for (int n = 30 - list.size(), i = 0; i < n; ++i) {
            player.sendMessage(" ");
        }
        Audience player2 = ServerTours.getInstance().getBukkitAudiences().player(player);
        for (MenuItem menuItem : list) {
            try {
                Component build = menuItem.build();
                if (build == null) {
                    continue;
                }
                player2.sendMessage(build);
            } catch (Exception thrown) {
                ServerTours.getInstance().getLogger().log(Level.SEVERE, "Failed to send chat menu for " + menuItem.getClass().getSimpleName(), thrown);
            }
        }
    }

    public void sendExit(Player player) {
        for (int i = 0; i < 30; ++i) {
            player.sendMessage("");
        }
    }

    public interface MenuItem {
        Component build();
    }

    public static class Builder {
        private final ChatMenu result;

        public Builder() {
            this.result = new ChatMenu();
        }

        public Builder append(MenuItem menuItem) {
            this.result.addItem(menuItem, () -> true);
            return this;
        }

        public Builder append(MenuItem menuItem, Supplier<Boolean> supplier) {
            this.result.addItem(menuItem, supplier);
            return this;
        }

        public ChatMenu get() {
            return this.result;
        }
    }
}
