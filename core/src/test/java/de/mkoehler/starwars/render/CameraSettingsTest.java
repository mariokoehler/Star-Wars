package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link CameraSettings}' zoom stepping and clamping — the pure
 * part of the manual-zoom feature (design.md 4.1); the persistence half
 * ({@link CameraSettingsStore}) touches {@code Gdx.files} and is left
 * untested, same as this project's other local-config stores.
 */
class CameraSettingsTest {

    private static final float TOLERANCE = 1e-4f;

    @Test
    void defaultsToTheUnmodifiedZoomLevel() {
        assertEquals(1f, new CameraSettings().getZoom(), TOLERANCE);
    }

    @Test
    void zoomingInLowersTheMultiplier() {
        // Smaller is more magnified, matching OrthographicCamera.zoom's own convention.
        CameraSettings settings = new CameraSettings();
        settings.zoomIn();

        assertTrue(settings.getZoom() < 1f, "expected zoom in to lower the multiplier");
    }

    @Test
    void zoomingOutRaisesTheMultiplier() {
        CameraSettings settings = new CameraSettings();
        settings.zoomOut();

        assertTrue(settings.getZoom() > 1f, "expected zoom out to raise the multiplier");
    }

    @Test
    void zoomingInStopsAtTheMinimum() {
        CameraSettings settings = new CameraSettings();
        for (int i = 0; i < 200; i++) {
            settings.zoomIn();
        }

        assertEquals(CameraSettings.MIN_ZOOM, settings.getZoom(), TOLERANCE);
    }

    @Test
    void zoomingOutStopsAtTheMaximum() {
        CameraSettings settings = new CameraSettings();
        for (int i = 0; i < 200; i++) {
            settings.zoomOut();
        }

        assertEquals(CameraSettings.MAX_ZOOM, settings.getZoom(), TOLERANCE);
    }

    @Test
    void aStepInAndBackOutReturnsToTheStartingZoom() {
        // Geometric stepping, so in-then-out is exactly the identity rather than drifting.
        CameraSettings settings = new CameraSettings();
        settings.zoomIn();
        settings.zoomOut();

        assertEquals(1f, settings.getZoom(), TOLERANCE);
    }

    @Test
    void inAndOutTakeTheSameNumberOfPressesToReachTheirLimits() {
        // The reason for stepping geometrically rather than by a fixed amount: the range is
        // asymmetric around 1 (0.5 below, 2.0 above), so a fixed step would make one direction
        // take twice as many presses as the other.
        CameraSettings in = new CameraSettings();
        int pressesToMinimum = 0;
        while (in.getZoom() > CameraSettings.MIN_ZOOM + TOLERANCE) {
            in.zoomIn();
            pressesToMinimum++;
        }

        CameraSettings out = new CameraSettings();
        int pressesToMaximum = 0;
        while (out.getZoom() < CameraSettings.MAX_ZOOM - TOLERANCE) {
            out.zoomOut();
            pressesToMaximum++;
        }

        assertEquals(pressesToMaximum, pressesToMinimum);
    }

    @Test
    void anOutOfRangeSavedValueIsClampedOnLoad() {
        // Jackson calls the setter, so a hand-edited camera-settings.json can't put the camera
        // somewhere the in-game keybinds couldn't reach.
        CameraSettings settings = new CameraSettings();
        settings.setZoom(37f);

        assertEquals(CameraSettings.MAX_ZOOM, settings.getZoom(), TOLERANCE);

        settings.setZoom(-4f);

        assertEquals(CameraSettings.MIN_ZOOM, settings.getZoom(), TOLERANCE);
    }
}
