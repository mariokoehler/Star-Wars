package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.MathUtils;

/**
 * Pure turret aiming math (design.md — turret weapons): computing a
 * lead/intercept angle against a moving target, and turning an aim angle
 * toward a desired one at a limited rate. No Box2D/Ashley/libGDX-application
 * dependency beyond {@link MathUtils}, so it's directly unit-testable and
 * usable identically wherever a turret needs it (currently just
 * {@code TurretSystem}), matching this project's convention of keeping
 * logic-heavy pure functions separate from system/rendering wiring (see
 * {@code ShipDamage}/{@code PowerDistribution} for the same split).
 * <p>
 * Angle convention throughout matches the rest of this project: 0 radians
 * faces "north" (+Y), increasing counter-clockwise — the same convention
 * {@code ShipControlSystem}/{@code WeaponSystem} already use.
 */
public final class TurretAiming {

    private TurretAiming() {
    }

    /**
     * Computes the angle a turret should aim at to hit a target moving at a
     * constant velocity, given a projectile that travels at a fixed speed —
     * the classic "firing solution" intercept problem, solved by finding the
     * smallest positive time {@code t} at which
     * {@code |targetPos + targetVel*t - shooterPos| = projectileSpeed * t}
     * (a quadratic in {@code t}). Falls back to aiming directly at the
     * target's current position when no valid positive-time solution exists
     * (e.g. the target is outrunning the projectile in every direction, or
     * is exactly stationary in a degenerate way) — still a reasonable aim,
     * just not a lead.
     *
     * @param shooterX       the shooter's X position, in meters
     * @param shooterY       the shooter's Y position, in meters
     * @param targetX        the target's current X position, in meters
     * @param targetY        the target's current Y position, in meters
     * @param targetVelX     the target's current X velocity, in meters/second
     * @param targetVelY     the target's current Y velocity, in meters/second
     * @param projectileSpeed the projectile's constant travel speed, in meters/second
     * @return the world-space angle to aim at, in radians
     */
    public static float computeLeadAngle(float shooterX, float shooterY, float targetX, float targetY,
                                          float targetVelX, float targetVelY, float projectileSpeed) {
        float relX = targetX - shooterX;
        float relY = targetY - shooterY;

        float a = targetVelX * targetVelX + targetVelY * targetVelY - projectileSpeed * projectileSpeed;
        float b = 2f * (relX * targetVelX + relY * targetVelY);
        float c = relX * relX + relY * relY;

        Float t = smallestPositiveRoot(a, b, c);
        float interceptX = relX;
        float interceptY = relY;
        if (t != null) {
            interceptX += targetVelX * t;
            interceptY += targetVelY * t;
        }
        return angleOfDirection(interceptX, interceptY);
    }

    private static Float smallestPositiveRoot(float a, float b, float c) {
        if (Math.abs(a) < 1e-6f) {
            // Linear case (target speed equals projectile speed, or both are stationary): b*t + c = 0.
            if (Math.abs(b) < 1e-6f) {
                return null;
            }
            float t = -c / b;
            return t > 0f ? t : null;
        }

        float discriminant = b * b - 4f * a * c;
        if (discriminant < 0f) {
            return null; // target can't be caught in any straight line at this projectile speed
        }
        float sqrtDiscriminant = (float) Math.sqrt(discriminant);
        float t1 = (-b - sqrtDiscriminant) / (2f * a);
        float t2 = (-b + sqrtDiscriminant) / (2f * a);

        Float best = null;
        if (t1 > 0f) {
            best = t1;
        }
        if (t2 > 0f && (best == null || t2 < best)) {
            best = t2;
        }
        return best;
    }

    private static float angleOfDirection(float dx, float dy) {
        if (dx == 0f && dy == 0f) {
            return 0f; // degenerate (shooter and intercept point coincide) - direction is arbitrary
        }
        // Inverse of this project's angle-to-direction convention (Vector2(0,1).rotateRad(angle)
        // -> (-sin(angle), cos(angle))), see ShipControlSystem/WeaponSystem for the forward direction.
        return MathUtils.atan2(-dx, dy);
    }

    /**
     * Turns {@code currentAngle} toward {@code desiredAngle} by at most
     * {@code maxStep}, taking the shorter way around (e.g. turning from
     * 170° toward -170° steps by +20°, not -340°).
     *
     * @param currentAngle the current aim angle, in radians
     * @param desiredAngle the angle to turn toward, in radians
     * @param maxStep      the maximum angle to turn this call, in radians (non-negative)
     * @return the new aim angle, in radians, wrapped to {@code (-pi, pi]}
     */
    public static float rotateToward(float currentAngle, float desiredAngle, float maxStep) {
        float diff = angularDifference(desiredAngle, currentAngle);
        float clampedDiff = MathUtils.clamp(diff, -maxStep, maxStep);
        return wrapAngle(currentAngle + clampedDiff);
    }

    /**
     * Returns the shortest signed angular difference {@code a - b}, wrapped
     * to {@code (-pi, pi]} — e.g. the difference between 170° and -170° is
     * -20°, not 340°.
     *
     * @param a the first angle, in radians
     * @param b the second angle, in radians
     * @return the wrapped difference, in radians
     */
    public static float angularDifference(float a, float b) {
        return wrapAngle(a - b);
    }

    private static float wrapAngle(float angle) {
        angle %= MathUtils.PI2;
        if (angle > MathUtils.PI) {
            angle -= MathUtils.PI2;
        } else if (angle <= -MathUtils.PI) {
            angle += MathUtils.PI2;
        }
        return angle;
    }
}
