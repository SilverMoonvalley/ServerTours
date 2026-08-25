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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
            this.saveRouteChecked(craftRoute);
        } catch (IOException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error occurred while attempting to save route " + craftRoute.getName(), thrown);
        }
    }

    /** Writes a route transactionally and reports failures to callers which need commit semantics. */
    public void saveRouteChecked(CraftRoute craftRoute) throws IOException {
        Objects.requireNonNull(craftRoute, "route may not be null");
        Path parent = ServerTours.getInstance().getDataFolder().toPath().resolve("routes");
        Files.createDirectories(parent);
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        craftRoute.saveTo(yamlConfiguration);
        writeAtomically(parent.resolve(craftRoute.getName() + ".yml"),
                yamlConfiguration.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    public static void writeAtomically(Path destination, byte[] bytes) throws IOException {
        Objects.requireNonNull(destination, "destination may not be null");
        Objects.requireNonNull(bytes, "bytes may not be null");
        Path parent = Objects.requireNonNull(destination.getParent(), "destination parent may not be null");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + destination.getFileName(), ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public void removeRoute(CraftRoute craftRoute) {
        try {
            this.removeRouteChecked(craftRoute);
        } catch (IOException thrown) {
            ServerTours.getInstance().getLogger().log(Level.SEVERE, "Error occurred while attempting to remove route" + craftRoute.getName(), thrown);
        }
    }

    /** Durably removes a route file before callers mutate route or recording caches. */
    public void removeRouteChecked(CraftRoute craftRoute) throws IOException {
        Objects.requireNonNull(craftRoute, "route may not be null");
        Path file = ServerTours.getInstance().getDataFolder().toPath()
                .resolve("routes").resolve(craftRoute.getName() + ".yml");
        Files.deleteIfExists(file);
    }
}
