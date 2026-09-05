package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link ShipDamage}'s proportional shield/hull split, including
 * the exact 90/10 example from design.md 2.5 and the shield-depletion
 * overflow case that section doesn't explicitly spell out.
 */
class ShipDamageTest {

    @Test
    void fullShieldAbsorbsTheEntireHit() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        HullComponent hull = new HullComponent(100f);

        ShipDamage.apply(shield, hull, 10f);

        assertEquals(90f, shield.getCurrent());
        assertEquals(100f, hull.getCurrent());
    }

    @Test
    void ninetyPercentShieldSplitsNinetyTen() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        shield.damage(10f); // shield now at 90/100
        HullComponent hull = new HullComponent(100f);

        ShipDamage.apply(shield, hull, 10f);

        assertEquals(81f, shield.getCurrent()); // 90 - (10 * 0.9)
        assertEquals(99f, hull.getCurrent());   // 100 - (10 * 0.1)
    }

    @Test
    void depletedShieldSendsEverythingToTheHull() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        shield.damage(100f); // shield now at 0
        HullComponent hull = new HullComponent(100f);

        ShipDamage.apply(shield, hull, 10f);

        assertEquals(0f, shield.getCurrent());
        assertEquals(90f, hull.getCurrent());
    }

    @Test
    void shieldOverflowBleedsThroughToTheHullInsteadOfBeingLost() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        shield.damage(95f); // shield now at 5/100 (5% fraction)
        HullComponent hull = new HullComponent(1000f); // large so it doesn't clamp at zero below

        // Designated shield portion = 200 * (5/100) = 10, but only 5 shield remains - the 5
        // overflow must bleed through on top of the designated hull portion (200 * 0.95 = 190),
        // not vanish.
        ShipDamage.apply(shield, hull, 200f);

        assertEquals(0f, shield.getCurrent());
        assertEquals(805f, hull.getCurrent()); // 1000 - (190 + 5 overflow)
    }

    @Test
    void zeroCapacityShieldNeverDividesByZero() {
        ShieldComponent shield = new ShieldComponent(0f, 0f);
        HullComponent hull = new HullComponent(100f);

        ShipDamage.apply(shield, hull, 10f);

        assertEquals(0f, shield.getCurrent());
        assertEquals(90f, hull.getCurrent());
    }
}
