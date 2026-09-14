package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TargetFinder}'s closest-candidate selection and
 * re-validation logic using a trivial candidate value (no Ashley/Box2D
 * involved — see {@link TargetFinder}'s own Javadoc for why).
 */
class TargetFinderTest {

    private record Candidate(int ownerPlayerId, float x, float y) {
    }

    private static int ownerOf(Candidate c) {
        return c.ownerPlayerId();
    }

    private static float xOf(Candidate c) {
        return c.x();
    }

    private static float yOf(Candidate c) {
        return c.y();
    }

    @Test
    void findsTheClosestCandidateWithinRange() {
        Candidate near = new Candidate(2, 5f, 0f);
        Candidate far = new Candidate(3, 20f, 0f);
        List<Candidate> candidates = List.of(far, near);

        Candidate result = TargetFinder.findClosest(candidates, TargetFinderTest::ownerOf, TargetFinderTest::xOf,
            TargetFinderTest::yOf, 1, 0f, 0f, TargetFinder.withinRange(0f, 0f, 100f));

        assertSame(near, result);
    }

    @Test
    void excludesTheSearchersOwnCandidate() {
        Candidate own = new Candidate(1, 1f, 0f);
        Candidate enemy = new Candidate(2, 10f, 0f);
        List<Candidate> candidates = List.of(own, enemy);

        Candidate result = TargetFinder.findClosest(candidates, TargetFinderTest::ownerOf, TargetFinderTest::xOf,
            TargetFinderTest::yOf, 1, 0f, 0f, TargetFinder.withinRange(0f, 0f, 100f));

        assertSame(enemy, result);
    }

    @Test
    void returnsNullWhenNothingIsWithinRange() {
        Candidate farAway = new Candidate(2, 1000f, 0f);
        List<Candidate> candidates = List.of(farAway);

        Candidate result = TargetFinder.findClosest(candidates, TargetFinderTest::ownerOf, TargetFinderTest::xOf,
            TargetFinderTest::yOf, 1, 0f, 0f, TargetFinder.withinRange(0f, 0f, 10f));

        assertNull(result);
    }

    @Test
    void returnsNullWhenOnlyCandidateIsOwnShip() {
        Candidate own = new Candidate(1, 0f, 0f);
        List<Candidate> candidates = List.of(own);

        Candidate result = TargetFinder.findClosest(candidates, TargetFinderTest::ownerOf, TargetFinderTest::xOf,
            TargetFinderTest::yOf, 1, 0f, 0f, TargetFinder.withinRange(0f, 0f, 100f));

        assertNull(result);
    }

    @Test
    void withinRangeFilterAdmitsExactlyTheBoundary() {
        TargetFinder.SpatialFilter filter = TargetFinder.withinRange(0f, 0f, 10f);
        assertTrue(filter.test(10f, 0f));
        assertFalse(filter.test(10.001f, 0f));
    }

    @Test
    void isStillValidTrueForALiveInRangeCandidate() {
        Candidate target = new Candidate(2, 5f, 0f);
        List<Candidate> liveCandidates = List.of(target);

        boolean valid = TargetFinder.isStillValid(target, liveCandidates, TargetFinderTest::xOf, TargetFinderTest::yOf,
            TargetFinder.withinRange(0f, 0f, 100f));

        assertTrue(valid);
    }

    @Test
    void isStillValidFalseWhenNoLongerAmongLiveCandidates() {
        Candidate target = new Candidate(2, 5f, 0f);
        List<Candidate> liveCandidates = List.of(); // target has died/despawned

        boolean valid = TargetFinder.isStillValid(target, liveCandidates, TargetFinderTest::xOf, TargetFinderTest::yOf,
            TargetFinder.withinRange(0f, 0f, 100f));

        assertFalse(valid);
    }

    @Test
    void isStillValidFalseWhenOutsideTheFilter() {
        Candidate target = new Candidate(2, 500f, 0f);
        List<Candidate> liveCandidates = List.of(target);

        boolean valid = TargetFinder.isStillValid(target, liveCandidates, TargetFinderTest::xOf, TargetFinderTest::yOf,
            TargetFinder.withinRange(0f, 0f, 10f));

        assertFalse(valid);
    }

    @Test
    void isStillValidUsesIdentityNotEquality() {
        // Same field values, but a different instance - must not be treated as "the same candidate."
        Candidate original = new Candidate(2, 5f, 0f);
        Candidate lookalike = new Candidate(2, 5f, 0f);
        List<Candidate> liveCandidates = List.of(lookalike);

        boolean valid = TargetFinder.isStillValid(original, liveCandidates, TargetFinderTest::xOf, TargetFinderTest::yOf,
            TargetFinder.withinRange(0f, 0f, 100f));

        assertFalse(valid);
    }

    @Test
    void findClosestBreaksTiesByFirstEncountered() {
        Candidate first = new Candidate(2, 5f, 0f);
        Candidate secondAtSameDistance = new Candidate(3, 0f, 5f);
        List<Candidate> candidates = List.of(first, secondAtSameDistance);

        Candidate result = TargetFinder.findClosest(candidates, TargetFinderTest::ownerOf, TargetFinderTest::xOf,
            TargetFinderTest::yOf, 1, 0f, 0f, TargetFinder.withinRange(0f, 0f, 100f));

        assertEquals(first, result);
    }
}
