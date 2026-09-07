package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link KillXp}'s tier-weighted formula, including the exact
 * underdog/lopsided examples from design.md's kill XP writeup.
 */
class KillXpTest {

    @Test
    void sameTierKillEarnsTheBaseXpTimesItsOwnTier() {
        // Both tier 2: 10 * 2 * (2/2) = 20.
        assertEquals(20, KillXp.calculate(ShipType.AWING, ShipType.TIEFIGHTER));
    }

    @Test
    void underdogKillIsRewardedWithTheFullRankDisparityMultiplier() {
        // Tier-1 Snowspeeder downs a tier-4 Star Destroyer: 10 * 4 * (4/1) = 160.
        assertEquals(160, KillXp.calculate(ShipType.STARDESTROYER, ShipType.SNOWSPEEDER));
    }

    @Test
    void lopsidedKillIsPenalizedByTheRankDisparityMultiplier() {
        // Tier-4 Star Destroyer downs a tier-1 Snowspeeder: 10 * 1 * (1/4) = 2.5, rounds to 3.
        assertEquals(3, KillXp.calculate(ShipType.SNOWSPEEDER, ShipType.STARDESTROYER));
    }

    @Test
    void killingTheBiggestShipAsTheBiggestShipIsWorthOnlyItsBaseTierValue() {
        // Both tier 4: 10 * 4 * (4/4) = 40.
        assertEquals(40, KillXp.calculate(ShipType.FALCON, ShipType.STARDESTROYER));
    }

    @Test
    void tierOneVersusTierOneYieldsExactlyTheBaseXp() {
        // 10 * 1 * (1/1) = 10 - the formula's floor, since tiers only go down to 1.
        assertEquals(10, KillXp.calculate(ShipType.SNOWSPEEDER, ShipType.SNOWSPEEDER));
    }
}
