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
 * (1000/1500/2000 for tiers 2/3/4, design.md).
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
        // TIE Fighter (tier 2) costs 1000.
        assertEquals(500, ShipUnlocks.availableXp(1500, Set.of(ShipType.TIEFIGHTER)));
    }

    @Test
    void availableXpSumsMultipleUnlockedShips() {
        // TIE Fighter (1000) + X-wing (1500) = 2500 spent.
        Set<ShipType> unlocked = EnumSet.of(ShipType.TIEFIGHTER, ShipType.XWING);
        assertEquals(500, ShipUnlocks.availableXp(3000, unlocked));
    }

    @Test
    void availableXpCanGoNegativeIfSomehowOverspent() {
        // Not expected in practice (the server only ever unlocks when affordable), but the
        // subtraction itself shouldn't clamp or throw - Falcon (tier 4) costs 2000.
        assertEquals(-500, ShipUnlocks.availableXp(1500, Set.of(ShipType.FALCON)));
    }
}
