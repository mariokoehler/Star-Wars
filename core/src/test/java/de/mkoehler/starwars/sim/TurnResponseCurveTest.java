package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TurnResponseCurve}'s power-law behavior (design.md 2.2's
 * addendum), using the actual power-distribution multiplier range this
 * project's {@code PowerDistribution} produces: ~0.30 at the floor, 1.0 at
 * baseline, ~2.40 maxed.
 */
class TurnResponseCurveTest {

    private static final float TOLERANCE = 1e-3f;

    @Test
    void exponentOfOneReproducesTheLinearMultiplierUnchanged() {
        assertEquals(0.30f, TurnResponseCurve.apply(0.30f, 1.0f), TOLERANCE);
        assertEquals(1.0f, TurnResponseCurve.apply(1.0f, 1.0f), TOLERANCE);
        assertEquals(2.40f, TurnResponseCurve.apply(2.40f, 1.0f), TOLERANCE);
    }

    @Test
    void alwaysEvaluatesToOneAtBaselineRegardlessOfExponent() {
        assertEquals(1.0f, TurnResponseCurve.apply(1.0f, 0.5f), TOLERANCE);
        assertEquals(1.0f, TurnResponseCurve.apply(1.0f, 0.1f), TOLERANCE);
        assertEquals(1.0f, TurnResponseCurve.apply(1.0f, 2.0f), TOLERANCE);
    }

    @Test
    void anExponentBelowOneCompressesTheMaxedOutMultiplier() {
        // The Snowspeeder's own worked example (design.md 2.2's addendum): 2.40^0.5 = 1.549.
        float curved = TurnResponseCurve.apply(2.40f, 0.5f);
        assertEquals(1.549f, curved, TOLERANCE);
        assertTrue(curved < 2.40f, "curved multiplier should be smaller than the linear one above baseline");
    }

    @Test
    void anExponentBelowOneSoftensTheFloorMultiplier() {
        // 0.30^0.5 = 0.548 - larger than the linear 0.30, i.e. less of a penalty at the floor,
        // the direct consequence of a power-law curve fixing at exactly 1.0 at baseline.
        float curved = TurnResponseCurve.apply(0.30f, 0.5f);
        assertEquals(0.548f, curved, TOLERANCE);
        assertTrue(curved > 0.30f, "curved multiplier should be larger than the linear one below baseline");
    }

    @Test
    void aSmallerExponentCompressesMoreAggressively() {
        float lessCompressed = TurnResponseCurve.apply(2.40f, 0.7f);
        float moreCompressed = TurnResponseCurve.apply(2.40f, 0.3f);
        assertTrue(moreCompressed < lessCompressed,
            "a smaller exponent should pull the maxed-out multiplier closer to 1.0");
    }
}
