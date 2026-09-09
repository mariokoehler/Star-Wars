package de.mkoehler.starwars.net.messages;

import de.mkoehler.starwars.sim.AsteroidType;

/**
 * One asteroid's position/orientation and velocity at the moment a
 * {@link WorldSnapshotMessage} was built (design.md — asteroids). Not sent
 * on its own, only as an element of that message's asteroid list.
 * Broadcast unfiltered to every connected player, same scope-boundary
 * reasoning as {@link ProjectileState} — not gated by radar (design.md
 * 2.14's own scope boundary already excludes projectiles from that
 * filtering; an asteroid is the same kind of always-visible environmental
 * object).
 * <p>
 * An asteroid has no destroyed/despawned notification of its own, same as a
 * projectile — a client infers it's gone simply by its id no longer
 * appearing in a subsequent snapshot ({@code GameNetworkServer#tickAsteroids}
 * despawns one once it drifts outside the arena).
 */
public class AsteroidState {

    private int asteroidId;
    private AsteroidType type;
    private float x;
    private float y;
    private float angle;
    private float velocityX;
    private float velocityY;
    private float angularVelocity;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public AsteroidState() {
    }

    /**
     * Creates an asteroid state entry.
     *
     * @param asteroidId      this asteroid's id, unique among currently-alive asteroids
     * @param type            this asteroid's texture/hitbox type
     * @param x               the asteroid's position, in meters
     * @param y               the asteroid's position, in meters
     * @param angle            the asteroid's rotation, in radians
     * @param velocityX       the asteroid's velocity, in meters/second
     * @param velocityY       the asteroid's velocity, in meters/second
     * @param angularVelocity the asteroid's rotation speed, in radians/second
     */
    public AsteroidState(int asteroidId, AsteroidType type, float x, float y, float angle,
                          float velocityX, float velocityY, float angularVelocity) {
        this.asteroidId = asteroidId;
        this.type = type;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.angularVelocity = angularVelocity;
    }

    /**
     * Returns this asteroid's id.
     *
     * @return the asteroid id
     */
    public int getAsteroidId() {
        return asteroidId;
    }

    /**
     * Returns this asteroid's texture/hitbox type.
     *
     * @return the asteroid type
     */
    public AsteroidType getType() {
        return type;
    }

    /**
     * Returns the asteroid's X position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the asteroid's Y position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }

    /**
     * Returns the asteroid's rotation.
     *
     * @return the angle, in radians
     */
    public float getAngle() {
        return angle;
    }

    /**
     * Returns the asteroid's X velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityX() {
        return velocityX;
    }

    /**
     * Returns the asteroid's Y velocity.
     *
     * @return the velocity, in meters/second
     */
    public float getVelocityY() {
        return velocityY;
    }

    /**
     * Returns the asteroid's rotation speed.
     *
     * @return the angular velocity, in radians/second
     */
    public float getAngularVelocity() {
        return angularVelocity;
    }
}
