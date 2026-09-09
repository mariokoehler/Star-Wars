package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server whenever a projectile is destroyed by actually
 * hitting a ship (design.md — explosions), carrying the position of the
 * hit so every client can play a small impact explosion there — a real
 * contact event, unlike a projectile simply disappearing after its
 * lifetime naturally expires (which broadcasts nothing; a client only
 * infers that case by the projectile's id no longer appearing in the next
 * {@link WorldSnapshotMessage}).
 * <p>
 * Sent to every connected client unconditionally, over the unreliable UDP
 * channel — same reasoning as {@code ProjectileState} itself (design.md
 * 2.14's scope boundary: projectiles are never radar-filtered), and
 * dropping an occasional one is harmless for a purely cosmetic effect.
 */
public class ProjectileHitMessage {

    private float x;
    private float y;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public ProjectileHitMessage() {
    }

    /**
     * Creates a projectile-hit message.
     *
     * @param x the hit position, in meters
     * @param y the hit position, in meters
     */
    public ProjectileHitMessage(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the hit position.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the hit position.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }
}
