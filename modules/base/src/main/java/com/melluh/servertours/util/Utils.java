package com.melluh.servertours.util;

import com.melluh.servertours.ServerTours;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Utils {
    private Utils() {
    }

    public static void saveLocation(Location location, ConfigurationSection configurationSection) {
        configurationSection.set("world", location.getWorld().getName());
        configurationSection.set("x", location.getX());
        configurationSection.set("y", location.getY());
        configurationSection.set("z", location.getZ());
        configurationSection.set("yaw", location.getYaw());
        configurationSection.set("pitch", location.getPitch());
    }

    public static Location loadLocation(ConfigurationSection configurationSection) {
        World world = Bukkit.getWorld(Objects.requireNonNull(configurationSection.getString("world")));
        if (world == null) {
            ServerTours.getInstance().getLogger().warning("Data file references world '" + configurationSection.getString("world") + "', but it cannot be found. This will cause issues!");
        }
        return new Location(world, configurationSection.getDouble("x"), configurationSection.getDouble("y"), configurationSection.getDouble("z"), (float) configurationSection.getDouble("yaw"), (float) configurationSection.getDouble("pitch"));
    }

    public static List<ConfigurationSection> sectionsAsList(ConfigurationSection obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        Stream<String> stream = obj.getKeys(false).stream();
        Objects.requireNonNull(obj);
        return stream.map(obj::getConfigurationSection).filter(Objects::nonNull).toList();
    }

    public static List<String> sortTabComplete(Collection<String> collection, String s) {
        List<String> list = new ArrayList<>();
        StringUtil.copyPartialMatches(s, collection, list);
        Collections.sort(list);
        return list;
    }

    public static Integer safelyParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static float safelyParseFloat(String s, float n) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException ex) {
            return n;
        }
    }

    public static <T extends Enum<T>> T safelyParseEnum(Class<T> enumClass, String name) {
        if (name == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean parseBoolean(String s) {
        return s.equals("true") || s.equals("on") || s.equals("yes");
    }

    public static void logLarge(Level level, String[] array) {
        int maxLength = getMaxLength(array);
        Logger logger = ServerTours.getInstance().getLogger();
        logger.log(level, repeat("*", maxLength));
        Arrays.stream(array).forEach(msg -> logger.log(level, msg));
        logger.log(level, repeat("*", maxLength));
    }

    public static String repeat(String o, int n) {
        return String.join("", Collections.nCopies(n, o));
    }

    public static int getMaxLength(String[] array) {
        return Arrays.stream(array).mapToInt(String::length).max().orElse(0);
    }

    public static float getYawDelta(float n, float n2) {
        double n3 = n - n2;
        double n4 = n2 - n;
        if (n3 < 0.0) {
            n3 += 360.0;
        }
        if (n4 < 0.0) {
            n4 += 360.0;
        }
        return (float) ((n3 < n4) ? (-n3) : n4);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static List<String> color(List<String> list) {
        return list.stream().map(Utils::color).toList();
    }

    public static String getWorldName(World world) {
        return (world != null) ? world.getName() : "unknown world";
    }
}
