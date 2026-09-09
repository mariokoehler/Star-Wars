package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.MathUtils;

import java.util.Optional;

/**
 * Pure geometry for placing a contact on {@link RadarHud}'s scope — pulled
 * out of it specifically so this arithmetic is unit-testable without a GL
 * context, same "logic-heavy pure function, separate from rendering/GL
 * wiring" convention as {@link HudGaugeClip}.
 * <p>
 * The scope is north-up and fixed (design.md 2.14's rendering addendum) —
 * it never rotates with the observer's own facing, so a contact's angle on
 * the scope is simply its raw world bearing from the observer, no
 * subtraction of the observer's own facing needed (unlike the cone overlay,
 * which does rotate to track the observer's facing, handled directly in
 * {@link RadarHud}, not here). Angle convention matches the rest of this
 * project: 0 radians/degrees faces "north" (+Y), increasing counter-clockwise.
 */
final class RadarScopeMath {

    private RadarScopeMath() {
    }

    /**
     * Where a contact lands on the scope, and whether it had to be clamped.
     *
     * @param bearingDegrees the contact's world bearing from the observer,
     *                       in degrees — the angle to rotate a "points
     *                       north" marker sprite by
     * @param radiusFraction how far out from the scope's center to place the
     *                       contact, as a fraction of the scope's radius,
     *                       in {@code [0, 1]}
     * @param clamped        {@code true} if the contact's true distance
     *                       exceeds {@code maxRangeMeters} and
     *                       {@code radiusFraction} was clamped to the
     *                       scope's edge (design.md 2.14 — a pulse-revealed
     *                       contact beyond the observer's own equipment)
     */
    record BlipPlacement(float bearingDegrees, float radiusFraction, boolean clamped) {
    }

    /**
     * Computes where a contact belongs on the scope.
     *
     * @param observerX      the observer's X position, in meters
     * @param observerY      the observer's Y position, in meters
     * @param targetX        the contact's X position, in meters
     * @param targetY        the contact's Y position, in meters
     * @param maxRangeMeters the observer's own largest enabled radar range
     *                       (see {@code ShipStats#getRadarMaxRangeMeters()}) —
     *                       the distance the scope's outer edge represents
     * @return the computed placement
     */
    static BlipPlacement computeBlipPlacement(float observerX, float observerY, float targetX, float targetY,
                                               float maxRangeMeters) {
        float dx = targetX - observerX;
        float dy = targetY - observerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float bearingDegrees = bearingDegrees(dx, dy);

        if (maxRangeMeters <= 0f) {
            return new BlipPlacement(bearingDegrees, 0f, false);
        }
        boolean clamped = distance > maxRangeMeters;
        float radiusFraction = MathUtils.clamp(distance / maxRangeMeters, 0f, 1f);
        return new BlipPlacement(bearingDegrees, radiusFraction, clamped);
    }

    /**
     * Returns how far out from the scope's center a range ring belongs, as
     * a fraction of the scope's radius.
     *
     * @param rangeMeters    the ring's own range (e.g. the base radar's range)
     * @param maxRangeMeters the observer's own largest enabled radar range —
     *                       the distance the scope's outer edge represents
     * @return the radius fraction, in {@code [0, 1]}
     */
    static float rangeRadiusFraction(float rangeMeters, float maxRangeMeters) {
        return maxRangeMeters > 0f ? MathUtils.clamp(rangeMeters / maxRangeMeters, 0f, 1f) : 0f;
    }

    private static float bearingDegrees(float dx, float dy) {
        if (dx == 0f && dy == 0f) {
            return 0f; // degenerate: exactly coincident positions, bearing is arbitrary
        }
        // Inverse of this project's angle-to-direction convention, same formula
        // TurretAiming/RadarDetection use for the identical purpose.
        return MathUtils.atan2(-dx, dy) * MathUtils.radiansToDegrees;
    }

    /**
     * Where a straight, axis-aligned arena-boundary edge segment (design.md
     * — arena bounds) should be drawn on the scope — or empty if it's
     * entirely outside the scope's currently-displayed range. Generic over
     * which world axis is "along" the edge (the direction it runs) and
     * which is "perpendicular" to it, so one method covers all 4 edges
     * (top/bottom swap which axis is which relative to left/right — see
     * {@link RadarHud}'s call sites).
     * <p>
     * Because the scope is north-up and fixed (see this class's own
     * Javadoc — a contact's bearing-based placement reduces algebraically
     * to a plain scaled identity, {@code offset = worldDelta * scopeRadius
     * / maxRangeMeters}, once the trig cancels out), an axis-aligned world
     * line stays axis-aligned on the scope too — this never needs rotation,
     * just a circle/segment intersection followed by that same scaling.
     *
     * @param observerAlong  the observer's position along the edge's own axis, in meters
     * @param observerPerp   the observer's position on the perpendicular axis, in meters
     * @param edgePerp       the edge's fixed position on the perpendicular axis, in meters
     * @param edgeAlongMin   the edge's own extent's lower bound, in meters (e.g. {@code -HALF_SIZE_METERS})
     * @param edgeAlongMax   the edge's own extent's upper bound, in meters (e.g. {@code +HALF_SIZE_METERS})
     * @param scopeRadius    the scope's on-screen radius, in pixels
     * @param maxRangeMeters the observer's own largest enabled radar range —
     *                       the distance the scope's outer edge represents
     * @return the placement to draw, or empty if this edge doesn't reach
     * within {@code maxRangeMeters} of the observer at all
     */
    static Optional<BoundaryLinePlacement> computeBoundaryLine(
            float observerAlong, float observerPerp, float edgePerp, float edgeAlongMin, float edgeAlongMax,
            float scopeRadius, float maxRangeMeters) {
        if (maxRangeMeters <= 0f) {
            return Optional.empty();
        }
        float perpDistance = edgePerp - observerPerp;
        float absPerpDistance = Math.abs(perpDistance);
        if (absPerpDistance > maxRangeMeters) {
            return Optional.empty(); // this edge is further away than the scope shows at all
        }
        // Circle/line intersection: how far the visible chord extends either side of the
        // observer's own position along the edge, at this perpendicular distance.
        float halfChord = (float) Math.sqrt(
            (double) maxRangeMeters * maxRangeMeters - (double) absPerpDistance * absPerpDistance);
        float worldAlongMin = Math.max(observerAlong - halfChord, edgeAlongMin);
        float worldAlongMax = Math.min(observerAlong + halfChord, edgeAlongMax);
        if (worldAlongMin >= worldAlongMax) {
            return Optional.empty(); // clipped away entirely by the edge's own finite extent
        }
        float scale = scopeRadius / maxRangeMeters;
        return Optional.of(new BoundaryLinePlacement(
            perpDistance * scale, (worldAlongMin - observerAlong) * scale, (worldAlongMax - observerAlong) * scale));
    }

    /**
     * One arena-boundary edge segment's placement on the scope, in screen
     * pixels relative to the scope's own center — see
     * {@link #computeBoundaryLine} for how the two axes map to X/Y.
     *
     * @param perpOffset  the line's fixed offset along the perpendicular axis
     * @param alongStart  the visible segment's start, along the edge's own axis
     * @param alongEnd    the visible segment's end, along the edge's own axis
     */
    record BoundaryLinePlacement(float perpOffset, float alongStart, float alongEnd) {
    }
}
