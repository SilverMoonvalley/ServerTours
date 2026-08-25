package com.melluh.servertours.cmd.hidden;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.cmd.CommandHandler;
import com.melluh.servertours.editmode.EditingPlayer;
import com.melluh.servertours.playback.ConfirmMode;
import com.melluh.servertours.route.point.CraftOrbitPoint;
import com.melluh.servertours.route.point.CraftRoutePoint;
import com.melluh.servertours.util.Utils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.stream.Collectors;

public class PointSettingSubCommand implements CommandHandler.SubCommand {
    @Override
    public void execute(CommandSender commandSender, String[] array) {
        Player player = (Player) commandSender;
        if (array.length < 3) {
            CommandHandler.sendUsage(this, player);
            return;
        }
        EditingPlayer editingPlayer = ServerTours.getInstance().getEditModeManager().getEditingPlayer(player);
        if (editingPlayer == null) {
            player.sendMessage(ServerTours.translate("commands.errors.notEditing"));
            return;
        }
        CraftRoutePoint selectedPoint = editingPlayer.getSelectedPoint();
        if (selectedPoint == null) {
            player.sendMessage(ServerTours.translate("commands.errors.noPointSelected"));
            return;
        }
        String s = array[1];
        String label = Arrays.stream(array, 2, array.length).collect(Collectors.joining(" "));
        switch (s) {
            case "type": {
                RoutePointType routePointType = Utils.safelyParseEnum(RoutePointType.class, label);
                if (routePointType == null) {
                    player.sendMessage(ChatColor.RED + "Invalid route point type");
                    return;
                }
                selectedPoint.getRoute().replacePoint(selectedPoint, routePointType);
                break;
            }
            case "secondsVisible": {
                selectedPoint.setSecondsVisible(Utils.safelyParseFloat(label, 0.0f));
                break;
            }
            case "orbitSpeed": {
                if (selectedPoint instanceof CraftOrbitPoint craftOrbitPoint) {
                    craftOrbitPoint.setSpeed(Utils.safelyParseFloat(label, 0.0f));
                }
                break;
            }
            case "orbitDistance": {
                if (selectedPoint instanceof CraftOrbitPoint craftOrbitPoint2) {
                    craftOrbitPoint2.setDistance(Utils.safelyParseFloat(label, 0.0f));
                }
                break;
            }
            case "orbitHeight": {
                if (selectedPoint instanceof CraftOrbitPoint craftOrbitPoint3) {
                    craftOrbitPoint3.setHeight(Utils.safelyParseFloat(label, 0.0f));
                }
                break;
            }
            case "orbitStartingPoint": {
                if (selectedPoint instanceof CraftOrbitPoint craftOrbitPoint4) {
                    craftOrbitPoint4.setStartingPoint(Utils.safelyParseFloat(label, 0.0f));
                }
                break;
            }
            case "title": {
                if (label.equals("clear")) {
                    selectedPoint.clearTitle();
                    break;
                }
                selectedPoint.setTitle(label);
                break;
            }
            case "titleFadeIn": {
                selectedPoint.setTitleFadeInTime(Utils.safelyParseFloat(label, 0.0f));
                break;
            }
            case "titleStay": {
                selectedPoint.setTitleStayTime(Utils.safelyParseFloat(label, 0.0f));
                break;
            }
            case "titleFadeOut": {
                selectedPoint.setTitleFadeOutTime(Utils.safelyParseFloat(label, 0.0f));
                break;
            }
            case "description": {
                if (label.equals("clear")) {
                    selectedPoint.clearDescription();
                    break;
                }
                selectedPoint.setDescription(label);
                break;
            }
            case "label": {
                if (label.equals("clear")) {
                    selectedPoint.clearLabel();
                    break;
                }
                selectedPoint.setLabel(label);
                break;
            }
            case "confirmRequired": {
                selectedPoint.setConfirmRequired(Utils.parseBoolean(label));
                break;
            }
            case "confirmMode": {
                ConfirmMode confirmMode = Utils.safelyParseEnum(ConfirmMode.class, label);
                if (confirmMode == null) {
                    player.sendMessage(ChatColor.RED + "Invalid confirm mode");
                    return;
                }
                selectedPoint.setConfirmMode(confirmMode);
                break;
            }
        }
        ServerTours.getInstance().getEditModeManager().updateChatMenu(player);
    }

    @Override
    public String getPermission() {
        return "servertours.commands.edit";
    }

    @Override
    public String getUsage() {
        return "/tour pointsetting <key> <value>";
    }

    @Override
    public String getDescription() {
        return null;
    }
}
