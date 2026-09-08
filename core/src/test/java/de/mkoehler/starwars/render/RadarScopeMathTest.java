package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link RadarScopeMath}'s scope-placement geometry (design.md
 * 2.14's rendering addendum).
 */
class RadarScopeMathTest {

    private static final float TOLERANCE = 1e-3f;

    @Test
    void placesAContactDueNorthAtBearingZero() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 0f, 50f, 200f);
        assertEquals(0f, placement.bearingDegrees(), TOLERANCE);
        assertEquals(0.25f, placement.radiusFraction(), TOLERANCE);
        assertFalse(placement.clamped());
    }

    @Test
    void placesAContactDueEastAtBearingMinus90() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 50f, 0f, 200f);
        assertEquals(-90f, placement.bearingDegrees(), TOLERANCE);
    }

    @Test
    void placesAContactDueSouthAtBearing180() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 0f, -50f, 200f);
        assertEquals(180f, Math.abs(placement.bearingDegrees()), TOLERANCE);
    }

    @Test
    void isRelativeToTheObserversOwnPosition() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(100f, 100f, 100f, 150f, 200f);
        assertEquals(0f, placement.bearingDegrees(), TOLERANCE);
        assertEquals(0.25f, placement.radiusFraction(), TOLERANCE);
    }

    @Test
    void clampsAContactBeyondMaxRangeToTheScopesEdge() {
        // A pulse-revealed contact 500m out - well beyond a 200m max range.
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 0f, 500f, 200f);
        assertTrue(placement.clamped());
        assertEquals(1f, placement.radiusFraction(), TOLERANCE);
        assertEquals(0f, placement.bearingDegrees(), TOLERANCE); // still the true bearing, just clamped radius
    }

    @Test
    void doesNotClampAContactExactlyAtMaxRange() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 0f, 200f, 200f);
        assertFalse(placement.clamped());
        assertEquals(1f, placement.radiusFraction(), TOLERANCE);
    }

    @Test
    void handlesAZeroMaxRangeWithoutDividingByZero() {
        RadarScopeMath.BlipPlacement placement =
            RadarScopeMath.computeBlipPlacement(0f, 0f, 10f, 10f, 0f);
        assertFalse(placement.clamped());
        assertEquals(0f, placement.radiusFraction(), TOLERANCE);
    }

    @Test
    void rangeRadiusFractionScalesLinearlyAgainstMaxRange() {
        assertEquals(0.3f, RadarScopeMath.rangeRadiusFraction(60f, 200f), TOLERANCE);
        assertEquals(0.6f, RadarScopeMath.rangeRadiusFraction(120f, 200f), TOLERANCE);
        assertEquals(1f, RadarScopeMath.rangeRadiusFraction(200f, 200f), TOLERANCE);
    }

    @Test
    void rangeRadiusFractionHandlesAZeroMaxRangeWithoutDividingByZero() {
        assertEquals(0f, RadarScopeMath.rangeRadiusFraction(60f, 0f), TOLERANCE);
    }
}
