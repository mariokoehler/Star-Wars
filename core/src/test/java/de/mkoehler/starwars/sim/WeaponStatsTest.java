package de.mkoehler.starwars.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies {@link WeaponStats#forShip} overrides exactly the two per-ship
 * fields (cooldown, shot energy cost) while leaving every other field at
 * {@link WeaponStats#BLASTER}'s shared baseline.
 */
class WeaponStatsTest {

    @Test
    void forShipOverridesOnlyCooldownAndShotEnergyCost() {
        WeaponStats stats = WeaponStats.forShip(0.5f, 35f);

        assertEquals(0.5f, stats.getCooldownSeconds());
        assertEquals(35f, stats.getShotEnergyCost());
        assertEquals(WeaponStats.BLASTER.getProjectileSpeed(), stats.getProjectileSpeed());
        assertEquals(WeaponStats.BLASTER.getDamage(), stats.getDamage());
        assertEquals(WeaponStats.BLASTER.getProjectileRadiusMeters(), stats.getProjectileRadiusMeters());
        assertEquals(WeaponStats.BLASTER.getProjectileLifetimeSeconds(), stats.getProjectileLifetimeSeconds());
        assertEquals(WeaponStats.BLASTER.getCapacitorMaxCharge(), stats.getCapacitorMaxCharge());
        assertEquals(WeaponStats.BLASTER.getBaseRechargePerSecond(), stats.getBaseRechargePerSecond());
    }

    @Test
    void forShipWithBlastersOwnValuesEqualsBlasterFieldByField() {
        WeaponStats stats = WeaponStats.forShip(WeaponStats.BLASTER.getCooldownSeconds(),
            WeaponStats.BLASTER.getShotEnergyCost());

        assertEquals(WeaponStats.BLASTER.getCooldownSeconds(), stats.getCooldownSeconds());
        assertEquals(WeaponStats.BLASTER.getShotEnergyCost(), stats.getShotEnergyCost());
    }
}
