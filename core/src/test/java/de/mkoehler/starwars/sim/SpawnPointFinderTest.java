package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SpawnPointFinder}'s two constraints (boundary margin,
 * minimum enemy distance) and its best-effort fallback for an unusually
 * crowded arena.
 */
class SpawnPointFinderTest {

    private static final float ARENA_HALF_SIZE = ArenaBounds.HALF_SIZE_METERS;
    private static final float BOUNDARY_MARGIN = SpawnPointFinder.BOUNDARY_MARGIN_METERS;
    private static final float MIN_ENEMY_DISTANCE = SpawnPointFinder.MIN_ENEMY_DISTANCE_METERS;

    @Test
    void withNoEnemiesStaysWithinTheBoundaryMargin() {
        Vector2 point = SpawnPointFinder.findSpawnPoint(ARENA_HALF_SIZE, BOUNDARY_MARGIN, MIN_ENEMY_DISTANCE,
            List.of(), new Random(1));

        assertWithinBoundaryMargin(point);
    }

    @Test
    void staysAtLeastTheMinimumDistanceFromEveryEnemy() {
        List<Vector2> enemies = List.of(new Vector2(0f, 0f), new Vector2(150f, -100f));

        Vector2 point = SpawnPointFinder.findSpawnPoint(ARENA_HALF_SIZE, BOUNDARY_MARGIN, MIN_ENEMY_DISTANCE,
            enemies, new Random(42));

        assertWithinBoundaryMargin(point);
        for (Vector2 enemy : enemies) {
            assertTrue(point.dst(enemy) >= MIN_ENEMY_DISTANCE,
                "expected " + point + " to be at least " + MIN_ENEMY_DISTANCE + "m from " + enemy);
        }
    }

    @Test
    void isDeterministicForTheSameSeed() {
        List<Vector2> enemies = List.of(new Vector2(-80f, 40f));

        Vector2 first = SpawnPointFinder.findSpawnPoint(ARENA_HALF_SIZE, BOUNDARY_MARGIN, MIN_ENEMY_DISTANCE,
            enemies, new Random(7));
        Vector2 second = SpawnPointFinder.findSpawnPoint(ARENA_HALF_SIZE, BOUNDARY_MARGIN, MIN_ENEMY_DISTANCE,
            enemies, new Random(7));

        assertEquals(first, second);
    }

    @Test
    void fallsBackToARealPointWhenNoCandidateCanSatisfyTheMinimumDistance() {
        // Enemies packed across the whole usable range - no single point can be
        // MIN_ENEMY_DISTANCE from all five of these within a 460x460 usable area, so this
        // exercises the best-effort fallback rather than the early-return success path.
        List<Vector2> enemies = List.of(
            new Vector2(-200f, -200f), new Vector2(200f, -200f),
            new Vector2(-200f, 200f), new Vector2(200f, 200f),
            new Vector2(0f, 0f));

        Vector2 point = SpawnPointFinder.findSpawnPoint(ARENA_HALF_SIZE, BOUNDARY_MARGIN, MIN_ENEMY_DISTANCE,
            enemies, new Random(3));

        assertWithinBoundaryMargin(point);
    }

    private static void assertWithinBoundaryMargin(Vector2 point) {
        float range = ARENA_HALF_SIZE - BOUNDARY_MARGIN;
        assertTrue(Math.abs(point.x) <= range, "x=" + point.x + " outside [-" + range + "," + range + "]");
        assertTrue(Math.abs(point.y) <= range, "y=" + point.y + " outside [-" + range + "," + range + "]");
    }
}
