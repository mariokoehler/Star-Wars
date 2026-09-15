package de.mkoehler.starwars.sim.components;

import de.mkoehler.starwars.sim.WeaponStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link WeaponComponent#hasCharge(float)}/{@link WeaponComponent#drainCharge(float)}
 * (design.md 2.9's addendum, the turret-firing path) act purely on the
 * capacitor's charge, independent of and never touching
 * {@link WeaponComponent#getCooldownRemaining()} — unlike
 * {@link WeaponComponent#canFire()}/{@link WeaponComponent#consumeShot()},
 * the main gun's own path, which is intentionally left untouched by this test.
 */
class WeaponComponentTest {

    @Test
    void hasChargeReflectsCapacitorRegardlessOfCooldownRemaining() {
        WeaponComponent weapon = new WeaponComponent(WeaponStats.forShip(1f, 20f, 10f));

        assertTrue(weapon.hasCharge(20f));
        assertFalse(weapon.hasCharge(200f));
    }

    @Test
    void drainChargeSubtractsGivenAmountWithoutTouchingCooldown() {
        WeaponComponent weapon = new WeaponComponent(WeaponStats.forShip(1f, 20f, 10f));
        float chargeBefore = weapon.getCurrentCharge();

        weapon.drainCharge(5f);

        assertEquals(chargeBefore - 5f, weapon.getCurrentCharge());
        assertEquals(0f, weapon.getCooldownRemaining());
    }

    @Test
    void drainChargeUsesGivenAmountNotTheComponentsOwnShotEnergyCost() {
        WeaponComponent weapon = new WeaponComponent(WeaponStats.forShip(1f, 95f, 100f));
        float chargeBefore = weapon.getCurrentCharge();

        weapon.drainCharge(5f);

        assertEquals(chargeBefore - 5f, weapon.getCurrentCharge());
    }
}
