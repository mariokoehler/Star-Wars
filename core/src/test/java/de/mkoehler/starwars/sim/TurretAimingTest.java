package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TurretAiming}'s lead-intercept calculation and
 * rotate-toward-with-max-step logic.
 */
class TurretAimingTest {

    private static final float TOLERANCE = 1e-3f;

    @Test
    void aimsDirectlyAtAStationaryTarget() {
        // Target due north of the shooter, not moving - lead angle should be exactly "north" (0).
        float angle = TurretAiming.computeLeadAngle(0f, 0f, 0f, 10f, 0f, 0f, 50f);
        assertEquals(0f, angle, TOLERANCE);
    }

    @Test
    void aimsDirectlyAtAStationaryTargetToTheEast() {
        // Target due east (+X) of the shooter - this project's angle convention puts "east" at -90°.
        float angle = TurretAiming.computeLeadAngle(0f, 0f, 10f, 0f, 0f, 0f, 50f);
        assertEquals(-MathUtils.HALF_PI, angle, TOLERANCE);
    }

    @Test
    void leadsATargetMovingPerpendicularToTheLineOfFire() {
        // Target starts due north, moving east at 10 m/s; projectile travels at 50 m/s.
        // Solving |  (0,10) + (10,0)*t  | = 50*t exactly: 100 + 200t + 100t^2 + 100t^2 ... solve numerically below,
        // but the key structural check is simpler: the computed angle must aim *ahead* of the target's
        // current position (a positive lead to the east, i.e. angle between "north" and "east").
        float angle = TurretAiming.computeLeadAngle(0f, 0f, 0f, 10f, 10f, 0f, 50f);
        assertTrue(angle < 0f && angle > -MathUtils.HALF_PI,
            "expected a lead angle strictly between north (0) and east (-HALF_PI), was " + angle);
    }

    @Test
    void leadAngleProducesAGenuineInterceptForAPerpendicularTarget() {
        // Same setup as above, but verify the actual intercept geometry: flying at the computed
        // angle for the solved intercept time should land on the target's future position.
        float shooterX = 0f;
        float shooterY = 0f;
        float targetX = 0f;
        float targetY = 10f;
        float targetVelX = 10f;
        float targetVelY = 0f;
        float projectileSpeed = 50f;

        float angle = TurretAiming.computeLeadAngle(shooterX, shooterY, targetX, targetY, targetVelX, targetVelY, projectileSpeed);

        // Reconstruct the intercept time from first principles (quadratic, same as production code)
        // to cross-check, then confirm flying at `angle` for that long reaches the target's position then.
        float relX = targetX - shooterX;
        float relY = targetY - shooterY;
        float a = targetVelX * targetVelX + targetVelY * targetVelY - projectileSpeed * projectileSpeed;
        float b = 2f * (relX * targetVelX + relY * targetVelY);
        float c = relX * relX + relY * relY;
        float discriminant = b * b - 4f * a * c;
        float t = (-b - (float) Math.sqrt(discriminant)) / (2f * a);

        float projectileX = shooterX + (-MathUtils.sin(angle)) * projectileSpeed * t;
        float projectileY = shooterY + MathUtils.cos(angle) * projectileSpeed * t;
        float futureTargetX = targetX + targetVelX * t;
        float futureTargetY = targetY + targetVelY * t;

        assertEquals(futureTargetX, projectileX, 0.05f);
        assertEquals(futureTargetY, projectileY, 0.05f);
    }

    @Test
    void fallsBackToDirectAimWhenNoInterceptIsPossible() {
        // Target fleeing directly away faster than the projectile can ever catch up.
        float angle = TurretAiming.computeLeadAngle(0f, 0f, 0f, 10f, 0f, 1000f, 50f);
        assertEquals(0f, angle, TOLERANCE); // straight north, same as the target's current position
    }

    @Test
    void rotateTowardSnapsWhenWithinMaxStep() {
        float result = TurretAiming.rotateToward(0f, 0.1f, 0.5f);
        assertEquals(0.1f, result, TOLERANCE);
    }

    @Test
    void rotateTowardClampsWhenBeyondMaxStep() {
        float result = TurretAiming.rotateToward(0f, MathUtils.HALF_PI, 0.1f);
        assertEquals(0.1f, result, TOLERANCE);
    }

    @Test
    void rotateTowardTakesTheShorterWayAroundTheWrap() {
        // From just past +170 degrees toward just past -170 degrees: shorter way is forward
        // (through 180), not backward through 0.
        float from = MathUtils.degreesToRadians * 170f;
        float toward = MathUtils.degreesToRadians * -170f;
        float result = TurretAiming.rotateToward(from, toward, MathUtils.degreesToRadians * 5f);
        assertEquals(MathUtils.degreesToRadians * 175f, result, TOLERANCE);
    }

    @Test
    void angularDifferenceWrapsToShortestPath() {
        float from = MathUtils.degreesToRadians * -170f;
        float to = MathUtils.degreesToRadians * 170f;
        float diff = TurretAiming.angularDifference(to, from);
        assertEquals(MathUtils.degreesToRadians * -20f, diff, TOLERANCE);
    }
}
