package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.MathUtils;

/**
 * Pure radar detection geometry (design.md 2.14): given an observing ship's
 * position/facing and a candidate target's position, plus that observer's
 * three radar mechanisms' enabled/range/angle values, decides whether the
 * target is detected. No Ashley/Box2D dependency, so it's directly
 * unit-testable and reusable wherever it's needed (currently just
 * {@code RadarSystem}) — same "logic-heavy pure function, separate from
 * system wiring" convention as {@code TurretAiming}/{@code ShipDamage}/
 * {@code PowerDistribution}.
 * <p>
 * Deliberately does <b>not</b> know about the "a pulsing ship is visible to
 * everyone" rule (design.md 2.14) — that's a property of the *target*
 * independent of the observer's own equipment/range, checked separately by
 * the caller and OR'd with this method's result.
 * <p>
 * Angle convention throughout matches the rest of this project: 0 radians
 * faces "north" (+Y), increasing counter-clockwise.
 */
public final class RadarDetection {

    private RadarDetection() {
    }

    /**
     * Returns whether an observer detects a target via its base
     * (omnidirectional), cone (forward-facing arc), or active pulse radar.
     *
     * @param observerX               the observer's X position, in meters
     * @param observerY               the observer's Y position, in meters
     * @param observerAngleRadians    the observer's current facing angle, in radians
     * @param targetX                 the target's X position, in meters
     * @param targetY                 the target's Y position, in meters
     * @param baseEnabled             whether the observer's base radar is enabled
     * @param baseRangeMeters         the base radar's omnidirectional range
     * @param coneEnabled             whether the observer's cone radar is enabled
     * @param coneRangeMeters         the cone radar's range
     * @param coneHalfAngleDegrees    the cone radar's half-angle either side of the observer's facing
     * @param pulseActive             whether the observer's active pulse is currently active
     *                                (both {@link de.mkoehler.starwars.sim.components.RadarComponent#isPulseActive()}
     *                                and the observer's own pulse being enabled)
     * @param pulseRangeMeters        the pulse's omnidirectional range while active
     * @return {@code true} if the target is detected by any of the three mechanisms
     */
    public static boolean detects(float observerX, float observerY, float observerAngleRadians,
                                   float targetX, float targetY,
                                   boolean baseEnabled, float baseRangeMeters,
                                   boolean coneEnabled, float coneRangeMeters, float coneHalfAngleDegrees,
                                   boolean pulseActive, float pulseRangeMeters) {
        float dx = targetX - observerX;
        float dy = targetY - observerY;
        float distanceSq = dx * dx + dy * dy;

        if (baseEnabled && distanceSq <= baseRangeMeters * baseRangeMeters) {
            return true;
        }
        if (pulseActive && distanceSq <= pulseRangeMeters * pulseRangeMeters) {
            return true;
        }
        return coneEnabled && distanceSq <= coneRangeMeters * coneRangeMeters
            && isWithinForwardArc(dx, dy, observerAngleRadians, coneHalfAngleDegrees);
    }

    private static boolean isWithinForwardArc(float dx, float dy, float observerAngleRadians, float halfAngleDegrees) {
        if (dx == 0f && dy == 0f) {
            return true; // degenerate: exactly coincident positions, trivially "in front"
        }
        // Inverse of this project's angle-to-direction convention, same formula TurretAiming uses
        // for the identical purpose (Vector2(0,1).rotateRad(angle) -> (-sin(angle), cos(angle))).
        float bearingToTarget = MathUtils.atan2(-dx, dy);
        float relativeBearing = TurretAiming.angularDifference(bearingToTarget, observerAngleRadians);
        return Math.abs(relativeBearing) <= MathUtils.degreesToRadians * halfAngleDegrees;
    }
}
