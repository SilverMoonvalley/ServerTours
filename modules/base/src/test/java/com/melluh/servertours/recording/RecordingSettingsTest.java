package com.melluh.servertours.recording;

import com.melluh.servertours.recording.math.RecordingTolerances;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingSettingsTest {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Test
    void loadsDocumentedDefaults() {
        RecordingSettings settings = RecordingSettings.load(new YamlConfiguration());

        assertEquals(300L * NANOS_PER_SECOND, settings.maxDurationNanos());
        assertEquals(RecordingTolerances.DEFAULT, settings.tolerances());
    }

    @Test
    void loadsConfiguredDurationAndErrorTolerances() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("recording.maxDurationSeconds", 42L);
        configuration.set("recording.positionTolerance", 0.125D);
        configuration.set("recording.rotationToleranceDegrees", 1.25D);

        RecordingSettings settings = RecordingSettings.load(configuration);

        assertEquals(42L * NANOS_PER_SECOND, settings.maxDurationNanos());
        assertEquals(new RecordingTolerances(0.125D, 1.25D, 1.25D), settings.tolerances());
    }

    @Test
    void rejectsNonPositiveAndOverflowingDurations() {
        YamlConfiguration zero = new YamlConfiguration();
        zero.set("recording.maxDurationSeconds", 0L);
        YamlConfiguration negative = new YamlConfiguration();
        negative.set("recording.maxDurationSeconds", -1L);
        YamlConfiguration overflow = new YamlConfiguration();
        overflow.set("recording.maxDurationSeconds", Long.MAX_VALUE / NANOS_PER_SECOND + 1L);

        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(zero));
        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(negative));
        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(overflow));
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingSettings(0L, RecordingTolerances.DEFAULT));
    }

    @Test
    void rejectsInvalidPositionAndRotationTolerances() {
        YamlConfiguration zeroPosition = validConfiguration();
        zeroPosition.set("recording.positionTolerance", 0.0D);
        YamlConfiguration negativeRotation = validConfiguration();
        negativeRotation.set("recording.rotationToleranceDegrees", -0.5D);
        YamlConfiguration nonFinitePosition = validConfiguration();
        nonFinitePosition.set("recording.positionTolerance", Double.NaN);
        YamlConfiguration nonFiniteRotation = validConfiguration();
        nonFiniteRotation.set("recording.rotationToleranceDegrees", Double.POSITIVE_INFINITY);

        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(zeroPosition));
        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(negativeRotation));
        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(nonFinitePosition));
        assertThrows(IllegalArgumentException.class, () -> RecordingSettings.load(nonFiniteRotation));
    }

    @Test
    void rejectsNullConfigurationAndTolerances() {
        assertThrows(NullPointerException.class, () -> RecordingSettings.load(null));
        assertThrows(NullPointerException.class, () -> new RecordingSettings(NANOS_PER_SECOND, null));
    }

    private static YamlConfiguration validConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("recording.maxDurationSeconds", 300L);
        configuration.set("recording.positionTolerance", 0.05D);
        configuration.set("recording.rotationToleranceDegrees", 0.5D);
        return configuration;
    }
}
