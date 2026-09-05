package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.ShipType;

/**
 * One ship's position, orientation, velocity, hull/shield status, and ship
 * type at the moment a {@link WorldSnapshotMessage} was built. Not sent on
 * its own, only as an element of that message's ship list.
 * <p>
 * Velocity is included (not just position/angle) so a client reconciling its
 * own predicted ship against this authoritative state can correct both —
 * correcting position alone while leaving a mismatched velocity in place
 * would just cause the ship to immediately drift out of sync again.
 * <p>
 * Hull/shield current and max (design.md 2.5) are included for every ship,
 * not just the local player's own, even though only the local player's HUD
 * ({@link de.mkoehler.starwars.render.ShipStatusHud}) reads them today —
 * broadcasting them for everyone costs little and leaves the door open for
 * a future enemy health readout without a protocol change.
 * <p>
 * The ship type is included so a client can render *other* players' ships
 * with the correct sprite/size (design.md 5.1) — other clients only ever
 * learn a ship's type from here, since {@link ShipSpawnedMessage} (which
 * also carries it) is only ever sent to the owning player.
 */
public class ShipState {

    private int playerId;
    private float x;
    private float y;
    private float angle;
    private float velocityX;
    private float velocityY;
    private float angularVelocity;
    private float hullCurrent;
    private float hullMax;
    private float shieldCurrent;
    private float shieldMax;
    private ShipType shipType;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipState() {
    }

    /**
     * Creates a ship state entry.
     *
     * @param playerId        the id of the player this ship belongs to
     * @param x               the ship's position, in meters
     * @param y               the ship's position, in meters
     * @param angle           the ship's facing angle, in radians
     * @param velocityX       the ship's linear velocity, in meters/second
     * @param velocityY       the ship's linear velocity, in meters/second
     * @param angularVelocity the ship's angular velocity, in radians/second
     * @param hullCurrent     the ship's current hull health
     * @param hullMax         the ship's maximum hull health
     * @param shieldCurrent   the ship's current shield strength
     * @param shieldMax       the ship's maximum shield capacity
     * @param shipType        the ship's type
     */
    public ShipState(int playerId, float x, float y, float angle,
                      float velocityX, float velocityY, float angularVelocity,
                      float hullCurrent, float hullMax, float shieldCurrent, float shieldMax,
                      ShipType shipType) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.angularVelocity = angularVelocity;
        this.hullCurrent = hullCurrent;
        this.hullMax = hullMax;
        this.shieldCurrent = shieldCurrent;
        this.shieldMax = shieldMax;
        this.shipType = shipType;
    }

    /**
     * Returns the id of the player this ship belongs to.
     *
     * @return the owning player's id
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Returns the ship's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the ship's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the ship's facing angle.
     *
     * @return the angle, in radians
     */
    public float getAngle() {
        return angle;
    }

    /**
     * Returns the ship's linear velocity on the X axis.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the ship's linear velocity on the Y axis.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Returns the ship's angular velocity.
     *
     * @return the angular velocity, in radians/second
     */
    public float getAngularVelocity() {
        return angularVelocity;
    }

    /**
     * Returns the ship's current hull health.
     *
     * @return the current hull health
     */
    public float getHullCurrent() {
        return hullCurrent;
    }

    /**
     * Returns the ship's maximum hull health.
     *
     * @return the maximum hull health
     */
    public float getHullMax() {
        return hullMax;
    }

    /**
     * Returns the ship's current shield strength.
     *
     * @return the current shield strength
     */
    public float getShieldCurrent() {
        return shieldCurrent;
    }

    /**
     * Returns the ship's maximum shield capacity.
     *
     * @return the maximum shield capacity
     */
    public float getShieldMax() {
        return shieldMax;
    }

    /**
     * Returns the ship's type.
     *
     * @return the ship type
     */
    public ShipType getShipType() {
        return shipType;
    }
}
