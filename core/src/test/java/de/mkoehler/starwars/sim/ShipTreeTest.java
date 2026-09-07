package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipTreeTest {

    @Test
    void tier2ShipsAndSnowspeederHaveNoPrerequisite() {
        assertTrue(ShipTree.prerequisiteOf(ShipType.SNOWSPEEDER).isEmpty());
        assertTrue(ShipTree.prerequisiteOf(ShipType.TIEFIGHTER).isEmpty());
        assertTrue(ShipTree.prerequisiteOf(ShipType.AWING).isEmpty());
    }

    @Test
    void imperialBranchClimbsTieFighterThenTieInterceptorThenStarDestroyer() {
        assertEquals(ShipType.TIEFIGHTER, ShipTree.prerequisiteOf(ShipType.TIEINTERCEPTOR).orElseThrow());
        assertEquals(ShipType.TIEINTERCEPTOR, ShipTree.prerequisiteOf(ShipType.STARDESTROYER).orElseThrow());
    }

    @Test
    void rebelBranchClimbsAWingThenXWingThenFalcon() {
        assertEquals(ShipType.AWING, ShipTree.prerequisiteOf(ShipType.XWING).orElseThrow());
        assertEquals(ShipType.XWING, ShipTree.prerequisiteOf(ShipType.FALCON).orElseThrow());
    }

    @Test
    void prerequisiteMetIsTrueWithNoPrerequisiteAtAll() {
        assertTrue(ShipTree.prerequisiteMet(ShipType.TIEFIGHTER, Set.of()));
    }

    @Test
    void prerequisiteMetIsFalseWhenThePreviousBranchShipIsNotUnlocked() {
        assertFalse(ShipTree.prerequisiteMet(ShipType.TIEINTERCEPTOR, Set.of()));
        assertFalse(ShipTree.prerequisiteMet(ShipType.STARDESTROYER, EnumSet.of(ShipType.TIEFIGHTER)));
    }

    @Test
    void prerequisiteMetIsTrueOnceThePreviousBranchShipIsUnlocked() {
        assertTrue(ShipTree.prerequisiteMet(ShipType.TIEINTERCEPTOR, EnumSet.of(ShipType.TIEFIGHTER)));
        assertTrue(ShipTree.prerequisiteMet(ShipType.STARDESTROYER, EnumSet.of(ShipType.TIEFIGHTER, ShipType.TIEINTERCEPTOR)));
    }

    @Test
    void branchesDoNotCrossUnlockRebelDoesNotSatisfyImperialPrerequisite() {
        assertFalse(ShipTree.prerequisiteMet(ShipType.TIEINTERCEPTOR, EnumSet.of(ShipType.AWING, ShipType.XWING)));
    }
}
