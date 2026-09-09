package de.mkoehler.starwars.render;

import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    @Test
    void boundaryEdgeFarBeyondMaxRangeIsEmpty() {
        // Observer at the arena's center; the edge at perp=250 is far past a 60m radar.
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(0f, 0f, 250f, -250f, 250f, 150f, 60f);
        assertTrue(placement.isEmpty());
    }

    @Test
    void boundaryEdgeDirectlyUnderTheObserverSpansTheFullChordAtPerpOffsetZero() {
        // Observer sitting exactly on the edge (perp distance 0) - the visible chord should
        // extend the scope's full radius either side, not clipped by the edge's own extent
        // (edge spans -250..250, well beyond the 60m chord this produces).
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(0f, 0f, 0f, -250f, 250f, 150f, 60f);
        assertTrue(placement.isPresent());
        RadarScopeMath.BoundaryLinePlacement line = placement.get();
        assertEquals(0f, line.perpOffset(), TOLERANCE);
        assertEquals(-150f, line.alongStart(), TOLERANCE); // full scope radius, scale = 150/60
        assertEquals(150f, line.alongEnd(), TOLERANCE);
    }

    @Test
    void boundaryEdgeAtAKnownPerpDistanceUsesCircleChordGeometry() {
        // 60m radar, edge 36m away perpendicular - a 3-4-5 triangle (36, 48, 60), so the chord
        // should extend 48m either side of the observer's own along-position.
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(0f, 0f, 36f, -250f, 250f, 150f, 60f);
        assertTrue(placement.isPresent());
        RadarScopeMath.BoundaryLinePlacement line = placement.get();
        float scale = 150f / 60f;
        assertEquals(36f * scale, line.perpOffset(), TOLERANCE);
        assertEquals(-48f * scale, line.alongStart(), TOLERANCE);
        assertEquals(48f * scale, line.alongEnd(), TOLERANCE);
    }

    @Test
    void boundaryEdgeNearACornerIsClippedByTheEdgesOwnExtentNotJustTheCircle() {
        // Observer 10m from the top edge (y=240, edge at y=250) and only 5m from the corner at
        // x=250 - the visible chord (radius up to 60m either way) would extend well past x=250,
        // so it must clip there instead of overshooting past the edge's own finite extent.
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(245f, 240f, 250f, -250f, 250f, 150f, 60f);
        assertTrue(placement.isPresent());
        RadarScopeMath.BoundaryLinePlacement line = placement.get();
        float scale = 150f / 60f;
        // Clipped to the edge's own max (250), not the wider circle-chord bound.
        assertEquals((250f - 245f) * scale, line.alongEnd(), TOLERANCE);
    }

    @Test
    void boundaryEdgeEntirelyPastItsOwnExtentIsEmpty() {
        // Observer positioned such that the visible circle-chord along this edge would fall
        // entirely beyond the edge's own extent (edge only runs to x=250, observer effectively
        // "past the corner" relative to this edge's own segment).
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(400f, 0f, 0f, -250f, 250f, 150f, 60f);
        assertTrue(placement.isEmpty());
    }

    @Test
    void boundaryEdgeHandlesAZeroMaxRangeWithoutDividingByZero() {
        Optional<RadarScopeMath.BoundaryLinePlacement> placement =
            RadarScopeMath.computeBoundaryLine(0f, 0f, 10f, -250f, 250f, 150f, 0f);
        assertTrue(placement.isEmpty());
    }
}
