package de.mkoehler.starwars.net.messages;

/**
 * One ship's position and orientation at the moment a
 * {@link WorldSnapshotMessage} was built. Not sent on its own, only as an
 * element of that message's ship list.
 */
public class ShipState {

    private int playerId;
    private float x;
    private float y;
    private float angle;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ShipState() {
    }

    /**
     * Creates a ship state entry.
     *
     * @param playerId the id of the player this ship belongs to
     * @param x        the ship's position, in meters
     * @param y        the ship's position, in meters
     * @param angle    the ship's facing angle, in radians
     */
    public ShipState(int playerId, float x, float y, float angle) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
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
}
