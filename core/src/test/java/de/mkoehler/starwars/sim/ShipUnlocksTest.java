package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ShipUnlocks}: the always-unlocked Snowspeeder special
 * case, and the available-XP subtraction, against the real per-ship
 * {@code unlockCostXp} values loaded from {@code shipdata/*.stats.json}
 * (design.md — ship unlocks: TIE Fighter 75 / A-wing 100 at tier 2, TIE
 * Interceptor 100 / X-wing 125 at tier 3, Star Destroyer 200 / Falcon 150
 * at tier 4).
 */
class ShipUnlocksTest {

    @Test
    void snowspeederIsAlwaysUnlockedEvenWithAnEmptySet() {
        assertTrue(ShipUnlocks.isUnlocked(ShipType.SNOWSPEEDER, Set.of()));
    }

    @Test
    void anotherShipIsLockedUntilItsInTheSet() {
        assertFalse(ShipUnlocks.isUnlocked(ShipType.XWING, Set.of()));
        assertTrue(ShipUnlocks.isUnlocked(ShipType.XWING, Set.of(ShipType.XWING)));
    }

    @Test
    void availableXpWithNothingUnlockedIsTheFullTotal() {
        assertEquals(500, ShipUnlocks.availableXp(500, Set.of()));
    }

    @Test
    void availableXpSubtractsTheCostOfEveryUnlockedShip() {
        // TIE Fighter (tier 2) costs 75.
        assertEquals(500, ShipUnlocks.availableXp(575, Set.of(ShipType.TIEFIGHTER)));
    }

    @Test
    void availableXpSumsMultipleUnlockedShips() {
        // TIE Fighter (75) + X-wing (125) = 200 spent.
        Set<ShipType> unlocked = EnumSet.of(ShipType.TIEFIGHTER, ShipType.XWING);
        assertEquals(500, ShipUnlocks.availableXp(700, unlocked));
    }

    @Test
    void availableXpCanGoNegativeIfSomehowOverspent() {
        // Not expected in practice (the server only ever unlocks when affordable), but the
        // subtraction itself shouldn't clamp or throw - Falcon (tier 4) costs 150.
        assertEquals(-50, ShipUnlocks.availableXp(100, Set.of(ShipType.FALCON)));
    }
}
