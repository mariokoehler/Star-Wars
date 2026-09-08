package de.mkoehler.starwars.sim;

/**
 * The missile's balance/tuning numbers, loaded from
 * {@code projectiles/missile.stats.json} (see {@link MissileStats}). Plain
 * Jackson bean, same convention as {@link ShipTypeConfig} — but unlike that
 * class, there's only one missile type, so this isn't type-keyed.
 */
public class MissileConfig {

    private float pixelsPerMeter;
    private float thrustForce;
    private float turnTorque;
    private float damage;
    private float flightSeconds;

    /**
     * Returns the conversion factor between the missile's authored
     * sprite-space pixel coordinates ({@code missile.meta.json}'s hitbox
     * polygon and attachment points) and Box2D meters — same role as
     * {@link ShipTypeConfig#getPixelsPerMeter()}, just for the missile's own
     * sprite instead of a ship's.
     *
     * @return the missile's pixels-per-meter
     */
    public float getPixelsPerMeter() {
        return pixelsPerMeter;
    }

    public void setPixelsPerMeter(float pixelsPerMeter) {
        this.pixelsPerMeter = pixelsPerMeter;
    }

    /**
     * Returns the constant forward thrust force applied to a missile in
     * flight for as long as its fuel lasts.
     *
     * @return the thrust force, in newtons
     */
    public float getThrustForce() {
        return thrustForce;
    }

    public void setThrustForce(float thrustForce) {
        this.thrustForce = thrustForce;
    }

    /**
     * Returns the maximum steering torque a missile can apply while tracking
     * its target — deliberately limited (design.md — missiles) so a target
     * can potentially outmaneuver a lock-acquired missile.
     *
     * @return the turn torque, in newton-meters
     */
    public float getTurnTorque() {
        return turnTorque;
    }

    public void setTurnTorque(float turnTorque) {
        this.turnTorque = turnTorque;
    }

    /**
     * Returns the damage a missile deals on a direct hit.
     *
     * @return the damage
     */
    public float getDamage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /**
     * Returns how long a fired missile's fuel lasts before it self-destructs
     * if it hasn't struck a target yet. Reused directly as the missile's
     * {@link de.mkoehler.starwars.sim.components.ProjectileComponent}
     * lifetime, so {@link de.mkoehler.starwars.sim.systems.ProjectileLifetimeSystem}
     * expires it for free with no missile-specific expiry code needed.
     *
     * @return the flight duration, in seconds
     */
    public float getFlightSeconds() {
        return flightSeconds;
    }

    public void setFlightSeconds(float flightSeconds) {
        this.flightSeconds = flightSeconds;
    }
}
