package de.mkoehler.starwars.sim;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure picking logic for a newly-spawned asteroid's texture/hitbox type and
 * initial motion (design.md — asteroids' user spec). Deliberately excludes
 * <em>where</em> to spawn — that's {@link SpawnPointFinder}'s job, already
 * generic over what "too close" means — this class only covers what's
 * unique to asteroids. Pure and deterministic given a seeded {@link Random}
 * (no Box2D/Ashley dependency), same "logic-heavy component gets tests"
 * convention as {@link SpawnPointFinder}.
 */
public final class AsteroidSpawner {

    /** How many asteroids should be active in the arena at once — the user's own spec. */
    public static final int ACTIVE_COUNT = 4;

    /**
     * Initial speed range (design.md — asteroids: "slow and hulking, not
     * speeding like a pebble") — well below typical ship speeds. Untuned
     * starting point.
     */
    public static final float MIN_SPEED_METERS_PER_SECOND = 3f;
    public static final float MAX_SPEED_METERS_PER_SECOND = 8f;

    /**
     * Initial rotation-speed range (design.md — asteroids: "a random slow
     * initial rotation") — roughly one full turn every ~20-60 seconds at
     * the low end. Untuned starting point.
     */
    public static final float MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND = 0.3f;

    private AsteroidSpawner() {
    }

    /**
     * Picks which texture/hitbox type a new asteroid should use, preferring
     * one not already among the currently-active asteroids (design.md — "the
     * 4 active ones never use the same texture, so all look different from
     * each other"). Falls back to picking from every type if every one is
     * somehow already active — not expected in practice ({@link #ACTIVE_COUNT}
     * is well below {@link AsteroidType#values()}'s length), but a defensive
     * fallback rather than an empty pool crashing {@link Random#nextInt}.
     *
     * @param activeTypes the types currently in use by other active asteroids
     * @param random      the source of randomness — pass a seeded {@link Random} for deterministic tests
     * @return the picked type
     */
    public static AsteroidType pickType(Set<AsteroidType> activeTypes, Random random) {
        List<AsteroidType> available = new ArrayList<>();
        for (AsteroidType type : AsteroidType.values()) {
            if (!activeTypes.contains(type)) {
                available.add(type);
            }
        }
        List<AsteroidType> pool = available.isEmpty() ? List.of(AsteroidType.values()) : available;
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Picks a random initial velocity within {@link #MIN_SPEED_METERS_PER_SECOND}/
     * {@link #MAX_SPEED_METERS_PER_SECOND}, in a uniformly random direction.
     *
     * @param random the source of randomness — pass a seeded {@link Random} for deterministic tests
     * @return the picked velocity, in meters/second
     */
    public static Vector2 pickVelocity(Random random) {
        float speed = MIN_SPEED_METERS_PER_SECOND
            + random.nextFloat() * (MAX_SPEED_METERS_PER_SECOND - MIN_SPEED_METERS_PER_SECOND);
        float angleRadians = random.nextFloat() * MathUtils.PI2;
        return new Vector2(speed, 0f).setAngleRad(angleRadians);
    }

    /**
     * Picks a random initial rotation speed within
     * {@code [-MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND, MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND]}.
     *
     * @param random the source of randomness — pass a seeded {@link Random} for deterministic tests
     * @return the picked angular velocity, in radians/second
     */
    public static float pickAngularVelocity(Random random) {
        return (random.nextFloat() * 2f - 1f) * MAX_ANGULAR_VELOCITY_RADIANS_PER_SECOND;
    }
}
