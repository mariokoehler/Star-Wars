package de.mkoehler.starwars.net.messages;

/**
 * One projectile's position and orientation at the moment a
 * {@link WorldSnapshotMessage} was built. Not sent on its own, only as an
 * element of that message's projectile list.
 * <p>
 * Projectiles have no destroyed/expired notification of their own — a
 * client infers a projectile is gone simply by its id no longer appearing
 * in a subsequent snapshot, the same way presence/absence already drives
 * everything else in this message.
 */
public class ProjectileState {

    private int projectileId;
    private int ownerPlayerId;
    private float x;
    private float y;
    private float angle;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ProjectileState() {
    }

    /**
     * Creates a projectile state entry.
     *
     * @param projectileId  this projectile's id, unique among currently-alive projectiles
     * @param ownerPlayerId the id of the player who fired it
     * @param x             the projectile's position, in meters
     * @param y             the projectile's position, in meters
     * @param angle         the projectile's facing/travel angle, in radians
     */
    public ProjectileState(int projectileId, int ownerPlayerId, float x, float y, float angle) {
        this.projectileId = projectileId;
        this.ownerPlayerId = ownerPlayerId;
        this.x = x;
        this.y = y;
        this.angle = angle;
    }

    /**
     * Returns this projectile's id.
     *
     * @return the projectile id
     */
    public int getProjectileId() {
        return projectileId;
    }

    /**
     * Returns the id of the player who fired this projectile.
     *
     * @return the owning player's id
     */
    public int getOwnerPlayerId() {
        return ownerPlayerId;
    }

    /**
     * Returns the projectile's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the projectile's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the projectile's facing/travel angle.
     *
     * @return the angle, in radians
     */
    public float getAngle() {
        return angle;
    }
}
