package com.melluh.servertours.util;

import com.melluh.servertours.ServerTours;
import lombok.Getter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;

public abstract class FileProvider {
    private final File file;
    @Getter
    private FileConfiguration config;

    protected FileProvider(String child) {
        this.file = new File(ServerTours.getInstance().getDataFolder(), child);
    }

    public void load() {
        try {
            InputStream resource = ServerTours.getInstance().getResource(this.file.getName());
            Objects.requireNonNull(resource, "Failed to find embedded template file for " + this.file.getName());
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            yamlConfiguration.load(new InputStreamReader(resource));
            this.config = new YamlConfiguration();
            boolean b = false;
            if (this.file.exists()) {
                this.config.load(this.file);
            } else {
                Files.createDirectories(this.file.getParentFile().toPath());
                b = true;
            }
            for (String s : yamlConfiguration.getKeys(true)) {
                if (!this.config.isSet(s)) {
                    this.config.set(s, yamlConfiguration.get(s));
                    this.config.setComments(s, yamlConfiguration.getComments(s));
                    this.config.setInlineComments(s, yamlConfiguration.getInlineComments(s));
                    b = true;
                }
            }
            for (String s2 : this.config.getKeys(true)) {
                if (!yamlConfiguration.contains(s2)) {
                    this.config.set(s2, null);
                    b = true;
                }
            }
            if (b) {
                this.config.options().header(new Replacer().add("%date_time%", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).add("%version%", ServerTours.getInstance().getDescription().getVersion()).apply(yamlConfiguration.options().header()));
                this.config.save(this.file);
            }
        } catch (IOException | InvalidConfigurationException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error while loading file " + this.file.getName(), thrown);
        }
    }

}
