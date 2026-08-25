package com.melluh.servertours.file;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledYamlResourcesTest {
    @Test
    void cameraDefaultsAndInterpolationTranslationsAreValidYaml() {
        YamlConfiguration config = load("config.yml");
        assertFalse(config.contains("playMode.camera.javaBackend"));
        assertEquals(3, config.getInt("playMode.camera.interpolationTicks"));
        assertEquals(300L, config.getLong("recording.maxDurationSeconds"));

        YamlConfiguration lang = load("lang.yml");
        assertNotNull(lang.getString("chatMenu.labels.positionInterpolation"));
        assertNotNull(lang.getString("chatMenu.labels.rotationInterpolation"));
        assertNotNull(lang.getString(
                "chatMenu.tooltips.positionInterpolationModes.centripetal_catmull_rom"));
        assertNotNull(lang.getString("chatMenu.tooltips.rotationInterpolationModes.catmull_rom"));
        for (String key : List.of(
                "started", "resumed", "processing", "saved", "cancelled", "draftSaved",
                "noDrafts", "draftsHeader", "draftLine", "actionBar", "discarded", "sourceChanged",
                "errors.notReady", "errors.invalidName", "errors.nameReserved",
                "errors.invalidSettings", "errors.setupFailed", "errors.invalidPlayerState",
                "errors.javaOnly", "errors.alreadyRecording", "errors.notRecording",
                "errors.tooShort", "errors.draftNotFound", "errors.notDraftOwner",
                "errors.processing", "errors.worldUnavailable", "errors.saveFailed",
                "errors.compileFailed", "errors.commitFailed", "errors.recordingUnavailable")) {
            assertNotNull(lang.getString("commands.record." + key),
                    "missing recording translation commands.record." + key);
        }
    }

    private static YamlConfiguration load(String resourceName) {
        InputStream stream = BundledYamlResourcesTest.class.getClassLoader()
                .getResourceAsStream(resourceName);
        assertNotNull(stream, "missing bundled resource " + resourceName);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new AssertionError("failed to load " + resourceName, exception);
        }
    }
}
