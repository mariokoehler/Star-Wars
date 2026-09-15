package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link WeaponStats#forShip} overrides exactly the three per-ship
 * fields (cooldown, shot energy cost, damage) while leaving every other
 * field at {@link WeaponStats#BLASTER}'s shared baseline.
 */
class WeaponStatsTest {

    @Test
    void forShipOverridesOnlyCooldownShotEnergyCostAndDamage() {
        WeaponStats stats = WeaponStats.forShip(0.5f, 35f, 15f);

        assertEquals(0.5f, stats.getCooldownSeconds());
        assertEquals(35f, stats.getShotEnergyCost());
        assertEquals(15f, stats.getDamage());
        assertEquals(WeaponStats.BLASTER.getProjectileSpeed(), stats.getProjectileSpeed());
        assertEquals(WeaponStats.BLASTER.getProjectileRadiusMeters(), stats.getProjectileRadiusMeters());
        assertEquals(WeaponStats.BLASTER.getProjectileLifetimeSeconds(), stats.getProjectileLifetimeSeconds());
        assertEquals(WeaponStats.BLASTER.getCapacitorMaxCharge(), stats.getCapacitorMaxCharge());
        assertEquals(WeaponStats.BLASTER.getBaseRechargePerSecond(), stats.getBaseRechargePerSecond());
    }

    @Test
    void forShipWithBlastersOwnValuesEqualsBlasterFieldByField() {
        WeaponStats stats = WeaponStats.forShip(WeaponStats.BLASTER.getCooldownSeconds(),
            WeaponStats.BLASTER.getShotEnergyCost(), WeaponStats.BLASTER.getDamage());

        assertEquals(WeaponStats.BLASTER.getCooldownSeconds(), stats.getCooldownSeconds());
        assertEquals(WeaponStats.BLASTER.getShotEnergyCost(), stats.getShotEnergyCost());
        assertEquals(WeaponStats.BLASTER.getDamage(), stats.getDamage());
    }

    @Test
    void forTurretOverridesOnlyCooldownShotEnergyCostAndDamage() {
        WeaponStats stats = WeaponStats.forTurret(0.6f, 5f, 5f);

        assertEquals(0.6f, stats.getCooldownSeconds());
        assertEquals(5f, stats.getShotEnergyCost());
        assertEquals(5f, stats.getDamage());
        assertEquals(WeaponStats.BLASTER.getProjectileSpeed(), stats.getProjectileSpeed());
        assertEquals(WeaponStats.BLASTER.getProjectileRadiusMeters(), stats.getProjectileRadiusMeters());
        assertEquals(WeaponStats.BLASTER.getProjectileLifetimeSeconds(), stats.getProjectileLifetimeSeconds());
        assertEquals(WeaponStats.BLASTER.getCapacitorMaxCharge(), stats.getCapacitorMaxCharge());
        assertEquals(WeaponStats.BLASTER.getBaseRechargePerSecond(), stats.getBaseRechargePerSecond());
    }
}
