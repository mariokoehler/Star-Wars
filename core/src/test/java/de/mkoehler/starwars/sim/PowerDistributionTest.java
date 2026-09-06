package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies {@link PowerDistribution}'s clamping algorithm (design.md 2.2),
 * including the normal/redirect/no-op cases and the ~78.3% practical
 * ceiling design.md calls out for repeated presses of the same system from
 * the even baseline.
 */
class PowerDistributionTest {

    private static final float TOLERANCE = 1e-4f;

    @Test
    void evenBaselineSplitsPowerEqually() {
        PowerDistribution distribution = PowerDistribution.even();

        assertEquals(1f / 3f, distribution.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(1f / 3f, distribution.getFraction(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(1f / 3f, distribution.getFraction(PowerSystem.ENGINES), TOLERANCE);
        assertEquals(1f, distribution.multiplierFor(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void normalCaseShiftsFivePercentFromEachOtherSystem() {
        PowerDistribution distribution = PowerDistribution.even().adjust(PowerSystem.ENGINES);

        assertEquals(1f / 3f + 0.05f, distribution.getFraction(PowerSystem.ENGINES), TOLERANCE);
        assertEquals(1f / 3f - 0.025f, distribution.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(1f / 3f - 0.025f, distribution.getFraction(PowerSystem.WEAPONS), TOLERANCE);
    }

    @Test
    void repeatedPressesOfTheSameSystemReachThePracticalCeilingAroundSeventyEightPercent() {
        PowerDistribution distribution = PowerDistribution.even();
        for (int i = 0; i < 9; i++) {
            distribution = distribution.adjust(PowerSystem.ENGINES);
        }

        // Both losing systems reach the floor together at the 9th press - design.md's "~78.3%".
        assertEquals(0.78333f, distribution.getFraction(PowerSystem.ENGINES), 1e-3f);
        assertEquals(0.10833f, distribution.getFraction(PowerSystem.SHIELDS), 1e-3f);
        assertEquals(0.10833f, distribution.getFraction(PowerSystem.WEAPONS), 1e-3f);
    }

    @Test
    void tenthConsecutivePressIsANoOpOnceBothOtherSystemsAreAtTheFloor() {
        PowerDistribution distribution = PowerDistribution.even();
        for (int i = 0; i < 9; i++) {
            distribution = distribution.adjust(PowerSystem.ENGINES);
        }

        PowerDistribution unchanged = distribution.adjust(PowerSystem.ENGINES);

        assertSame(distribution, unchanged);
    }

    @Test
    void redirectCaseTakesTheFullShiftFromTheOnlySystemThatCanAffordIt() {
        // Shields already at the floor from some earlier, different sequence of presses;
        // Weapons still has plenty of room.
        PowerDistribution distribution = PowerDistribution.even()
            .adjust(PowerSystem.WEAPONS) // Shields/Engines both lose 2.5%, still well above floor
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS)
            .adjust(PowerSystem.WEAPONS); // Shields/Engines now at the floor (~10.83%), Weapons ~78.3%

        // Push power back toward Engines: the normal split would drop Shields below the floor
        // (already at it) but Weapons has plenty of room to absorb the full 5%.
        PowerDistribution redirected = distribution.adjust(PowerSystem.ENGINES);

        assertEquals(distribution.getFraction(PowerSystem.SHIELDS), redirected.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(distribution.getFraction(PowerSystem.WEAPONS) - 0.05f, redirected.getFraction(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(distribution.getFraction(PowerSystem.ENGINES) + 0.05f, redirected.getFraction(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void resetReturnsToTheEvenBaseline() {
        PowerDistribution distribution = PowerDistribution.even().adjust(PowerSystem.SHIELDS).adjust(PowerSystem.SHIELDS);

        PowerDistribution reset = distribution.reset();

        assertEquals(1f / 3f, reset.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(1f / 3f, reset.getFraction(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(1f / 3f, reset.getFraction(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void maximizeJumpsStraightToTheTheoreticalMaximumRegardlessOfCurrentSplit() {
        PowerDistribution distribution = PowerDistribution.even().maximize(PowerSystem.ENGINES);

        assertEquals(0.80f, distribution.getFraction(PowerSystem.ENGINES), TOLERANCE);
        assertEquals(0.10f, distribution.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(0.10f, distribution.getFraction(PowerSystem.WEAPONS), TOLERANCE);
    }

    @Test
    void maximizeAlwaysSucceedsEvenFromAnAlreadyLopsidedSplit() {
        PowerDistribution lopsided = PowerDistribution.even().maximize(PowerSystem.SHIELDS);

        PowerDistribution distribution = lopsided.maximize(PowerSystem.WEAPONS);

        assertEquals(0.80f, distribution.getFraction(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(0.10f, distribution.getFraction(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(0.10f, distribution.getFraction(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void multiplierScalesLinearlyWithFraction() {
        PowerDistribution distribution = PowerDistribution.even().adjust(PowerSystem.ENGINES);

        float expectedMultiplier = distribution.getFraction(PowerSystem.ENGINES) / PowerDistribution.BASELINE_FRACTION;
        assertEquals(expectedMultiplier, distribution.multiplierFor(PowerSystem.ENGINES), TOLERANCE);
    }
}
