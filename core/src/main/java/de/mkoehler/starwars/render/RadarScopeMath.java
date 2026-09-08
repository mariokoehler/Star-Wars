package de.mkoehler.starwars.render;

import com.badlogic.gdx.math.MathUtils;

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
}
