package com.melluh.servertours.playback;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.util.FormattingUtils;
import com.melluh.servertours.api.util.LocalizableEnum;

public enum ConfirmMode implements LocalizableEnum {
    MOUSE,
    KEYBOARD,
    CHAT;

    @Override
    public String getName() {
        return FormattingUtils.formatEnumName(this.name());
    }

    @Override
    public String getDescription() {
        return ServerTours.translate("chatMenu.tooltips.confirmModes." + this.name().toLowerCase());
    }
}
