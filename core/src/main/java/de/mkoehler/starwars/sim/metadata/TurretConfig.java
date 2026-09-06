package de.mkoehler.starwars.sim.metadata;

/**
 * Tuning values for a ship type's turret(s) (design.md — turret weapons),
 * shared by every {@code TURRET} attachment point on that ship (a Star
 * Destroyer's four turrets all scan/turn/fire identically; they act
 * independently only in that each tracks its own target). Present only on
 * ship types that actually have turrets — absent (null) otherwise, same
 * optionality convention as {@link ShipSpriteMetadata} itself being absent
 * for a ship with no authored metadata at all.
 * <p>
 * Plain mutable bean (public no-arg constructor, getters and setters) so
 * Jackson can (de)serialize it with no extra configuration.
 */
public class TurretConfig {

    /**
     * The {@link ShipSpriteMetadata#getAttachmentPoints()} name a turret
     * mount is authored under — shared by every consumer that needs to look
     * up a ship type's turret positions (currently {@code ShipFactory} and
     * the client's turret rendering).
     */
    public static final String ATTACHMENT_NAME = "TURRET";

    private float scanRangeMeters;
    private float cooldownSeconds;
    private float turnRateDegreesPerSecond;

    /**
     * Returns how far a turret can detect and engage targets.
     *
     * @return the scan range, in meters
     */
    public float getScanRangeMeters() {
        return scanRangeMeters;
    }

    public void setScanRangeMeters(float scanRangeMeters) {
        this.scanRangeMeters = scanRangeMeters;
    }

    /**
     * Returns the minimum time between shots — the turret's own rate of
     * fire, independent of the ship's main weapon cooldown (though the two
     * draw from the same shared capacitor, see {@code TurretSystem}).
     *
     * @return the cooldown, in seconds
     */
    public float getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(float cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    /**
     * Returns how fast the turret can rotate to track a target.
     *
     * @return the turn rate, in degrees/second
     */
    public float getTurnRateDegreesPerSecond() {
        return turnRateDegreesPerSecond;
    }

    public void setTurnRateDegreesPerSecond(float turnRateDegreesPerSecond) {
        this.turnRateDegreesPerSecond = turnRateDegreesPerSecond;
    }
}
