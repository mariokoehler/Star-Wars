package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ArenaBounds#wallImpactDamage(float)}'s threshold/linear
 * shape: harmless below the threshold, scaling linearly above it.
 */
class ArenaBoundsTest {

    @Test
    void aSlowBounceDealsNoDamage() {
        assertEquals(0f, ArenaBounds.wallImpactDamage(5f));
        assertEquals(0f, ArenaBounds.wallImpactDamage(0f));
    }

    @Test
    void exactlyAtTheThresholdDealsNoDamage() {
        assertEquals(0f, ArenaBounds.wallImpactDamage(20f));
    }

    @Test
    void aFastImpactScalesLinearlyAboveTheThreshold() {
        float damageAt40 = ArenaBounds.wallImpactDamage(40f);
        float damageAt80 = ArenaBounds.wallImpactDamage(80f);

        assertTrue(damageAt40 > 0f);
        // Twice as far above the threshold (60 vs 20) should deal exactly 3x the damage.
        assertEquals(3f * damageAt40, damageAt80, 0.001f);
    }

    @Test
    void neverReturnsNegativeDamage() {
        assertTrue(ArenaBounds.wallImpactDamage(-50f) >= 0f);
    }
}
