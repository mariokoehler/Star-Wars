package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.Vector2;

import java.util.List;
import java.util.Random;

/**
 * Picks a spawn/respawn point inside the arena (design.md — arena bounds):
 * a random point at least {@code boundaryMargin} inside the arena edge and
 * at least {@code minEnemyDistance} from every currently-alive enemy ship —
 * the user's own spec, chosen as the simple option for a first pass rather
 * than anything smarter (fixed spawn zones, spread-maximizing placement,
 * etc.).
 * <p>
 * Pure and deterministic given a seeded {@link Random} (no Box2D/Ashley
 * dependency), so it's directly unit-testable — same "logic-heavy
 * component gets tests" convention as {@link ShipDamage}/{@link PowerDistribution}.
 */
public final class SpawnPointFinder {

    /**
     * How far inside the arena edge a spawn point must stay (design.md —
     * arena bounds) — the user's own spec.
     */
    public static final float BOUNDARY_MARGIN_METERS = 20f;

    /**
     * How far a spawn point must stay from every other currently-alive ship
     * (design.md — arena bounds) — the user's own spec.
     */
    public static final float MIN_ENEMY_DISTANCE_METERS = 100f;

    /**
     * How many random candidates to try before giving up on satisfying
     * {@code minEnemyDistance} exactly and falling back to the least-bad
     * one tried — a match crowded enough to matter is the rare case, not
     * the common one, so this doesn't need to be large to be effective.
     */
    private static final int MAX_ATTEMPTS = 50;

    private SpawnPointFinder() {
    }

    /**
     * Finds a spawn point.
     *
     * @param arenaHalfSize    half the arena's width/height, in meters (see
     *                         {@link ArenaBounds#HALF_SIZE_METERS})
     * @param boundaryMargin   minimum distance the point must keep from the
     *                         arena edge
     * @param minEnemyDistance minimum distance the point must keep from
     *                         every position in {@code enemyPositions}
     * @param enemyPositions   every currently-alive enemy ship's position;
     *                         an empty list means any point in range works
     * @param random           the source of randomness — pass a seeded
     *                         {@link Random} for deterministic tests
     * @return a point satisfying both constraints if one was found within
     * {@value #MAX_ATTEMPTS} random attempts; otherwise the attempted point
     * that came closest (the largest minimum distance to any enemy) — a
     * best-effort fallback for an unusually crowded arena, not a failure
     */
    public static Vector2 findSpawnPoint(float arenaHalfSize, float boundaryMargin, float minEnemyDistance,
                                          List<Vector2> enemyPositions, Random random) {
        float range = arenaHalfSize - boundaryMargin;
        Vector2 best = new Vector2(0f, 0f);
        float bestMinDistance = -1f;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            float x = (random.nextFloat() * 2f - 1f) * range;
            float y = (random.nextFloat() * 2f - 1f) * range;
            float minDistanceToEnemy = minDistanceTo(x, y, enemyPositions);
            if (minDistanceToEnemy >= minEnemyDistance) {
                return new Vector2(x, y);
            }
            if (minDistanceToEnemy > bestMinDistance) {
                bestMinDistance = minDistanceToEnemy;
                best = new Vector2(x, y);
            }
        }
        return best;
    }

    /**
     * Returns whether {@code point} keeps at least {@code minEnemyDistance}
     * from every position in {@code enemyPositions} — lets a caller reject
     * {@link #findSpawnPoint}'s best-effort fallback outright rather than
     * accepting it, for a spawn (design.md — asteroids) where popping up
     * near a player at all is unacceptable and simply retrying later
     * (unlike a ship, which must spawn somewhere right now) is an option.
     *
     * @param point            the candidate point
     * @param minEnemyDistance minimum distance the point must keep from every position in {@code enemyPositions}
     * @param enemyPositions   every currently-alive enemy ship's position; an empty list always passes
     * @return {@code true} if {@code point} satisfies the distance constraint
     */
    public static boolean isFarEnoughFromEnemies(Vector2 point, float minEnemyDistance, List<Vector2> enemyPositions) {
        return minDistanceTo(point.x, point.y, enemyPositions) >= minEnemyDistance;
    }

    private static float minDistanceTo(float x, float y, List<Vector2> positions) {
        if (positions.isEmpty()) {
            return Float.MAX_VALUE;
        }
        float minDistanceSq = Float.MAX_VALUE;
        for (Vector2 position : positions) {
            float dx = x - position.x;
            float dy = y - position.y;
            minDistanceSq = Math.min(minDistanceSq, dx * dx + dy * dy);
        }
        return (float) Math.sqrt(minDistanceSq);
    }
}
