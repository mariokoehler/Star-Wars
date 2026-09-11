package de.mkoehler.starwars.net.messages;

/**
 * Broadcast by the server whenever a ship picks up a power-up (design.md
 * — power-ups' audio addendum), carrying the power-up's position at the
 * moment of pickup so every client can play the shared pickup sound
 * there. Same "just an SFX trigger" shape as {@link ProjectileHitMessage}/
 * {@link MineDetonatedMessage} — a client doesn't need to know which
 * power-up or which ship picked it up, only where to play the sound; it
 * already infers the power-up is gone the normal way (its id no longer
 * appearing in the next {@link WorldSnapshotMessage}).
 * <p>
 * Sent to every connected client unconditionally, over the unreliable UDP
 * channel — same reasoning as every other one-shot sound trigger here:
 * dropping an occasional one is harmless for a purely cosmetic effect.
 */
public class PowerUpPickedUpMessage {

    private float x;
    private float y;

    /**
     * No-arg constructor required by Kryo for deserialization.
     */
    public PowerUpPickedUpMessage() {
    }

    /**
     * Creates a power-up-picked-up message.
     *
     * @param x the power-up's position at the moment of pickup, in meters
     * @param y the power-up's position at the moment of pickup, in meters
     */
    public PowerUpPickedUpMessage(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the power-up's position at the moment of pickup.
     *
     * @return the position, in meters
     */
    public float getX() {
        return x;
    }

    /**
     * Returns the power-up's position at the moment of pickup.
     *
     * @return the position, in meters
     */
    public float getY() {
        return y;
    }
}
