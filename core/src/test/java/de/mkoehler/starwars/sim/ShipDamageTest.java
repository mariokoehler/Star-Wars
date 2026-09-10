package de.mkoehler.starwars.sim;

import de.mkoehler.starwars.sim.components.HullComponent;
import de.mkoehler.starwars.sim.components.ShieldComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void chunkedApplicationLetsFarMoreDamageThroughToTheHullThanOneLumpHit() {
        // The exact worked example from a missile's own numbers (design.md - missiles' damage-
        // application addendum): a full-shield ship takes a 100-damage hit as one lump sum vs. as
        // ten 10-damage sub-hits.
        ShieldComponent lumpShield = new ShieldComponent(100f, 0f);
        HullComponent lumpHull = new HullComponent(100f);
        ShipDamage.apply(lumpShield, lumpHull, 100f);
        assertEquals(0f, lumpShield.getCurrent());
        assertEquals(100f, lumpHull.getCurrent()); // shield's 100% fraction absorbed the whole hit

        ShieldComponent chunkedShield = new ShieldComponent(100f, 0f);
        HullComponent chunkedHull = new HullComponent(100f);
        ShipDamage.applyChunked(chunkedShield, chunkedHull, 100f, 10);
        assertEquals(34.87f, chunkedShield.getCurrent(), 0.01f);
        assertEquals(65.13f, chunkedHull.getCurrent(), 0.01f); // real hull damage the lump hit never dealt at all

        assertTrue(chunkedHull.getCurrent() < lumpHull.getCurrent(),
            "chunked application should let strictly more damage through to the hull");
    }

    @Test
    void chunkedApplicationConservesTheTotalDamageDealt() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        HullComponent hull = new HullComponent(100f);

        ShipDamage.applyChunked(shield, hull, 100f, 10);

        float shieldDamageDealt = 100f - shield.getCurrent();
        float hullDamageDealt = 100f - hull.getCurrent();
        assertEquals(100f, shieldDamageDealt + hullDamageDealt, 0.05f);
    }

    @Test
    void chunkCountOfOneBehavesIdenticallyToAPlainApply() {
        ShieldComponent chunkedShield = new ShieldComponent(100f, 0f);
        HullComponent chunkedHull = new HullComponent(100f);
        ShipDamage.applyChunked(chunkedShield, chunkedHull, 42f, 1);

        ShieldComponent plainShield = new ShieldComponent(100f, 0f);
        HullComponent plainHull = new HullComponent(100f);
        ShipDamage.apply(plainShield, plainHull, 42f);

        assertEquals(plainShield.getCurrent(), chunkedShield.getCurrent());
        assertEquals(plainHull.getCurrent(), chunkedHull.getCurrent());
    }

    @Test
    void aMisconfiguredZeroChunkCountFallsBackToASingleApplyRatherThanDividingByZero() {
        ShieldComponent shield = new ShieldComponent(100f, 0f);
        HullComponent hull = new HullComponent(100f);

        ShipDamage.applyChunked(shield, hull, 10f, 0);

        assertEquals(90f, shield.getCurrent());
        assertEquals(100f, hull.getCurrent());
    }

    @Test
    void chunkedApplicationOnAnAlreadyDepletedShieldMatchesALumpHit() {
        // With the shield already at 0, every sub-hit's fraction is 0 too, so chunking changes
        // nothing - both paths should send the full amount straight to the hull either way.
        ShieldComponent chunkedShield = new ShieldComponent(100f, 0f);
        chunkedShield.damage(100f);
        HullComponent chunkedHull = new HullComponent(100f);
        ShipDamage.applyChunked(chunkedShield, chunkedHull, 30f, 10);

        assertEquals(0f, chunkedShield.getCurrent());
        assertEquals(70f, chunkedHull.getCurrent());
    }
}
