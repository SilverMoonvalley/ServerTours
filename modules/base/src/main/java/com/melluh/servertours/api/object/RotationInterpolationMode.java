package com.melluh.servertours.api.object;

import com.melluh.servertours.api.ServerToursAPI;
import com.melluh.servertours.api.util.FormattingUtils;
import com.melluh.servertours.api.util.LocalizableEnum;

/**
 * Selects how yaw and pitch are interpolated between route points.
 */
public enum RotationInterpolationMode implements LocalizableEnum {
    /** The original shortest-path linear yaw and pitch interpolation. */
    LINEAR_SHORTEST_PATH,

    /** Four-point Catmull-Rom rotation interpolation expressed with cubic Hermite tangents. */
    CATMULL_ROM;

    @Override
    public String getName() {
        return FormattingUtils.formatEnumName(this.name());
    }

    @Override
    public String getDescription() {
        return ServerToursAPI.getImplementation().getTranslation(
                "chatMenu.tooltips.rotationInterpolationModes." + this.name().toLowerCase()
        );
    }
}
