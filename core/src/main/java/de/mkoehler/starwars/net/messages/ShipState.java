package de.mkoehler.starwars.net.messages;

/**
 * One ship's position, orientation and velocity at the moment a
 * {@link WorldSnapshotMessage} was built. Not sent on its own, only as an
 * element of that message's ship list.
 * <p>
 * Velocity is included (not just position/angle) so a client reconciling its
 * own predicted ship against this authoritative state can correct both —
 * correcting position alone while leaving a mismatched velocity in place
 * would just cause the ship to immediately drift out of sync again.
 */
public class ShipState {

    private int playerId;
    private float x;
    private float y;
    private float angle;
    private float velocityX;
    private float velocityY;
    private float angularVelocity;

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
     */
    public ShipState(int playerId, float x, float y, float angle,
                      float velocityX, float velocityY, float angularVelocity) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.angularVelocity = angularVelocity;
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
}
