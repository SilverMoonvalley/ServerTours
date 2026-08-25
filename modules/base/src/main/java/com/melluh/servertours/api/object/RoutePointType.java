package com.melluh.servertours.api.object;

import com.melluh.servertours.api.ServerToursAPI;
import com.melluh.servertours.api.util.FormattingUtils;
import com.melluh.servertours.api.util.LocalizableEnum;
import lombok.Getter;

@Getter
public enum RoutePointType implements LocalizableEnum {
    STATIONARY(false),
    INTERPOLATE(true),
    ORBIT(false);

    private static final String DESCRIPTION_KEY_FORMAT = "chatMenu.tooltips.pointTypes.%s";
    private final boolean confirmUponEnter;

    RoutePointType(boolean confirmUponEnter) {
        this.confirmUponEnter = confirmUponEnter;
    }

    @Override
    public String getName() {
        return FormattingUtils.formatEnumName(this.name());
    }

    @Override
    public String getDescription() {
        return ServerToursAPI.getImplementation().getTranslation(String.format("chatMenu.tooltips.pointTypes.%s", this.name().toLowerCase()));
    }

}
