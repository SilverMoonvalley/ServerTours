package com.melluh.servertours.recording.math;

import com.melluh.servertours.recording.model.CompiledRecording;
import com.melluh.servertours.recording.model.RecordingSample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingCompilerTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void compilesConstantVelocityPathToImmutableEndpoints() {
        List<RecordingSample> mutableRaw = new ArrayList<>(List.of(
                sample(0L, 0.0, 0.0, 0.0),
                sample(50_000_000L, 1.0, 10.0, 2.0),
                sample(100_000_000L, 2.0, 20.0, 4.0),
                sample(150_000_001L, 3.0, 30.0, 6.0)
        ));

        CompiledRecording compiled = new RecordingCompiler().compile(mutableRaw);
        mutableRaw.clear();

        assertEquals(List.of(0, 3), compiled.keyframeIndices());
        assertEquals(150_000_001L, compiled.durationNanos());
        assertEquals(4L, compiled.endFrame());
        assertPose(compiled.sampleAt(75_000_000L), 1.49999999, 14.9999999, 2.99999998, 1.0e-7);
        assertPose(compiled.sampleAt(Long.MAX_VALUE), 3.0, 30.0, 6.0, EPSILON);
        assertThrows(UnsupportedOperationException.class,
                () -> compiled.rawSamples().add(sample(200_000_000L, 4.0, 0.0, 0.0)));
        assertThrows(UnsupportedOperationException.class, () -> compiled.keyframeIndices().add(2));
    }

    @Test
    void validatesCurveAtRawTimesAndMidpointsThenFallsBackFromOvershoot() {
        List<RecordingSample> raw = List.of(
                sample(0L, 0.0, 0.0, 0.0),
                sample(50_000_000L, 1.0, 0.0, 0.0),
                sample(100_000_000L, 0.0, 0.0, 0.0)
        );

        CompiledRecording compiled = new RecordingCompiler().compile(raw);

        assertEquals(List.of(0, 1, 2), compiled.keyframeIndices());
        assertEquals(List.of(0, 1), compiled.linearSegments());
        assertEquals(0.5, compiled.sampleAt(25_000_000L).x(), EPSILON);
        assertEquals(0.5, compiled.sampleAt(75_000_000L).x(), EPSILON);
    }

    @Test
    void nonUniformHermiteUsesRealTimestampSpacingAndHitsEveryKeyExactly() {
        List<RecordingSample> raw = List.of(
                sample(0L, 0.0, 170.0, 0.0),
                sample(50_000_000L, 1.0, 190.0, 5.0),
                sample(200_000_000L, 2.0, 220.0, 10.0)
        );
        CompiledRecording compiled = new CompiledRecording(raw, List.of(0, 1, 2), List.of());

        assertEquals(raw.get(0), compiled.sampleAt(0L));
        assertEquals(raw.get(1), compiled.sampleAt(50_000_000L));
        assertEquals(raw.get(2), compiled.sampleAt(200_000_000L));
        RecordingSample between = compiled.sampleAt(100_000_000L);
        assertTrue(between.x() > 1.0 && between.x() < 2.0);
        assertTrue(between.yawUnwrapped() > 190.0 && between.yawUnwrapped() < 220.0);
    }

    @Test
    void rejectsMalformedTimelinesAndCompiledIndices() {
        RecordingSample atZero = sample(0L, 0.0, 0.0, 0.0);
        RecordingSample later = sample(50_000_000L, 1.0, 0.0, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> new RecordingCompiler().compile(List.of(sample(1L, 0.0, 0.0, 0.0))));
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingCompiler().compile(List.of(atZero, atZero)));
        assertThrows(IllegalArgumentException.class,
                () -> new CompiledRecording(List.of(atZero, later), List.of(1), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CompiledRecording(List.of(atZero, later), List.of(0, 1), List.of(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingSample(0L, Double.NaN, 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RecordingSample(0L, 0.0, 0.0, 0.0, 0.0, 91.0));
    }

    @Test
    void compiledCurveAlwaysHonorsRawAndAdjacentMidpointErrorBounds() {
        Random random = new Random(0x5EEDC0DEL);
        RecordingTolerances tolerances = RecordingTolerances.DEFAULT;
        RecordingCompiler compiler = new RecordingCompiler(tolerances);
        for (int recording = 0; recording < 40; recording++) {
            List<RecordingSample> raw = randomWalk(random, 24);
            CompiledRecording compiled = compiler.compile(raw);

            for (int index = 0; index < raw.size(); index++) {
                RecordingSample expected = raw.get(index);
                assertTrue(tolerances.normalizedError(expected,
                                compiled.sampleAt(expected.timeNanos())) <= 1.0 + 1.0e-9,
                        "Raw sample exceeded tolerance at index " + index);
                if (index == raw.size() - 1) {
                    continue;
                }

                RecordingSample next = raw.get(index + 1);
                long midpoint = expected.timeNanos()
                        + (next.timeNanos() - expected.timeNanos()) / 2L;
                RecordingSample midpointExpected = linear(expected, next, midpoint);
                assertTrue(tolerances.normalizedError(midpointExpected,
                                compiled.sampleAt(midpoint)) <= 1.0 + 1.0e-9,
                        "Midpoint exceeded tolerance after raw index " + index);
            }
        }
    }

    private static RecordingSample sample(long time, double x, double yaw, double pitch) {
        return new RecordingSample(time, x, 0.0, 0.0, yaw, pitch);
    }

    private static List<RecordingSample> randomWalk(Random random, int size) {
        List<RecordingSample> samples = new ArrayList<>(size);
        long time = 0L;
        double x = 0.0;
        double y = 64.0;
        double z = 0.0;
        double yaw = 0.0;
        double pitch = 0.0;
        for (int index = 0; index < size; index++) {
            samples.add(new RecordingSample(time, x, y, z, yaw, pitch));
            time += 1L + random.nextLong(20_000_000L, 100_000_001L);
            x += random.nextDouble(-0.4, 0.4);
            y += random.nextDouble(-0.2, 0.2);
            z += random.nextDouble(-0.4, 0.4);
            yaw += random.nextDouble(-15.0, 15.0);
            pitch = Math.max(-90.0, Math.min(90.0, pitch + random.nextDouble(-5.0, 5.0)));
        }
        return samples;
    }

    private static RecordingSample linear(RecordingSample start, RecordingSample end, long time) {
        double progress = (time - start.timeNanos())
                / (double) (end.timeNanos() - start.timeNanos());
        return new RecordingSample(
                time,
                start.x() + (end.x() - start.x()) * progress,
                start.y() + (end.y() - start.y()) * progress,
                start.z() + (end.z() - start.z()) * progress,
                start.yawUnwrapped() + (end.yawUnwrapped() - start.yawUnwrapped()) * progress,
                start.pitch() + (end.pitch() - start.pitch()) * progress
        );
    }

    private static void assertPose(RecordingSample sample, double x, double yaw,
                                   double pitch, double epsilon) {
        assertEquals(x, sample.x(), epsilon);
        assertEquals(yaw, sample.yawUnwrapped(), epsilon);
        assertEquals(pitch, sample.pitch(), epsilon);
    }
}
