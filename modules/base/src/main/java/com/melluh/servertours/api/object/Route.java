package com.melluh.servertours.api.object;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Route {
    RoutePoint createPoint(Location p0, RoutePointType p1);

    RoutePoint insertPoint(int p0, Location p1, RoutePointType p2);

    RoutePoint getPoint(int p0);

    List<? extends RoutePoint> getPoints();

    int getNumPoints();

    int indexOf(RoutePoint p0);

    void removePoint(int p0);

    void removePoint(RoutePoint p0);

    String getName();

    /** Returns which built-in camera track this route uses. */
    default CameraSource getCameraSource() {
        return CameraSource.POINTS;
    }

    default void setCameraSource(CameraSource source) {
        throw new UnsupportedOperationException("this route does not support configurable camera sources");
    }

    /** Returns the retained recording reference, including while the point source is selected. */
    default Optional<UUID> getCameraRecordingId() {
        return Optional.empty();
    }

    default void setCameraRecordingId(UUID recordingId) {
        throw new UnsupportedOperationException("this route does not support recorded cameras");
    }

    /** Detaches the recording and selects the point camera source. */
    default void clearCameraRecording() {
        throw new UnsupportedOperationException("this route does not support recorded cameras");
    }

    /**
     * Returns the positional interpolation used by this route.
     *
     * <p>The default keeps third-party {@code Route} implementations binary
     * compatible while selecting the current ServerTours default.</p>
     */
    default PositionInterpolationMode getPositionInterpolationMode() {
        return PositionInterpolationMode.CENTRIPETAL_CATMULL_ROM;
    }

    default void setPositionInterpolationMode(PositionInterpolationMode mode) {
        throw new UnsupportedOperationException("this route does not support configurable position interpolation");
    }

    /** Returns the rotation interpolation used by this route. */
    default RotationInterpolationMode getRotationInterpolationMode() {
        return RotationInterpolationMode.CATMULL_ROM;
    }

    default void setRotationInterpolationMode(RotationInterpolationMode mode) {
        throw new UnsupportedOperationException("this route does not support configurable rotation interpolation");
    }

    void saveToDisk();
}
