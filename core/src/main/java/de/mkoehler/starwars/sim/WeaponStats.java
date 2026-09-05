package de.mkoehler.starwars.sim;

/**
 * Per-weapon-type tuning values, shared between the server (which needs them
 * to spawn/simulate projectiles) and the client (which needs the projectile
 * radius to size the drawn sprite consistently with the server's collision
 * shape).
 * <p>
 * A single hardcoded constant for now, standing in for the not-yet-built
 * weapon capacitor mechanic (design.md 2.2) — this is a plain fixed cooldown
 * between shots, not the eventual buffered-energy system tied to the power
 * distribution mechanic, which doesn't exist yet either. Revisit once power
 * distribution is implemented.
 */
public final class WeaponStats {

    /**
     * The (currently only) weapon: a simple blaster cannon.
     */
    public static final WeaponStats BLASTER = new WeaponStats(0.25f, 50f, 10f, 0.15f, 3f);

    private final float cooldownSeconds;
    private final float projectileSpeed;
    private final float damage;
    private final float projectileRadiusMeters;
    private final float projectileLifetimeSeconds;

    private WeaponStats(float cooldownSeconds, float projectileSpeed, float damage,
                         float projectileRadiusMeters, float projectileLifetimeSeconds) {
        this.cooldownSeconds = cooldownSeconds;
        this.projectileSpeed = projectileSpeed;
        this.damage = damage;
        this.projectileRadiusMeters = projectileRadiusMeters;
        this.projectileLifetimeSeconds = projectileLifetimeSeconds;
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
}
