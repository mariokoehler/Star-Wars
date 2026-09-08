package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link RadarDetection}'s base/cone/pulse detection geometry
 * (design.md 2.14).
 */
class RadarDetectionTest {

    @Test
    void baseRadarDetectsOmnidirectionallyWithinRange() {
        // Target due south (behind the observer, which faces "north"/0 rad) - base radar has no
        // facing requirement, only cone radar does.
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, -50f,
            true, 60f, false, 0f, 0f, false, 0f);
        assertTrue(detected);
    }

    @Test
    void baseRadarDoesNotDetectBeyondItsRange() {
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, 61f,
            true, 60f, false, 0f, 0f, false, 0f);
        assertFalse(detected);
    }

    @Test
    void baseRadarDetectsNothingWhenDisabled() {
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, 10f,
            false, 60f, false, 0f, 0f, false, 0f);
        assertFalse(detected);
    }

    @Test
    void coneRadarDetectsAheadBeyondBaseRange() {
        // Target due north (straight ahead), 100m out - beyond a 60m base range but within a
        // 120m cone range and dead-center of a +/-30 degree arc.
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, 100f,
            true, 60f, true, 120f, 30f, false, 0f);
        assertTrue(detected);
    }

    @Test
    void coneRadarDoesNotDetectOutsideTheArc() {
        // Target due east (90 degrees off the observer's north-facing heading) - well outside a
        // +/-30 degree arc, even though it's within the cone's range.
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 100f, 0f,
            false, 0f, true, 120f, 30f, false, 0f);
        assertFalse(detected);
    }

    @Test
    void coneRadarDetectsJustInsideItsHalfAngleBoundary() {
        float distance = 100f;
        // A point just inside the +30 degree boundary from "north" - a hair under the exact
        // boundary to avoid a sin/cos-then-atan2 round trip landing a float epsilon past it.
        float angleFromNorth = MathUtils.degreesToRadians * 29.99f;
        float targetX = -MathUtils.sin(angleFromNorth) * distance;
        float targetY = MathUtils.cos(angleFromNorth) * distance;

        boolean detected = RadarDetection.detects(0f, 0f, 0f, targetX, targetY,
            false, 0f, true, 120f, 30f, false, 0f);
        assertTrue(detected);
    }

    @Test
    void coneRadarDoesNotDetectJustPastItsHalfAngleBoundary() {
        float distance = 100f;
        float angleFromNorth = MathUtils.degreesToRadians * 31f;
        float targetX = -MathUtils.sin(angleFromNorth) * distance;
        float targetY = MathUtils.cos(angleFromNorth) * distance;

        boolean detected = RadarDetection.detects(0f, 0f, 0f, targetX, targetY,
            false, 0f, true, 120f, 30f, false, 0f);
        assertFalse(detected);
    }

    @Test
    void coneRadarDetectsRegardlessOfObserverFacingRotation() {
        // Observer faces east (-90 degrees, this project's convention); target is 100m due east,
        // i.e. dead ahead relative to that facing.
        boolean detected = RadarDetection.detects(0f, 0f, -MathUtils.HALF_PI, 100f, 0f,
            false, 0f, true, 120f, 30f, false, 0f);
        assertTrue(detected);
    }

    @Test
    void coneRadarDetectsNothingWhenDisabledEvenWithinTheArc() {
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, 50f,
            false, 0f, false, 120f, 30f, false, 0f);
        assertFalse(detected);
    }

    @Test
    void activePulseDetectsOmnidirectionallyBeyondBaseAndConeRange() {
        // Target due south (outside the forward cone entirely), 150m out - beyond both base and
        // cone range, but within an active 200m pulse.
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, -150f,
            true, 60f, true, 120f, 30f, true, 200f);
        assertTrue(detected);
    }

    @Test
    void inactivePulseDoesNotExtendDetectionRange() {
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 0f, -150f,
            true, 60f, true, 120f, 30f, false, 200f);
        assertFalse(detected);
    }

    @Test
    void detectsNothingWhenAllThreeMechanismsMiss() {
        boolean detected = RadarDetection.detects(0f, 0f, 0f, 500f, 500f,
            true, 60f, true, 120f, 30f, true, 200f);
        assertFalse(detected);
    }
}
