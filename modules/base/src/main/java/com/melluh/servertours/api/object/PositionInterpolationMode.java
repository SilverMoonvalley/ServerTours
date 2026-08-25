package com.melluh.servertours.api.object;

import com.melluh.servertours.api.ServerToursAPI;
import com.melluh.servertours.api.util.FormattingUtils;
import com.melluh.servertours.api.util.LocalizableEnum;

/**
 * Selects the curve used for the positional part of interpolated route points.
 */
public enum PositionInterpolationMode implements LocalizableEnum {
    /** The original ServerTours cardinal/Hermite curve, with corrected arc-length sampling. */
    LEGACY_CARDINAL,

    /** A chord-length-aware Catmull-Rom curve using the centripetal parameterization. */
    CENTRIPETAL_CATMULL_ROM;

    @Override
    public String getName() {
        return FormattingUtils.formatEnumName(this.name());
    }

    @Override
    public String getDescription() {
        return ServerToursAPI.getImplementation().getTranslation(
                "chatMenu.tooltips.positionInterpolationModes." + this.name().toLowerCase()
        );
    }
}
