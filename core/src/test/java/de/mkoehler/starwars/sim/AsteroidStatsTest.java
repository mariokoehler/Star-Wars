package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies every {@link AsteroidType}'s {@code asteroids/<name>.meta.json}
 * actually resolves from the classpath and has a usable hitbox — the one
 * thing this project's own splash-screen boot check ({@code GameAssets})
 * doesn't exercise for asteroids (unlike ships, the client never calls
 * {@link AsteroidStats#forType}, only the server does — see
 * {@link AsteroidFactory}). Without this, a missing/malformed
 * {@code .meta.json} would only surface as an {@link IllegalStateException}
 * on the dedicated server's first tick.
 */
class AsteroidStatsTest {

    @Test
    void everyAsteroidTypeHasAUsableHitboxPolygon() {
        for (AsteroidType type : AsteroidType.values()) {
            int pointCount = AsteroidStats.forType(type).getSpriteMetadata().getHitboxPolygon().size();
            assertTrue(pointCount >= 3,
                type + " has only " + pointCount + " hitbox points, needs at least 3");
        }
    }
}
