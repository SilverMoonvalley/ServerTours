package com.melluh.servertours.route;

import com.melluh.servertours.ServerTours;
import com.melluh.servertours.api.object.PositionInterpolationMode;
import com.melluh.servertours.api.object.RoutePointType;
import com.melluh.servertours.api.object.RotationInterpolationMode;
import com.melluh.servertours.editmode.EditModeManager;
import com.melluh.servertours.route.point.CraftInterpolatePoint;
import com.melluh.servertours.util.math.SineEasingFunction;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CraftRouteInterpolationSettingsTest {
    private MockedStatic<ServerTours> serverTours;

    @BeforeEach
    void mockPlugin() {
        ServerTours plugin = mock(ServerTours.class);
        EditModeManager editModeManager = mock(EditModeManager.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(plugin.getEditModeManager()).thenReturn(editModeManager);
        when(editModeManager.getEditingPlayer(any(CraftRoute.class))).thenReturn(null);
        when(plugin.getDescription()).thenReturn(description);
        when(description.getVersion()).thenReturn("test");
        this.serverTours = mockStatic(ServerTours.class);
        this.serverTours.when(ServerTours::getInstance).thenReturn(plugin);
    }

    @AfterEach
    void closeStaticMock() {
        this.serverTours.close();
    }

    @Test
    void missingSchemaAndSettingsUseNewDefaultsWithoutMutatingLoadedYaml() {
        YamlConfiguration yaml = routeYaml("old-route");

        CraftRoute route = new CraftRoute(yaml);

        assertEquals(PositionInterpolationMode.CENTRIPETAL_CATMULL_ROM,
                route.getPositionInterpolationMode());
        assertEquals(RotationInterpolationMode.CATMULL_ROM,
                route.getRotationInterpolationMode());
        assertFalse(yaml.contains("versions.schema"));
        assertFalse(yaml.contains("camera"));
    }

    @Test
    void explicitLegacyModesRemainAvailable() {
        YamlConfiguration yaml = routeYaml("legacy-route");
        yaml.set("versions.schema", 1);
        yaml.set("camera.positionInterpolation", "LEGACY_CARDINAL");
        yaml.set("camera.rotationInterpolation", "LINEAR_SHORTEST_PATH");

        CraftRoute route = new CraftRoute(yaml);

        assertEquals(PositionInterpolationMode.LEGACY_CARDINAL, route.getPositionInterpolationMode());
        assertEquals(RotationInterpolationMode.LINEAR_SHORTEST_PATH, route.getRotationInterpolationMode());
    }

    @Test
    void nextSaveWritesSchemaTwoAndExplicitModes() {
        CraftRoute route = new CraftRoute(routeYaml("migrated-route"));
        YamlConfiguration saved = new YamlConfiguration();

        route.saveTo(saved);

        assertEquals(2, saved.getInt("versions.schema"));
        assertEquals("CENTRIPETAL_CATMULL_ROM", saved.getString("camera.positionInterpolation"));
        assertEquals("CATMULL_ROM", saved.getString("camera.rotationInterpolation"));

        CraftRoute reloaded = new CraftRoute(saved);
        assertEquals(PositionInterpolationMode.CENTRIPETAL_CATMULL_ROM,
                reloaded.getPositionInterpolationMode());
        assertEquals(RotationInterpolationMode.CATMULL_ROM,
                reloaded.getRotationInterpolationMode());
    }

    @Test
    void newRoutesUseNewDefaultsAndAllowExplicitFallback() {
        CraftRoute route = new CraftRoute("new-route");
        assertEquals(PositionInterpolationMode.CENTRIPETAL_CATMULL_ROM,
                route.getPositionInterpolationMode());
        assertEquals(RotationInterpolationMode.CATMULL_ROM,
                route.getRotationInterpolationMode());

        route.setPositionInterpolationMode(PositionInterpolationMode.LEGACY_CARDINAL);
        route.setRotationInterpolationMode(RotationInterpolationMode.LINEAR_SHORTEST_PATH);
        assertEquals(PositionInterpolationMode.LEGACY_CARDINAL, route.getPositionInterpolationMode());
        assertEquals(RotationInterpolationMode.LINEAR_SHORTEST_PATH, route.getRotationInterpolationMode());
        assertThrows(NullPointerException.class, () -> route.setPositionInterpolationMode(null));
        assertThrows(NullPointerException.class, () -> route.setRotationInterpolationMode(null));
    }

    @Test
    void invalidConfiguredModeRejectsRouteWithFieldContext() {
        YamlConfiguration yaml = routeYaml("invalid-route");
        yaml.set("camera.positionInterpolation", "not-a-spline");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new CraftRoute(yaml));
        assertTrue(error.getMessage().contains("camera.positionInterpolation"));
        assertTrue(error.getMessage().contains("invalid-route"));
    }

    @Test
    void interpolatePointUsesSelectedPositionAndRotationModes() {
        CraftRoute route = new CraftRoute("camera-route");
        CraftInterpolatePoint from = (CraftInterpolatePoint) route.createPoint(
                new Location(null, 0.0, 0.0, 0.0, 170.0f, 5.0f),
                RoutePointType.INTERPOLATE
        );
        route.createPoint(
                new Location(null, 10.0, 3.0, -4.0, -170.0f, 25.0f),
                RoutePointType.STATIONARY
        );

        Location modernEnd = from.getPlaybackLocation(1.0f, new SineEasingFunction());
        assertEquals(10.0, modernEnd.getX(), 1.0e-6);
        assertEquals(3.0, modernEnd.getY(), 1.0e-6);
        assertEquals(-4.0, modernEnd.getZ(), 1.0e-6);
        assertEquals(190.0, modernEnd.getYaw(), 1.0e-5);
        assertEquals(25.0, modernEnd.getPitch(), 1.0e-5);

        route.setPositionInterpolationMode(PositionInterpolationMode.LEGACY_CARDINAL);
        route.setRotationInterpolationMode(RotationInterpolationMode.LINEAR_SHORTEST_PATH);
        Location legacyEnd = from.getPlaybackLocation(1.0f, new SineEasingFunction());
        assertEquals(10.0, legacyEnd.getX(), 1.0e-6);
        assertEquals(3.0, legacyEnd.getY(), 1.0e-6);
        assertEquals(-4.0, legacyEnd.getZ(), 1.0e-6);
        assertEquals(190.0, legacyEnd.getYaw(), 1.0e-5);
        assertEquals(25.0, legacyEnd.getPitch(), 1.0e-5);
    }

    private static YamlConfiguration routeYaml(String name) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("usePlayerWorld", false);
        return yaml;
    }
}
