package de.mkoehler.starwars.sim.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link CombatTimerComponent}'s combat-lock rule (design.md 2.3):
 * a ship counts as "in combat" for a window after either firing or being
 * hit, whichever happened more recently.
 */
class CombatTimerComponentTest {

    private static final float THRESHOLD = 20f;

    @Test
    void freshShipIsNotInCombat() {
        CombatTimerComponent timer = new CombatTimerComponent();

        assertFalse(timer.isInCombat(THRESHOLD));
    }

    @Test
    void firingLocksCombatUntilTheThresholdElapses() {
        CombatTimerComponent timer = new CombatTimerComponent();

        timer.markFired();
        assertTrue(timer.isInCombat(THRESHOLD));

        timer.tick(19.9f);
        assertTrue(timer.isInCombat(THRESHOLD));

        timer.tick(0.2f);
        assertFalse(timer.isInCombat(THRESHOLD));
    }

    @Test
    void beingHitLocksCombatTheSameWayFiringDoes() {
        CombatTimerComponent timer = new CombatTimerComponent();

        timer.markHit();
        assertTrue(timer.isInCombat(THRESHOLD));

        timer.tick(20.1f);
        assertFalse(timer.isInCombat(THRESHOLD));
    }

    @Test
    void eitherConditionAloneIsEnoughToStayLocked() {
        CombatTimerComponent timer = new CombatTimerComponent();

        timer.markFired();
        timer.tick(25f); // firing lock expires
        assertFalse(timer.isInCombat(THRESHOLD));

        timer.markHit(); // being hit re-locks it independently
        assertTrue(timer.isInCombat(THRESHOLD));
    }
}
