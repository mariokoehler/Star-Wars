package de.mkoehler.starwars.sim;

/**
 * Pure "closest live enemy" targeting logic, shared by every system that
 * needs to pick or re-validate an enemy target: {@code TurretSystem}
 * (range-only), {@code MissileLockSystem} (cone-restricted, via
 * {@link RadarDetection#isWithinCone}), and {@code NpcBrainSystem}
 * (range-only, generous range — design.md, NPC ships). Extracted once a
 * third consumer needed the same "closest candidate, excluding my own,
 * admitted by some spatial test" shape that the first two had already
 * duplicated.
 * <p>
 * Deliberately framework-free (no Ashley {@code Entity}/{@code
 * ComponentMapper}, no Box2D {@code Body}) so it's directly unit-testable
 * with plain test values, matching this project's convention of keeping
 * logic-heavy pure functions separate from system wiring (see
 * {@link TurretAiming}). Callers extract a candidate's owning player id and
 * position via the given functional extractors.
 *
 * @param <T> the candidate type (in practice, an Ashley {@code Entity})
 */
public final class TargetFinder {

    private TargetFinder() {
    }

    /**
     * Extracts a {@code float} value from a candidate — used instead of
     * {@code java.util.function.ToDoubleFunction} to avoid a
     * double/float round trip at every call.
     *
     * @param <T> the candidate type
     */
    @FunctionalInterface
    public interface FloatExtractor<T> {
        float apply(T candidate);
    }

    /**
     * Extracts an {@code int} value (a candidate's owning player id) from a
     * candidate.
     *
     * @param <T> the candidate type
     */
    @FunctionalInterface
    public interface IntExtractor<T> {
        int apply(T candidate);
    }

    /**
     * A spatial admissibility test for a position, used to express "within
     * scan range" or "within radar cone" without {@link #findClosest}
     * needing to know which.
     */
    @FunctionalInterface
    public interface SpatialFilter {
        boolean test(float x, float y);
    }

    /**
     * Builds a {@link SpatialFilter} admitting any position within {@code
     * rangeMeters} of {@code (fromX, fromY)}, inclusive.
     *
     * @param fromX      the origin X position, in meters
     * @param fromY      the origin Y position, in meters
     * @param rangeMeters the admissible range, in meters
     * @return the filter
     */
    public static SpatialFilter withinRange(float fromX, float fromY, float rangeMeters) {
        float rangeSq = rangeMeters * rangeMeters;
        return (x, y) -> {
            float dx = x - fromX;
            float dy = y - fromY;
            return dx * dx + dy * dy <= rangeSq;
        };
    }

    /**
     * Returns whether {@code target} is still a valid target: present
     * (identity-compared) among {@code liveCandidates} and admitted by
     * {@code filter} at its current position.
     *
     * @param target         the target to re-validate, previously returned by {@link #findClosest}
     * @param liveCandidates every currently-alive candidate
     * @param xOf            extracts a candidate's current X position
     * @param yOf            extracts a candidate's current Y position
     * @param filter         the spatial admissibility test (e.g. {@link #withinRange}, or a radar cone check)
     * @param <T>            the candidate type
     * @return {@code true} if {@code target} is still valid
     */
    public static <T> boolean isStillValid(T target, Iterable<T> liveCandidates, FloatExtractor<T> xOf,
                                            FloatExtractor<T> yOf, SpatialFilter filter) {
        boolean alive = false;
        for (T candidate : liveCandidates) {
            if (candidate == target) {
                alive = true;
                break;
            }
        }
        if (!alive) {
            return false;
        }
        return filter.test(xOf.apply(target), yOf.apply(target));
    }

    /**
     * Finds the closest candidate that isn't {@code excludedOwnerPlayerId}'s
     * own ship and is admitted by {@code filter}, ranked by distance from
     * {@code (fromX, fromY)}.
     *
     * @param candidates            every currently-alive candidate to consider
     * @param ownerPlayerIdOf       extracts a candidate's owning player id
     * @param xOf                   extracts a candidate's current X position
     * @param yOf                   extracts a candidate's current Y position
     * @param excludedOwnerPlayerId the searching ship's own player id, never returned
     * @param fromX                 the search origin's X position, in meters
     * @param fromY                 the search origin's Y position, in meters
     * @param filter                the spatial admissibility test (e.g. {@link #withinRange}, or a radar cone check)
     * @param <T>                   the candidate type
     * @return the closest admissible candidate, or {@code null} if none qualify
     */
    public static <T> T findClosest(Iterable<T> candidates, IntExtractor<T> ownerPlayerIdOf,
                                     FloatExtractor<T> xOf, FloatExtractor<T> yOf,
                                     int excludedOwnerPlayerId, float fromX, float fromY, SpatialFilter filter) {
        T closest = null;
        float closestDistanceSq = Float.MAX_VALUE;
        for (T candidate : candidates) {
            if (ownerPlayerIdOf.apply(candidate) == excludedOwnerPlayerId) {
                continue;
            }
            float x = xOf.apply(candidate);
            float y = yOf.apply(candidate);
            if (!filter.test(x, y)) {
                continue;
            }
            float dx = x - fromX;
            float dy = y - fromY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < closestDistanceSq) {
                closest = candidate;
                closestDistanceSq = distanceSq;
            }
        }
        return closest;
    }
}
