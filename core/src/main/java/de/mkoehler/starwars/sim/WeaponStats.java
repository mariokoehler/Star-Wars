package de.mkoehler.starwars.sim;

/**
 * Per-weapon-type tuning values, shared between the server (which needs them
 * to spawn/simulate projectiles, and to run the weapon capacitor) and the
 * client (which needs the projectile radius to size the drawn sprite
 * consistently with the server's collision shape).
 * <p>
 * {@link #getCooldownSeconds()} is a hard mechanical fire-rate cap (the gun
 * itself can't cycle faster than this); on top of that,
 * {@link de.mkoehler.starwars.sim.components.WeaponComponent} gates firing on
 * a real weapon capacitor (design.md 2.2) — a small energy
 * buffer ({@link #getCapacitorMaxCharge()}, sized for a handful of shots)
 * that trickle-recharges at {@link #getBaseRechargePerSecond()}, scaled by
 * the ship's current {@link de.mkoehler.starwars.sim.PowerSystem#WEAPONS}
 * power allocation — draining it shot by shot
 * ({@link #getShotEnergyCost()}) lets a player burst-fire above their
 * sustained rate for a few shots before the capacitor's recharge becomes
 * the binding constraint instead of the cooldown.
 */
public final class WeaponStats {

    /**
     * Attachment point name convention (design.md 2.4) for where projectiles
     * spawn — a ship can define more than one, e.g. an X-wing's four
     * cannons, and one projectile is fired per point each time the weapon is
     * off cooldown. Shared between {@link de.mkoehler.starwars.sim.systems.WeaponSystem}
     * (server, authoritative firing) and {@code Client} (client-side shot
     * prediction, design.md 2.4's addendum) so the two can't quietly drift
     * apart the way two independently-typed copies of this string could —
     * same reasoning as {@link de.mkoehler.starwars.sim.metadata.TurretConfig#ATTACHMENT_NAME}.
     */
    public static final String PROJECTILE_ATTACHMENT_NAME = "PROJECTILE";

    /**
     * The (currently only) weapon: a simple blaster cannon. Capacitor sized
     * for 5.5 shots at full charge; base recharge (at the even power
     * baseline) sustains 2 shots/sec indefinitely, well under the 4 shots/sec
     * cooldown cap, so investing power in Weapons has real room to matter.
     * All untuned placeholders pending a real balancing pass, same as the
     * other weapon/ship numbers.
     */
    public static final WeaponStats BLASTER = new WeaponStats(0.25f, 50f, 10f, 0.15f, 3f, 20f, 110f, 40f);

    private final float cooldownSeconds;
    private final float projectileSpeed;
    private final float damage;
    private final float projectileRadiusMeters;
    private final float projectileLifetimeSeconds;
    private final float shotEnergyCost;
    private final float capacitorMaxCharge;
    private final float baseRechargePerSecond;

    private WeaponStats(float cooldownSeconds, float projectileSpeed, float damage,
                         float projectileRadiusMeters, float projectileLifetimeSeconds,
                         float shotEnergyCost, float capacitorMaxCharge, float baseRechargePerSecond) {
        this.cooldownSeconds = cooldownSeconds;
        this.projectileSpeed = projectileSpeed;
        this.damage = damage;
        this.projectileRadiusMeters = projectileRadiusMeters;
        this.projectileLifetimeSeconds = projectileLifetimeSeconds;
        this.shotEnergyCost = shotEnergyCost;
        this.capacitorMaxCharge = capacitorMaxCharge;
        this.baseRechargePerSecond = baseRechargePerSecond;
    }

    /**
     * Returns the minimum time between shots.
     *
     * @return the cooldown, in seconds
     */
    public float getCooldownSeconds() {
        return cooldownSeconds;
    }

    /**
     * Returns the speed a fired projectile travels at.
     *
     * @return the speed, in meters/second
     */
    public float getProjectileSpeed() {
        return projectileSpeed;
    }

    /**
     * Returns the damage a single hit deals.
     *
     * @return the damage
     */
    public float getDamage() {
        return damage;
    }

    /**
     * Returns the collision/draw radius of a fired projectile.
     *
     * @return the radius, in meters
     */
    public float getProjectileRadiusMeters() {
        return projectileRadiusMeters;
    }

    /**
     * Returns how long a projectile survives before expiring, if it hits
     * nothing first.
     *
     * @return the lifetime, in seconds
     */
    public float getProjectileLifetimeSeconds() {
        return projectileLifetimeSeconds;
    }

    /**
     * Returns the energy drawn from the capacitor by a single shot (one
     * volley, even if it fires from multiple attachment points at once).
     *
     * @return the energy cost
     */
    public float getShotEnergyCost() {
        return shotEnergyCost;
    }

    /**
     * Returns the capacitor's maximum charge.
     *
     * @return the maximum charge, in the same units as {@link #getShotEnergyCost()}
     */
    public float getCapacitorMaxCharge() {
        return capacitorMaxCharge;
    }

    /**
     * Returns how fast the capacitor recharges at the even power baseline
     * (i.e. before {@link de.mkoehler.starwars.sim.PowerSystem#WEAPONS}'s
     * multiplier is applied).
     *
     * @return the base recharge rate, in charge/second
     */
    public float getBaseRechargePerSecond() {
        return baseRechargePerSecond;
    }
}
