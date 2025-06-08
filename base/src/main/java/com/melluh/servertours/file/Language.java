package com.melluh.servertours.file;

import com.melluh.servertours.util.FileProvider;
import net.md_5.bungee.api.ChatColor;

import java.util.Objects;

public class Language extends FileProvider {
    public Language() {
        super("lang.yml");
    }

    public String getTranslation(String s) {
        if (!this.getConfig().contains(s)) {
            return ChatColor.RED + "[Missing translation: " + s;
        }
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString(s)));
    }
}
