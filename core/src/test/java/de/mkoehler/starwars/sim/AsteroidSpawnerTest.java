package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link AsteroidSpawner}'s pure picking logic: texture-distinctness
 * (design.md — asteroids: "the 4 active ones never use the same texture"),
 * and that initial velocity/angular velocity always stay within their
 * configured ranges.
 */
class AsteroidSpawnerTest {

    @Test
    void neverPicksAnAlreadyActiveTypeWhenAnAlternativeExists() {
        Set<AsteroidType> active = EnumSet.of(AsteroidType.ASTEROID, AsteroidType.ASTEROID2,
            AsteroidType.ASTEROID3, AsteroidType.ASTEROID4);
        Random random = new Random(1);

        for (int i = 0; i < 200; i++) {
            AsteroidType picked = AsteroidSpawner.pickType(active, random);
            assertFalse(active.contains(picked), "picked an already-active type: " + picked);
        }
    }

    @Test
    void fallsBackToAnyTypeWhenEveryTypeIsAlreadyActive() {
        Set<AsteroidType> allTypes = EnumSet.allOf(AsteroidType.class);
        AsteroidType picked = AsteroidSpawner.pickType(allTypes, new Random(2));
        assertTrue(allTypes.contains(picked));
    }

    @Test
    void velocityMagnitudeStaysWithinTheConfiguredRange() {
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            Vector2 velocity = AsteroidSpawner.pickVelocity(random);
            float speed = velocity.len();
            assertTrue(speed >= AsteroidSpawner.MIN_SPEED_METERS_PER_SECOND - 1e-4f,
                "speed " + speed + " below minimum");
            assertTrue(speed <= AsteroidSpawner.MAX_SPEED_METERS_PER_SECOND + 1e-4f,
                "speed " + speed + " above maximum");
        }
    }

    @Test
    void angularVelocityStaysWithinTheConfiguredRange() {
        Random random = new Random(4);
        for (int i = 0; i < 200; i++) {
            float angularVelocity = AsteroidSpawner.pickAngularVelocity(random);
            assertTrue(Math.abs(angularVelocity) <= AsteroidSpawner.MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND,
                "angular velocity " + angularVelocity + " outside range");
        }
    }

    @Test
    void isDeterministicForTheSameSeed() {
        Set<AsteroidType> active = EnumSet.of(AsteroidType.ASTEROID);
        AsteroidType firstType = AsteroidSpawner.pickType(active, new Random(9));
        AsteroidType secondType = AsteroidSpawner.pickType(active, new Random(9));
        assertEquals(firstType, secondType);

        Vector2 firstVelocity = AsteroidSpawner.pickVelocity(new Random(9));
        Vector2 secondVelocity = AsteroidSpawner.pickVelocity(new Random(9));
        assertEquals(firstVelocity, secondVelocity);
    }
}
