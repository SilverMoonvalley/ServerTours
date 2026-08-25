package com.melluh.servertours.recording.storage;

import com.melluh.servertours.recording.math.RecordingTolerances;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable identity and capture settings shared by drafts and ready recordings. */
public record RecordingMetadata(UUID id, String routeName, UUID creatorId, String creatorName,
                                UUID worldId, String worldName, long createdAtEpochMillis,
                                long sampleIntervalNanos,
                                RecordingTolerances tolerances, int compilerVersion) {
    private static final Pattern ROUTE_NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    public RecordingMetadata {
        id = Objects.requireNonNull(id, "id may not be null");
        routeName = normalizeRouteName(routeName);
        creatorId = Objects.requireNonNull(creatorId, "creatorId may not be null");
        creatorName = requireNonBlank("creatorName", creatorName);
        worldId = Objects.requireNonNull(worldId, "worldId may not be null");
        worldName = requireNonBlank("worldName", worldName);
        if (createdAtEpochMillis <= 0L) {
            throw new IllegalArgumentException("createdAtEpochMillis must be greater than zero");
        }
        if (sampleIntervalNanos <= 0L) {
            throw new IllegalArgumentException("sampleIntervalNanos must be greater than zero");
        }
        tolerances = Objects.requireNonNull(tolerances, "tolerances may not be null");
        if (compilerVersion <= 0) {
            throw new IllegalArgumentException("compilerVersion must be greater than zero");
        }
    }

    public static String normalizeRouteName(String routeName) {
        String normalized = requireNonBlank("routeName", routeName).toLowerCase(Locale.ROOT);
        if (!ROUTE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "routeName must match [a-z0-9_-]{1,64}: " + routeName);
        }
        return normalized;
    }

    private static String requireNonBlank(String field, String value) {
        Objects.requireNonNull(value, field + " may not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " may not be blank");
        }
        return trimmed;
    }
}
