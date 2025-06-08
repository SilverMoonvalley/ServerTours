package com.melluh.servertours.chat;

import net.kyori.adventure.text.Component;

public class MenuSpacer implements ChatMenu.MenuItem {
    @Override
    public Component build() {
        return Component.text("");
    }
}
