package com.melluh.servertours.file;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.event.RoutesLoadEvent;
import com.melluh.servertours.route.CraftRoute;
import com.melluh.servertours.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

public class PersistenceManager {
    public void load() {
        this.convertLegacyFile();
        File file = new File(ServerTours.getInstance().getDataFolder(), "routes");
        if (!file.isDirectory()) {
            return;
        }

        Arrays.stream(Objects.requireNonNull(file.listFiles())).filter(file2 -> file2.isFile() && file2.getName().endsWith(".yml")).forEach(file3 -> {
            try {
                YamlConfiguration yamlConfiguration = new YamlConfiguration();
                yamlConfiguration.load(file3);
                ServerTours.getInstance().getRouteManager().registerRoute(new CraftRoute(yamlConfiguration));
            } catch (Exception e) {
                ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error while attempting to load file routes/" + file3.getName(), e);
            }
        });

        ServerTours.getInstance().getLogger().info("Loaded " + ServerTours.getInstance().getRouteManager().getRoutes().size() + " routes");
        Bukkit.getPluginManager().callEvent(new RoutesLoadEvent());
    }

    private void convertLegacyFile() {
        File file = new File(ServerTours.getInstance().getDataFolder(), "data.yml");
        if (!file.exists()) {
            return;
        }
        ServerTours.getInstance().getLogger().info("Converting legacy data.yml to new data format...");
        try {
            File parent = new File(ServerTours.getInstance().getDataFolder(), "routes");
            if (!parent.isDirectory()) {
                Files.createDirectory(parent.toPath());
            }
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            yamlConfiguration.load(file);
            ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("routes");
            if (configurationSection != null) {
                for (ConfigurationSection configurationSection2 : Utils.sectionsAsList(configurationSection)) {
                    YamlConfiguration yamlConfiguration2 = new YamlConfiguration();
                    configurationSection2.getKeys(true).forEach(str -> yamlConfiguration2.set(str, configurationSection2.get(str)));
                    yamlConfiguration2.save(new File(file, configurationSection2.getString("name") + ".yml"));
                }

            }
            Files.delete(file.toPath());
        } catch (IOException | InvalidConfigurationException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error occurred while attempting to convert legacy data file", thrown);
        }
    }

    public void saveRoute(CraftRoute craftRoute) {
        try {
            File parent = new File(ServerTours.getInstance().getDataFolder(), "routes");
            if (!parent.isDirectory()) {
                Files.createDirectory(parent.toPath());
            }
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            craftRoute.saveTo(yamlConfiguration);
            yamlConfiguration.save(new File(parent, craftRoute.getName() + ".yml"));
        } catch (IOException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error occurred while attempting to save route " + craftRoute.getName(), thrown);
        }
    }

    public void removeRoute(CraftRoute craftRoute) {
        try {
            File file = new File(ServerTours.getInstance().getDataFolder(), "routes/" + craftRoute.getName() + ".yml");
            if (file.isFile()) {
                Files.delete(file.toPath());
            }
        } catch (IOException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error occurred while attempting to remove route" + craftRoute.getName(), thrown);
        }
    }
}
