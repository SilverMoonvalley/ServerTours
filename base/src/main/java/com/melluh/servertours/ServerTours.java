package com.melluh.servertours;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.melluh.servertours.api.ServerToursAPI;
import com.melluh.servertours.cmd.*;
import com.melluh.servertours.cmd.editmode.CreatePointSubCommand;
import com.melluh.servertours.cmd.editmode.PreviewSubCommand;
import com.melluh.servertours.cmd.editmode.SelectPointSubCommand;
import com.melluh.servertours.cmd.editmode.ToggleParticlesSubCommand;
import com.melluh.servertours.cmd.hidden.*;
import com.melluh.servertours.editmode.EditModeManager;
import com.melluh.servertours.file.Language;
import com.melluh.servertours.file.PersistenceManager;
import com.melluh.servertours.file.Settings;
import com.melluh.servertours.hook.FloodgateHook;
import com.melluh.servertours.hook.HookHandler;
import com.melluh.servertours.hook.VentureChatHook;
import com.melluh.servertours.playback.CraftPlaybackManager;
import com.melluh.servertours.route.CraftRouteManager;
import com.melluh.servertours.util.nms.NmsAdapter;
import com.melluh.servertours.util.protocol.PacketUtil;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ServerTours extends JavaPlugin implements ServerToursAPI.ServerToursPlugin {
    private static ServerTours instance;
    @Getter
    private ProtocolManager protocolManager;
    @Getter
    private EditModeManager editModeManager;
    private CraftPlaybackManager playbackManager;
    private CraftRouteManager routeManager;
    private Settings settings;
    private Language language;
    @Getter
    private PersistenceManager persistenceManager;
    @Getter
    private BukkitAudiences bukkitAudiences;
    private boolean placeholdersEnabled;

    public static ServerTours getInstance() {
        return ServerTours.instance;
    }

    public static String placeholders(Player player, String s) {
        if (!ServerTours.instance.placeholdersEnabled) {
            return s;
        }
        return PlaceholderAPI.setPlaceholders(player, s);
    }

    public static String translate(String s, Object... array) {
        String s2 = ServerTours.instance.getTranslation(s);
        for (Object o : array) {
            s2 = s2.replaceFirst("\\{}", (o == null) ? "null" : o.toString().replace("\\", "\\\\"));
        }
        return s2;
    }

    public void onEnable() {
        ServerToursAPI.setImplementation(ServerTours.instance = this);
        if (!NmsAdapter.initialize()) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.bukkitAudiences = BukkitAudiences.create(this);
        this.settings = new Settings();
        this.language = new Language();
        this.persistenceManager = new PersistenceManager();
        this.settings.load();
        this.language.load();
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        PacketUtil.registerProtocolLib();
        this.routeManager = new CraftRouteManager();
        (this.editModeManager = new EditModeManager()).startRunnable();
        (this.playbackManager = new CraftPlaybackManager()).startRunnable();
        PluginManager pluginManager = this.getServer().getPluginManager();
        pluginManager.registerEvents(this.editModeManager, this);
        pluginManager.registerEvents(this.playbackManager, this);
        CommandHandler commandHandler = new CommandHandler();
        commandHandler.register("create", new CreateSubCommand());
        commandHandler.register("edit", new EditSubCommand());
        commandHandler.register("exit", new ExitSubCommand());
        commandHandler.register("play", new PlaySubCommand());
        commandHandler.register("playnear", new PlayNearSubCommand());
        commandHandler.register("stop", new StopSubCommand());
        commandHandler.register("remove", new RemoveSubCommand());
        commandHandler.register("reload", new ReloadSubCommand());
        commandHandler.register("continue", new ContinueSubCommand());
        commandHandler.register("deselect", new DeselectSubCommand());
        commandHandler.register("pointaction", new PointActionSubCommand());
        commandHandler.register("pointcommand", new PointCommandSubCommand());
        commandHandler.register("pointsetting", new PointSettingSubCommand());
        if (this.settings.getConfig().getBoolean("editMode.enableHotbarAltCommands")) {
            commandHandler.register("createpoint", new CreatePointSubCommand());
            commandHandler.register("preview", new PreviewSubCommand());
            commandHandler.register("selectpoint", new SelectPointSubCommand());
            commandHandler.register("toggleparticles", new ToggleParticlesSubCommand());
        }
        PluginCommand pluginCommand = Objects.requireNonNull(this.getCommand("tour"));
        pluginCommand.setExecutor(commandHandler);
        pluginCommand.setTabCompleter(commandHandler);
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            this.getLogger().info("PlaceholderAPI detected, hooking into it");
            this.placeholdersEnabled = true;
            new PlaceholderHandler().register();
        }
        HookHandler.initializeHook("Floodgate", FloodgateHook.class);
        HookHandler.initializeHook("VentureChat", VentureChatHook.class);
        this.getServer().getScheduler().runTask(this, () -> this.persistenceManager.load());
    }

    public void onDisable() {
        if (this.playbackManager != null) {
            this.playbackManager.stopAllTouring();
        }
        if (this.editModeManager != null) {
            this.editModeManager.stopAllEditing();
        }
    }

    public void reload() {
        this.settings.load();
        this.language.load();
    }

    public @NotNull FileConfiguration getConfig() {
        return this.settings.getConfig();
    }

    @Override
    public CraftRouteManager getRouteManager() {
        return this.routeManager;
    }

    @Override
    public CraftPlaybackManager getPlaybackManager() {
        return this.playbackManager;
    }

    @Override
    public String getTranslation(String s) {
        return this.language.getTranslation(s);
    }

    @Override
    public boolean isBedrockPlayer(Player player) {
        return HookHandler.get(FloodgateHook.class).map(floodgateHook -> floodgateHook.isBedrockPlayer(player)).orElse(false);
    }
}
