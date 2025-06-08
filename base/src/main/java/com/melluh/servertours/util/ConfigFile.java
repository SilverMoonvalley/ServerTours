package com.melluh.servertours.util;

import com.melluh.servertours.ServerTours;
import lombok.Getter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

@Getter
public class ConfigFile {
    private final File file;
    private final YamlConfiguration config;

    public ConfigFile(File file, YamlConfiguration config) {
        this.file = file;
        this.config = config;
    }

    public static ConfigFile load(String child) {
        return load(new File(ServerTours.getInstance().getDataFolder(), child));
    }

    public static ConfigFile load(File file) {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        try {
            yamlConfiguration.load(file);
        } catch (IOException | InvalidConfigurationException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Failed to load " + file.getName(), thrown);
        }
        return new ConfigFile(file, yamlConfiguration);
    }

    public static void save(File file, YamlConfiguration yamlConfiguration) {
        try {
            yamlConfiguration.save(file);
        } catch (IOException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Failed to save " + file.getName(), thrown);
        }
    }

    public void save() {
        save(this.file, this.config);
    }

}
