package de.mkoehler.starwars.sim.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link PowerBoostComponent}'s activate/tick/multiplier state
 * machine (design.md — power-ups' BOOST effect).
 */
class PowerBoostComponentTest {

    @Test
    void freshComponentHasNoMultiplierBonus() {
        PowerBoostComponent boost = new PowerBoostComponent();

        assertEquals(1f, boost.getMultiplier());
        assertEquals(0f, boost.getRemainingSeconds());
    }

    @Test
    void activatingGrantsTheMultiplierUntilTheDurationElapses() {
        PowerBoostComponent boost = new PowerBoostComponent();

        boost.activate(15f);
        assertEquals(PowerBoostComponent.BOOST_MULTIPLIER, boost.getMultiplier());

        boost.tick(14.9f);
        assertEquals(PowerBoostComponent.BOOST_MULTIPLIER, boost.getMultiplier());

        boost.tick(0.2f);
        assertEquals(1f, boost.getMultiplier());
        assertEquals(0f, boost.getRemainingSeconds());
    }

    @Test
    void activatingWhileAlreadyActiveRefreshesRatherThanStacks() {
        PowerBoostComponent boost = new PowerBoostComponent();

        boost.activate(15f);
        boost.tick(10f);
        assertEquals(5f, boost.getRemainingSeconds());

        boost.activate(15f); // a second pickup resets the timer, doesn't add to it
        assertEquals(15f, boost.getRemainingSeconds());
        assertEquals(PowerBoostComponent.BOOST_MULTIPLIER, boost.getMultiplier());
    }

    @Test
    void tickNeverDropsRemainingSecondsBelowZero() {
        PowerBoostComponent boost = new PowerBoostComponent();

        boost.activate(5f);
        boost.tick(100f);

        assertEquals(0f, boost.getRemainingSeconds());
        assertEquals(1f, boost.getMultiplier());
    }
}
