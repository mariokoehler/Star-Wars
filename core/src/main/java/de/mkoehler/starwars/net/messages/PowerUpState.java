package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.PowerUpType;

/**
 * One power-up's position/orientation and velocity at the moment a
 * {@link WorldSnapshotMessage} was built (design.md — power-ups). Not sent
 * on its own, only as an element of that message's power-up list. Broadcast
 * unfiltered to every connected player, same scope-boundary reasoning as
 * {@link AsteroidState}/{@link ProjectileState} — not gated by radar.
 * <p>
 * Velocity/angular velocity are included, same as {@link AsteroidState},
 * because a power-up isn't always stationary — a stray or deliberate shot
 * can set one drifting, and a client needs both to dead-reckon its render
 * position between snapshots.
 * <p>
 * A power-up has no destroyed/despawned notification of its own, same as an
 * asteroid — a client infers it's gone (picked up, or drifted out of the
 * arena and respawned elsewhere) simply by its id no longer appearing in a
 * subsequent snapshot.
 */
public class PowerUpState {

    private int powerUpId;
    private PowerUpType type;
    private float x;
    private float y;
    private float angle;
    private float velocityX;
    private float velocityY;
    private float angularVelocity;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PowerUpState() {
    }

    /**
     * Creates a power-up state entry.
     *
     * @param powerUpId       this power-up's id, unique among currently-active power-ups
     * @param type            this power-up's effect type
     * @param x               the power-up's position, in meters
     * @param y               the power-up's position, in meters
     * @param angle           the power-up's rotation, in radians
     * @param velocityX       the power-up's velocity, in meters/second
     * @param velocityY       the power-up's velocity, in meters/second
     * @param angularVelocity the power-up's rotation speed, in radians/second
     */
    public PowerUpState(int powerUpId, PowerUpType type, float x, float y, float angle,
                         float velocityX, float velocityY, float angularVelocity) {
        this.powerUpId = powerUpId;
        this.type = type;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.angularVelocity = angularVelocity;
    }

    /**
     * Returns this power-up's id.
     *
     * @return the power-up id
     */
    public int getPowerUpId() {
        return powerUpId;
    }

    /**
     * Returns this power-up's effect type.
     *
     * @return the power-up type
     */
    public PowerUpType getType() {
        return type;
    }

    /**
     * Returns the power-up's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the power-up's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the power-up's rotation.
     *
     * @return the angle, in radians
     */
    public float getAngle() {
        return angle;
    }

    /**
     * Returns the power-up's X velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the power-up's Y velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Returns the power-up's rotation speed.
     *
     * @return the angular velocity, in radians/second
     */
    public float getAngularVelocity() {
        return angularVelocity;
    }
}
