package com.melluh.servertours.hook;

import com.melluh.servertours.ServerTours;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HookHandler {
    private static final Map<Class<? extends Hook>, Hook> LOADED_HOOKS;

    static {
        LOADED_HOOKS = new HashMap<>();
    }

    private HookHandler() {
    }

    public static void initializeHook(String s, Class<? extends Hook> clazz) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(s);
        if (plugin == null) {
            return;
        }
        Hook instantiate = instantiate(clazz);
        if (!instantiate.init()) {
            ServerTours.getInstance().getLogger().info("Not hooking into " + s + " (version: " + plugin.getDescription().getVersion() + "), hook initialization failed");
            return;
        }
        HookHandler.LOADED_HOOKS.put(clazz, instantiate);
        ServerTours.getInstance().getLogger().info("Successfully hooked into " + s + " (version: " + plugin.getDescription().getVersion());
    }

    private static Hook instantiate(Class<? extends Hook> clazz) {
        try {
            return clazz.getDeclaredConstructor(new Class[0]).newInstance();
        } catch (ReflectiveOperationException cause) {
            throw new RuntimeException(cause);
        }
    }

    public static <T extends Hook> Optional<T> get(Class<T> clazz) {
        return (Optional<T>) Optional.ofNullable(HookHandler.LOADED_HOOKS.get(clazz));
    }

    public interface Hook {
        default boolean init() {
            return true;
        }
    }
}
