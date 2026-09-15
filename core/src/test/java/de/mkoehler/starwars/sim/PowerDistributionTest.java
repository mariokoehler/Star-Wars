package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void effectiveFractionsWithEveryoneDemandingMatchesRawPriority() {
        PowerDistribution distribution = PowerDistribution.even().adjust(PowerSystem.ENGINES);

        Map<PowerSystem, Float> effective = distribution.effectiveFractions(EnumSet.allOf(PowerSystem.class));

        assertEquals(distribution.getFraction(PowerSystem.SHIELDS), effective.get(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(distribution.getFraction(PowerSystem.WEAPONS), effective.get(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(distribution.getFraction(PowerSystem.ENGINES), effective.get(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void effectiveFractionsWithNobodyDemandingIsAllZero() {
        Map<PowerSystem, Float> effective = PowerDistribution.even().effectiveFractions(EnumSet.noneOf(PowerSystem.class));

        assertEquals(0f, effective.get(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(0f, effective.get(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(0f, effective.get(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void effectiveFractionsGivesIdleSystemsShareToTheSoleDemanderUpToTheCap() {
        // Even baseline (1/3 each): Weapons alone demanding, at baseline priority, would
        // renormalize to a full 1.0 without a cap - clamped to REDISTRIBUTION_CAP_FRACTION instead
        // (design.md 2.2 - never more than committing to it outright via maximize() would give).
        Map<PowerSystem, Float> effective = PowerDistribution.even().effectiveFractions(EnumSet.of(PowerSystem.WEAPONS));

        assertEquals(0f, effective.get(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(0f, effective.get(PowerSystem.ENGINES), TOLERANCE);
        assertEquals(PowerDistribution.REDISTRIBUTION_CAP_FRACTION, effective.get(PowerSystem.WEAPONS), TOLERANCE);
    }

    @Test
    void effectiveFractionsRenormalizesProportionallyAmongTwoDemandingSystems() {
        // Shields idle; Engines/Weapons both demanding, still at even baseline priority - their
        // 1/3 each renormalizes to an even split of the whole pool between just the two of them.
        Map<PowerSystem, Float> effective = PowerDistribution.even()
            .effectiveFractions(EnumSet.of(PowerSystem.ENGINES, PowerSystem.WEAPONS));

        assertEquals(0f, effective.get(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(0.5f, effective.get(PowerSystem.ENGINES), TOLERANCE);
        assertEquals(0.5f, effective.get(PowerSystem.WEAPONS), TOLERANCE);
    }

    @Test
    void effectiveFractionsWaterFillsCappedOverflowToTheOtherDemandingSystem() {
        // Weapons maximized (80%), Engines/Shields at the floor (10% each). Shields idle, so its
        // 10% is free; Engines and Weapons both demanding. Weapons' own 80% share already exceeds
        // the cap on its own, so it's clamped to REDISTRIBUTION_CAP_FRACTION (0.8) and the leftover
        // pool (0.2) goes entirely to Engines - not wasted, since Engines can still use it.
        PowerDistribution distribution = PowerDistribution.even().maximize(PowerSystem.WEAPONS);

        Map<PowerSystem, Float> effective = distribution.effectiveFractions(EnumSet.of(PowerSystem.ENGINES, PowerSystem.WEAPONS));

        assertEquals(0f, effective.get(PowerSystem.SHIELDS), TOLERANCE);
        assertEquals(PowerDistribution.REDISTRIBUTION_CAP_FRACTION, effective.get(PowerSystem.WEAPONS), TOLERANCE);
        assertEquals(1f - PowerDistribution.REDISTRIBUTION_CAP_FRACTION, effective.get(PowerSystem.ENGINES), TOLERANCE);
    }

    @Test
    void effectiveMultiplierIfDemandingMatchesGenuineDemandWhenAlreadyDemanding() {
        // Forcing an already-genuinely-demanding system into the set is a no-op (design.md 2.2's
        // Engines/Weapons onset-fix addendum) - must match effectiveFractions' own answer exactly.
        PowerDistribution distribution = PowerDistribution.even();
        EnumSet<PowerSystem> genuinelyDemanding = EnumSet.of(PowerSystem.ENGINES, PowerSystem.SHIELDS);

        float viaEffectiveFractions = distribution.effectiveFractions(genuinelyDemanding).get(PowerSystem.ENGINES)
            / PowerDistribution.BASELINE_FRACTION;
        float viaIfDemanding = distribution.effectiveMultiplierIfDemanding(PowerSystem.ENGINES,
            EnumSet.of(PowerSystem.SHIELDS));

        assertEquals(viaEffectiveFractions, viaIfDemanding, TOLERANCE);
    }

    @Test
    void effectiveMultiplierIfDemandingIsNonZeroEvenWhenGenuinelyIdle() {
        // Design.md 2.2's Engines/Weapons onset-fix addendum: unlike a genuine effectiveFractions
        // lookup (which would read 0 for a non-demanding system), this always answers "what if" -
        // here, Engines alone forced into an otherwise-empty demand set reaches the same capped
        // ceiling as being the sole genuine demander would (see the sole-demander test above).
        float multiplier = PowerDistribution.even()
            .effectiveMultiplierIfDemanding(PowerSystem.ENGINES, EnumSet.noneOf(PowerSystem.class));

        assertEquals(PowerDistribution.REDISTRIBUTION_CAP_FRACTION / PowerDistribution.BASELINE_FRACTION, multiplier, TOLERANCE);
    }

    @Test
    void everyDemandingSystemsEffectiveFractionIsAtLeastItsPriorityFraction() {
        // Invariant PowerAllocationSystem's consumers rely on: redistribution can only ever help a
        // demanding system, never hurt it - checked across a skewed distribution (Weapons
        // maximized) with two different demand sets, both a two- and a three-way demand.
        PowerDistribution distribution = PowerDistribution.even().maximize(PowerSystem.WEAPONS);

        for (EnumSet<PowerSystem> demanding : java.util.List.of(
            EnumSet.of(PowerSystem.ENGINES, PowerSystem.WEAPONS), EnumSet.allOf(PowerSystem.class))) {
            Map<PowerSystem, Float> effective = distribution.effectiveFractions(demanding);
            for (PowerSystem system : demanding) {
                assertTrue(effective.get(system) >= distribution.getFraction(system) - TOLERANCE,
                    system + " with demand " + demanding + " should be >= its priority fraction");
            }
        }
    }

    @Test
    void effectiveFractionsNeverSumToMoreThanTheWholeOutput() {
        PowerDistribution distribution = PowerDistribution.even().maximize(PowerSystem.WEAPONS);

        for (EnumSet<PowerSystem> demanding : java.util.List.of(
            EnumSet.of(PowerSystem.WEAPONS), EnumSet.of(PowerSystem.ENGINES, PowerSystem.WEAPONS),
            EnumSet.allOf(PowerSystem.class))) {
            float sum = 0f;
            for (float fraction : distribution.effectiveFractions(demanding).values()) {
                sum += fraction;
            }
            assertTrue(sum <= 1f + TOLERANCE, "demand " + demanding + " summed to " + sum);
        }
    }
}
