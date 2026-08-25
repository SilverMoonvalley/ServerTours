package com.melluh.servertours.playback.camera;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraPlaybackSettingsTest {
    @Test
    void defaultsPreserveLegacyVehicleBackend() {
        CameraPlaybackSettings settings = CameraPlaybackSettings.load(new YamlConfiguration());

        assertEquals(JavaCameraBackend.VEHICLE, settings.javaBackend());
        assertEquals(3, settings.interpolationTicks());
        assertEquals(10, settings.anchorIntervalFrames());
        assertEquals(25.0, settings.maxAnchorDistance());
    }

    @Test
    void readsDisplaySettingsCaseInsensitively() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("playMode.camera.javaBackend", "display");
        configuration.set("playMode.camera.interpolationTicks", 4);
        configuration.set("playMode.camera.anchorIntervalFrames", 7);
        configuration.set("playMode.camera.maxAnchorDistance", 32.5);

        CameraPlaybackSettings settings = CameraPlaybackSettings.load(configuration);

        assertEquals(JavaCameraBackend.DISPLAY, settings.javaBackend());
        assertEquals(4, settings.interpolationTicks());
        assertEquals(7, settings.anchorIntervalFrames());
        assertEquals(32.5, settings.maxAnchorDistance());
    }

    @Test
    void invalidValuesFallBackToSafeDefaults() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("playMode.camera.javaBackend", "unknown");
        configuration.set("playMode.camera.interpolationTicks", 60);
        configuration.set("playMode.camera.anchorIntervalFrames", 0);
        configuration.set("playMode.camera.maxAnchorDistance", Double.NaN);

        CameraPlaybackSettings settings = CameraPlaybackSettings.load(configuration);

        assertEquals(JavaCameraBackend.VEHICLE, settings.javaBackend());
        assertEquals(3, settings.interpolationTicks());
        assertEquals(10, settings.anchorIntervalFrames());
        assertEquals(25.0, settings.maxAnchorDistance());
    }
}
