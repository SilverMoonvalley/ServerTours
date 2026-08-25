package com.melluh.servertours.route;

import com.google.common.base.Preconditions;
import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.util.FormattingUtils;
import com.melluh.servertours.api.util.LocalizableEnum;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@Setter
@Getter
public class RoutePointCommand {
    private CommandExecutorType executorType;
    private CommandTrigger triggerType;
    private String command;

    public RoutePointCommand(CommandExecutorType executorType, CommandTrigger triggerType, String command) {
        this.executorType = executorType;
        this.triggerType = triggerType;
        this.command = command;
    }

    public void execute(Player reference) {
        Preconditions.checkNotNull(reference);
        Bukkit.dispatchCommand((this.executorType == CommandExecutorType.CONSOLE) ? Bukkit.getConsoleSender() : reference, this.command.replaceAll("%?%player%%?", reference.getName()));
    }

    public enum CommandExecutorType implements LocalizableEnum {
        PLAYER,
        CONSOLE;

        @Override
        public String getName() {
            return FormattingUtils.formatEnumName(this.name());
        }

        @Override
        public String getDescription() {
            return ServerTours.translate("chatMenu.tooltips." + this.name().toLowerCase() + "Executor");
        }
    }

    public enum CommandTrigger implements LocalizableEnum {
        ENTER,
        EXIT,
        QUIT;

        @Override
        public String getName() {
            return FormattingUtils.formatEnumName(this.name());
        }

        @Override
        public String getDescription() {
            return ServerTours.translate("chatMenu.tooltips.triggers." + this.name().toLowerCase());
        }
    }
}
